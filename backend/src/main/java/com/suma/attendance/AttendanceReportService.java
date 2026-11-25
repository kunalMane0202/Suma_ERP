package com.suma.attendance;

import com.suma.common.ApiException;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Builds daily and monthly attendance reports from actual attendance records, honouring data-access scope. */
@Service
public class AttendanceReportService {
    private final AttendanceSessionRepository sessions;
    private final BreakRecordRepository breaks;
    private final IdleEventRepository idles;
    private final AttendanceSettingsService settings;
    private final AccessScopeService scope;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AttendanceReportService(AttendanceSessionRepository sessions, BreakRecordRepository breaks, IdleEventRepository idles,
                                   AttendanceSettingsService settings, AccessScopeService scope, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.sessions = sessions;
        this.breaks = breaks;
        this.idles = idles;
        this.settings = settings;
        this.scope = scope;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Employee(UUID userId, String employeeCode, String fullName, String email, UUID projectId, String projectName,
                           UUID teamId, String teamName, Set<Short> weeklyOffs, Instant createdAt) {}

    public record DailyRow(Employee employee, AttendanceCalculator.DayResult day) {}

    public record DailyReport(LocalDate date, String timezone, Instant generatedAt, List<DailyRow> rows, Totals totals) {}

    public record MonthlyEmployee(Employee employee, List<AttendanceCalculator.DayResult> days, int daysPresent,
                                  long sessionSeconds, int breakCount, long breakSeconds, long averageBreakSeconds,
                                  int idleCount, long idleSeconds, int exceptionCount, int missingLogoutCount,
                                  int missingLoginDays, int exceededBreakCount) {}

    public record MonthlyReport(int year, int month, String timezone, Instant generatedAt, String scopeLabel,
                                List<MonthlyEmployee> employees, Totals totals) {}

    public record Totals(int employees, int present, long sessionSeconds, int breakCount, long breakSeconds,
                         long averageBreakSeconds, int idleCount, long idleSeconds, int exceptions, int missingLogouts) {}

    public record Filter(UUID projectId, UUID teamId, UUID userId) {}

    // ------------------------------------------------------------------ population

    /** Employees the caller may see, filtered by project / team / employee. A null caller means system (scheduler). */
    public List<Employee> population(AuthUser caller, Filter f) {
        Set<UUID> visible = caller == null ? null : scope.attendanceVisibleUserIds(caller);
        if (f.projectId() != null && caller != null) {
            Set<UUID> projects = scope.attendanceVisibleProjectIds(caller);
            boolean ownProject = visible != null && visible.contains(caller.id()) && isMember(caller.id(), f.projectId());
            if (projects != null && !projects.contains(f.projectId()) && !ownProject)
                throw ApiException.forbidden("You cannot view attendance for this project");
        }
        var p = new MapSqlParameterSource().addValue("project", f.projectId()).addValue("team", f.teamId()).addValue("user", f.userId());
        List<Employee> list = jdbc.query("""
                select u.id, u.employee_code, u.full_name, u.email,
                  pp.project_id, pr.name as project_name, tt.team_id, t.name as team_name,
                  (select string_agg(day_of_week::text, ',') from user_weekly_offs w where w.user_id = u.id) as offs, u.created_at
                from users u
                left join lateral (select pm.project_id from project_members pm join projects px on px.id = pm.project_id
                    where pm.user_id = u.id and (cast(:project as uuid) is null or pm.project_id = :project)
                    order by pm.is_primary desc, pm.assigned_at limit 1) pp on true
                left join projects pr on pr.id = pp.project_id
                left join lateral (select tm.team_id from team_members tm where tm.user_id = u.id
                    and (cast(:team as uuid) is null or tm.team_id = :team) order by tm.joined_at limit 1) tt on true
                left join teams t on t.id = tt.team_id
                where u.status <> 'INACTIVE'
                  and (cast(:project as uuid) is null or pp.project_id is not null)
                  and (cast(:team as uuid) is null or tt.team_id is not null)
                  and (cast(:user as uuid) is null or u.id = :user)
                order by u.full_name
                """, p, (rs, i) -> new Employee(rs.getObject("id", UUID.class), rs.getString("employee_code"), rs.getString("full_name"),
                rs.getString("email"), rs.getObject("project_id", UUID.class), rs.getString("project_name"), rs.getObject("team_id", UUID.class),
                rs.getString("team_name"), parseOffs(rs.getString("offs")), rs.getTimestamp("created_at").toInstant()));
        return list.stream().filter(e -> visible == null || visible.contains(e.userId())).toList();
    }

    private boolean isMember(UUID userId, UUID projectId) {
        Integer c = jdbc.queryForObject("select count(*) from project_members where user_id = :u and project_id = :p",
                new MapSqlParameterSource("u", userId).addValue("p", projectId), Integer.class);
        return c != null && c > 0;
    }

    private static Set<Short> parseOffs(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return Arrays.stream(s.split(",")).map(String::trim).map(Short::valueOf).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ daily

    @Transactional(readOnly = true)
    public DailyReport daily(AuthUser caller, LocalDate date, Filter f) {
        List<Employee> people = population(caller, f);
        AttendanceSettings cfg = settings.effective(f.projectId());
        ZoneId zone = ZoneId.of(cfg.getReportingTimezone());
        Instant now = Instant.now(clock);
        Data d = load(people, date, date);
        List<DailyRow> rows = new ArrayList<>();
        for (Employee e : people) {
            rows.add(new DailyRow(e, compute(e, date, d, zone, now)));
        }
        return new DailyReport(date, zone.getId(), now, rows, totals(rows.stream().map(DailyRow::day).toList(), people.size()));
    }

    // ------------------------------------------------------------------ monthly

    @Transactional(readOnly = true)
    public MonthlyReport monthly(AuthUser caller, int year, int month, Filter f, String scopeLabel) {
        if (month < 1 || month > 12) throw ApiException.badRequest("month must be 1-12");
        List<Employee> people = population(caller, f);
        AttendanceSettings cfg = settings.effective(f.projectId());
        ZoneId zone = ZoneId.of(cfg.getReportingTimezone());
        Instant now = Instant.now(clock);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
        if (from.isAfter(today)) {
            return new MonthlyReport(year, month, zone.getId(), now, scopeLabel, List.of(), totals(List.of(), 0));
        }
        Data d = load(people, from, to);
        List<MonthlyEmployee> result = new ArrayList<>();
        List<AttendanceCalculator.DayResult> all = new ArrayList<>();
        for (Employee e : people) {
            List<AttendanceCalculator.DayResult> days = new ArrayList<>();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) days.add(compute(e, day, d, zone, now));
            all.addAll(days);
            int present = (int) days.stream().filter(AttendanceCalculator.DayResult::present).count();
            long sess = days.stream().mapToLong(AttendanceCalculator.DayResult::sessionSeconds).sum();
            int bc = days.stream().mapToInt(AttendanceCalculator.DayResult::breakCount).sum();
            long bs = days.stream().mapToLong(AttendanceCalculator.DayResult::breakSeconds).sum();
            int ic = days.stream().mapToInt(AttendanceCalculator.DayResult::idleCount).sum();
            long is = days.stream().mapToLong(AttendanceCalculator.DayResult::idleSeconds).sum();
            int exc = days.stream().mapToInt(x -> x.exceptions().size()).sum();
            int ml = (int) days.stream().filter(AttendanceCalculator.DayResult::missingLogout).count();
            int noLogin = (int) days.stream().filter(x -> x.exceptions().contains("NO_LOGIN_RECORD")).count();
            int exceeded = days.stream().mapToInt(AttendanceCalculator.DayResult::exceededBreakCount).sum();
            result.add(new MonthlyEmployee(e, days, present, sess, bc, bs, bc == 0 ? 0 : bs / bc, ic, is, exc, ml, noLogin, exceeded));
        }
        Totals t = totals(all, people.size());
        int presentEmployees = (int) result.stream().filter(m -> m.daysPresent() > 0).count();
        t = new Totals(t.employees(), presentEmployees, t.sessionSeconds(), t.breakCount(), t.breakSeconds(), t.averageBreakSeconds(),
                t.idleCount(), t.idleSeconds(), t.exceptions(), t.missingLogouts());
        return new MonthlyReport(year, month, zone.getId(), now, scopeLabel, result, t);
    }

    // ------------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public AttendanceDtos.DashboardView dashboard(AuthUser caller, LocalDate date, Filter f) {
        List<Employee> people = population(caller, f);
        AttendanceSettings cfg = settings.effective(f.projectId());
        ZoneId zone = ZoneId.of(cfg.getReportingTimezone());
        Instant now = Instant.now(clock);
        LocalDate d0 = date == null ? LocalDate.ofInstant(now, zone) : date;
        boolean isToday = d0.equals(LocalDate.ofInstant(now, zone));
        Data d = load(people, d0, d0);
        long logged = 0, active = 0, onBreak = 0, idle = 0, away = 0, loggedOut = 0, notLogged = 0, missing = 0, breaksTotal = 0,
                breakSeconds = 0, exceeding = 0, exceptions = 0, compliant = 0, expected = 0;
        List<AttendanceDtos.LiveStatusRow> live = new ArrayList<>();
        long[] loginsByHour = new long[24];
        long[] breaksByHour = new long[24];
        for (Employee e : people) {
            var day = compute(e, d0, d, zone, now);
            List<AttendanceSession> ss = d.sessions.getOrDefault(key(e.userId(), d0), List.of());
            List<BreakRecord> bs = d.breaks.getOrDefault(key(e.userId(), d0), List.of());
            ss.forEach(s -> loginsByHour[s.getLoginAt().atZone(zone).getHour()]++);
            bs.forEach(b -> breaksByHour[b.getStartedAt().atZone(zone).getHour()]++);
            if (!day.weeklyOff() && (!isToday || day.present())) expected++;
            if (day.present()) logged++;
            if (day.missingLogout()) missing++;
            breaksTotal += day.breakCount();
            breakSeconds += day.breakSeconds();
            if (day.exceededBreakCount() > 0 || day.exceptions().contains("DAILY_BREAK_LIMIT_EXCEEDED")) exceeding++;
            List<String> realExceptions = day.exceptions().stream().filter(x -> !x.equals("NO_LOGIN_RECORD") || !isToday).toList();
            if (!realExceptions.isEmpty()) exceptions++;
            if (!day.weeklyOff() && day.present() && realExceptions.isEmpty()) compliant++;
            AttendanceSession current = ss.stream().filter(AttendanceSession::isActive)
                    .max(Comparator.comparing(s -> s.getLastHeartbeatAt() == null ? s.getLoginAt() : s.getLastHeartbeatAt())).orElse(null);
            String status;
            if (current != null) {
                status = current.getCurrentState();
                switch (status) {
                    case "ON_BREAK" -> onBreak++;
                    case "IDLE" -> idle++;
                    case "AWAY" -> away++;
                    default -> active++;
                }
            } else if (day.present()) {
                status = "LOGGED_OUT";
                loggedOut++;
            } else {
                status = day.weeklyOff() ? "WEEKLY_OFF" : "NOT_LOGGED_IN";
                if (!day.weeklyOff()) notLogged++;
            }
            BreakRecord activeBreak = bs.stream().filter(BreakRecord::isActive).findFirst().orElse(null);
            IdleEvent openIdle = d.idles.getOrDefault(key(e.userId(), d0), List.of()).stream().filter(i -> i.getEndedAt() == null).findFirst().orElse(null);
            live.add(new AttendanceDtos.LiveStatusRow(e.userId(), e.employeeCode(), e.fullName(), e.projectName(), e.teamName(), status,
                    day.firstLoginAt(), current == null ? null : current.getLastActivityAt(),
                    activeBreak == null ? null : activeBreak.getStartedAt(), activeBreak == null ? null : activeBreak.getBreakCategory(),
                    day.breakSeconds(), day.breakCount(), day.exceededBreakCount() > 0, openIdle == null ? null : openIdle.getIdleStartedAt()));
        }
        List<AttendanceDtos.HourBucket> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) hours.add(new AttendanceDtos.HourBucket(h, loginsByHour[h], breaksByHour[h]));
        return new AttendanceDtos.DashboardView(d0, zone.getId(), people.size(), logged, active, onBreak, idle, away, loggedOut, notLogged, missing,
                breaksTotal, breaksTotal == 0 ? 0 : breakSeconds / breaksTotal, exceeding, exceptions,
                expected == 0 ? 100.0 : Math.round(compliant * 1000.0 / expected) / 10.0, live, hours);
    }

    // ------------------------------------------------------------------ internals

    private record Data(Map<String, List<AttendanceSession>> sessions, Map<String, List<BreakRecord>> breaks,
                        Map<String, List<IdleEvent>> idles) {}

    private static String key(UUID user, LocalDate date) {
        return user + "|" + date;
    }

    private Data load(List<Employee> people, LocalDate from, LocalDate to) {
        if (people.isEmpty()) return new Data(Map.of(), Map.of(), Map.of());
        Set<UUID> ids = people.stream().map(Employee::userId).collect(Collectors.toSet());
        Map<String, List<AttendanceSession>> s = new HashMap<>();
        Map<String, List<BreakRecord>> b = new HashMap<>();
        Map<String, List<IdleEvent>> i = new HashMap<>();
        sessions.findByUserIdInAndWorkDateBetweenOrderByLoginAtAsc(ids, from, to)
                .forEach(x -> s.computeIfAbsent(key(x.getUserId(), x.getWorkDate()), k -> new ArrayList<>()).add(x));
        breaks.findByUserIdInAndWorkDateBetweenOrderByStartedAtAsc(ids, from, to)
                .forEach(x -> b.computeIfAbsent(key(x.getUserId(), x.getWorkDate()), k -> new ArrayList<>()).add(x));
        idles.findByUserIdInAndWorkDateBetweenOrderByIdleStartedAtAsc(ids, from, to)
                .forEach(x -> i.computeIfAbsent(key(x.getUserId(), x.getWorkDate()), k -> new ArrayList<>()).add(x));
        return new Data(s, b, i);
    }

    private AttendanceCalculator.DayResult compute(Employee e, LocalDate date, Data d, ZoneId zone, Instant now) {
        AttendanceSettings cfg = settings.effective(e.projectId());
        AttendanceCalculator.DayResult r = AttendanceCalculator.computeDay(date, d.sessions.getOrDefault(key(e.userId(), date), List.of()),
                d.breaks.getOrDefault(key(e.userId(), date), List.of()), d.idles.getOrDefault(key(e.userId(), date), List.of()),
                new AttendanceCalculator.Limits(cfg.getMaxDailyBreakMinutes(), cfg.getMaxBreaksPerDay(), cfg.getDefaultWorkHours()),
                settings::maxBreakMinutes, e.weeklyOffs(), zone, now);
        if (!r.present() && e.createdAt() != null && date.isBefore(LocalDate.ofInstant(e.createdAt(), zone))) {
            // employee did not exist yet: no attendance expected
            return new AttendanceCalculator.DayResult(r.date(), false, null, null, r.sessions(), 0, r.breaks(), 0, 0, 0, 0, r.idles(), 0, 0, 0,
                    false, false, List.of());
        }
        return r;
    }

    private static Totals totals(List<AttendanceCalculator.DayResult> days, int employees) {
        int present = (int) days.stream().filter(AttendanceCalculator.DayResult::present).count();
        long sess = days.stream().mapToLong(AttendanceCalculator.DayResult::sessionSeconds).sum();
        int bc = days.stream().mapToInt(AttendanceCalculator.DayResult::breakCount).sum();
        long bs = days.stream().mapToLong(AttendanceCalculator.DayResult::breakSeconds).sum();
        int ic = days.stream().mapToInt(AttendanceCalculator.DayResult::idleCount).sum();
        long is = days.stream().mapToLong(AttendanceCalculator.DayResult::idleSeconds).sum();
        int exc = days.stream().mapToInt(x -> x.exceptions().size()).sum();
        int ml = (int) days.stream().filter(AttendanceCalculator.DayResult::missingLogout).count();
        return new Totals(employees, present, sess, bc, bs, bc == 0 ? 0 : bs / bc, ic, is, exc, ml);
    }
}
