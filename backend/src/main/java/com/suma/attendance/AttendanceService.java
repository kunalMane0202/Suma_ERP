package com.suma.attendance;

import com.suma.audit.AuditService;
import com.suma.auth.SessionLifecycleListener;
import com.suma.auth.UserSession;
import com.suma.common.ApiException;
import com.suma.common.ClientInfo;
import com.suma.notification.NotificationService;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/**
 * Attendance engine: session-per-login tracking, breaks, application-level idle detection and administrative corrections.
 * Every state change writes an immutable {@link AttendanceEvent}.
 */
@Service
public class AttendanceService implements SessionLifecycleListener {
    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    public static final String SCOPE_NOTICE = "Idle detection is limited to activity inside the SUMA application tab "
            + "(keyboard, mouse, touch, page visibility and heartbeat). It cannot observe activity in other applications, and "
            + "no system-wide monitoring agent is installed.";

    private final AttendanceSessionRepository sessions;
    private final AttendanceEventRepository events;
    private final BreakRecordRepository breaks;
    private final IdleEventRepository idles;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceSettingsService settings;
    private final NotificationService notifications;
    private final AccessScopeService scope;
    private final UserRepository users;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AttendanceService(AttendanceSessionRepository sessions, AttendanceEventRepository events, BreakRecordRepository breaks,
                             IdleEventRepository idles, AttendanceCorrectionRepository corrections, AttendanceSettingsService settings,
                             NotificationService notifications, AccessScopeService scope, UserRepository users, AuditService audit,
                             JdbcTemplate jdbc, Clock clock) {
        this.sessions = sessions;
        this.events = events;
        this.breaks = breaks;
        this.idles = idles;
        this.corrections = corrections;
        this.settings = settings;
        this.notifications = notifications;
        this.scope = scope;
        this.users = users;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ login / logout

    @Override
    @Transactional
    public void onLogin(User user, UserSession authSession, ClientInfo ci) {
        if (sessions.findByAuthSessionId(authSession.getId()).isPresent()) return; // idempotent
        UUID projectId = primaryProject(user.getId());
        UUID teamId = primaryTeam(user.getId());
        ZoneId zone = settings.zone(projectId);
        Instant at = authSession.getCreatedAt();
        AttendanceSession s = new AttendanceSession();
        s.setUserId(user.getId());
        s.setAuthSessionId(authSession.getId());
        s.setProjectId(projectId);
        s.setTeamId(teamId);
        s.setWorkDate(AttendanceCalculator.workDate(at, zone));
        s.setLoginAt(at);
        s.setLastHeartbeatAt(at);
        s.setLastActivityAt(at);
        s.setLoginIp(ci.ip());
        s.setUserAgent(ci.userAgent());
        s.setDevice(ci.device());
        s.setBrowser(ci.browser());
        try {
            sessions.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            return; // concurrent duplicate for the same auth session: the other insert wins
        }
        event(user.getId(), s.getId(), "LOGIN", at, "AUTH", "ip=" + ci.ip() + " device=" + ci.device() + " browser=" + ci.browser(), null);
    }

    @Override
    @Transactional
    public void onSessionEnded(UUID authSessionId, String source, Instant at) {
        sessions.findByAuthSessionId(authSessionId).filter(AttendanceSession::isActive).ifPresent(s -> close(s, source, at));
    }

    /** Closes an attendance session, its open break and open idle event. */
    void close(AttendanceSession s, String source, Instant at) {
        if (at.isBefore(s.getLoginAt())) at = s.getLoginAt();
        for (BreakRecord b : breaks.findBySessionIdAndStatus(s.getId(), "ACTIVE")) {
            finishBreak(b, at, "SESSION_END", "AUTO_CLOSED");
        }
        Instant end = at;
        idles.findFirstBySessionIdAndEndedAtIsNull(s.getId()).ifPresent(i -> {
            i.setEndedAt(end.isBefore(i.getIdleStartedAt()) ? i.getIdleStartedAt() : end);
            i.setDurationSeconds(Duration.between(i.getIdleStartedAt(), i.getEndedAt()).toSeconds());
            if ("UNCONFIRMED".equals(i.getStatus())) i.setStatus("AUTO_RESOLVED");
            event(s.getUserId(), s.getId(), "IDLE_END", end, "SYSTEM", "session ended (" + source + ")", null);
        });
        s.setLogoutAt(at);
        s.setLogoutSource(source);
        s.setDurationSeconds(Duration.between(s.getLoginAt(), at).toSeconds());
        s.setCurrentState("OFFLINE");
        s.setStatus(switch (source) {
            case "USER_LOGOUT", "BROWSER_UNLOAD" -> "LOGGED_OUT";
            case "HEARTBEAT_TIMEOUT", "RECONCILIATION" -> "MISSING_LOGOUT";
            default -> "AUTO_CLOSED";
        });
        event(s.getUserId(), s.getId(), "LOGOUT", at, source, "status=" + s.getStatus() + " duration=" + s.getDurationSeconds() + "s", null);
    }

    // ------------------------------------------------------------------ heartbeat

    @Transactional
    public AttendanceDtos.IdleStatus heartbeat(AuthUser me, AttendanceDtos.HeartbeatRequest r) {
        AttendanceSession s = activeSession(me);
        Instant now = Instant.now(clock);
        s.setLastHeartbeatAt(now);
        if (r != null && r.lastActivityAt() != null) {
            Instant act = r.lastActivityAt().isAfter(now) ? now : r.lastActivityAt();
            if (act.isAfter(s.getLoginAt()) && (s.getLastActivityAt() == null || act.isAfter(s.getLastActivityAt()))) {
                s.setLastActivityAt(act);
            }
        }
        return idleStatus(me, s);
    }

    // ------------------------------------------------------------------ breaks

    @Transactional
    public AttendanceDtos.BreakDto startBreak(AuthUser me, String category, String notes, String source) {
        AttendanceSession s = activeSession(me);
        String cat = category == null || category.isBlank() ? "GENERAL" : category;
        if (settings.categories().stream().noneMatch(c -> c.getCode().equals(cat) && c.isActive()))
            throw ApiException.badRequest("Unknown break category: " + cat);
        if (breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE").isPresent())
            throw ApiException.conflict("A break is already in progress. End it before starting another.");
        Instant now = Instant.now(clock);
        BreakRecord b = new BreakRecord();
        b.setUserId(me.id());
        b.setSessionId(s.getId());
        b.setProjectId(s.getProjectId());
        b.setBreakCategory(cat);
        b.setWorkDate(AttendanceCalculator.workDate(now, settings.zone(s.getProjectId())));
        b.setStartedAt(now);
        b.setStartSource(source);
        b.setNotes(notes);
        try {
            breaks.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("A break is already in progress.");
        }
        s.setCurrentState("ON_BREAK");
        s.setLastActivityAt(now);
        event(me.id(), s.getId(), "BREAK_START", now, source, "category=" + cat, null);
        notifications.notify(me.id(), "BREAK_STARTED", "Break started", "Your " + label(cat) + " started at " + now, "INFO", "BreakRecord", b.getId());
        return toDto(b);
    }

    @Transactional
    public AttendanceDtos.BreakDto endBreak(AuthUser me, String source) {
        BreakRecord b = breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE")
                .orElseThrow(() -> ApiException.conflict("No break is in progress"));
        Instant now = Instant.now(clock);
        finishBreak(b, now, source, "COMPLETED");
        sessions.findById(b.getSessionId()).filter(AttendanceSession::isActive).ifPresent(s -> {
            s.setCurrentState("WORKING");
            s.setLastActivityAt(now);
        });
        AttendanceDtos.BreakDto dto = toDto(b);
        notifications.notify(me.id(), "BREAK_ENDED", "Break ended",
                "Break duration " + formatDuration(dto.durationSeconds()) + (dto.exceeded() ? " (exceeded configured limit)" : ""),
                dto.exceeded() ? "WARNING" : "INFO", "BreakRecord", b.getId());
        return dto;
    }

    private void finishBreak(BreakRecord b, Instant at, String source, String status) {
        if (at.isBefore(b.getStartedAt())) at = b.getStartedAt();
        b.setEndedAt(at);
        b.setDurationSeconds(Duration.between(b.getStartedAt(), at).toSeconds());
        b.setEndSource(source);
        b.setStatus(status);
        Integer max = settings.maxBreakMinutes(b.getProjectId(), b.getBreakCategory());
        if (max != null && b.getDurationSeconds() > max * 60L) b.setExceeded(true);
        event(b.getUserId(), b.getSessionId(), "BREAK_END", at, source,
                "category=" + b.getBreakCategory() + " duration=" + b.getDurationSeconds() + "s" + (b.isExceeded() ? " exceeded" : ""), null);
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.BreakDto currentBreak(AuthUser me) {
        return breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE").map(this::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.BreakDto> breakHistory(AuthUser me, UUID userId, LocalDate from, LocalDate to) {
        UUID target = userId == null ? me.id() : userId;
        if (!scope.canViewAttendanceOf(me, target)) throw ApiException.forbidden("You cannot view this employee's attendance");
        LocalDate t = to == null ? LocalDate.now(clock) : to;
        LocalDate f = from == null ? t.minusDays(30) : from;
        if (f.isAfter(t)) throw ApiException.badRequest("from must be before to");
        return breaks.findByUserIdAndWorkDateBetweenOrderByStartedAtDesc(target, f, t).stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ idle

    /** Browser reports inactivity that reached the configured threshold. Idempotent per session. */
    @Transactional
    public AttendanceDtos.IdleDto reportIdle(AuthUser me, Instant idleStartedAt, String source) {
        AttendanceSession s = activeSession(me);
        Optional<IdleEvent> open = idles.findFirstBySessionIdAndEndedAtIsNull(s.getId());
        if (open.isPresent()) return toDto(open.get());
        Instant now = Instant.now(clock);
        Instant start = idleStartedAt == null ? now : idleStartedAt;
        if (start.isAfter(now)) start = now;
        if (start.isBefore(s.getLoginAt())) start = s.getLoginAt();
        return toDto(createIdle(s, start, now, source));
    }

    IdleEvent createIdle(AttendanceSession s, Instant start, Instant now, String source) {
        AttendanceSettings cfg = settings.effective(s.getProjectId());
        IdleEvent i = new IdleEvent();
        i.setUserId(s.getUserId());
        i.setSessionId(s.getId());
        i.setWorkDate(AttendanceCalculator.workDate(start, settings.zone(s.getProjectId())));
        i.setIdleStartedAt(start);
        i.setDetectedAt(now);
        i.setSource(source);
        idles.saveAndFlush(i);
        if (!"ON_BREAK".equals(s.getCurrentState())) s.setCurrentState("IDLE");
        event(s.getUserId(), s.getId(), "IDLE_START", start, source, "detected=" + now + " threshold=" + cfg.getIdleThresholdMinutes() + "m", null);
        notifications.notify(s.getUserId(), "INACTIVITY_DETECTED", "Are you still working?",
                "No activity was detected in SUMA for " + cfg.getIdleThresholdMinutes() + " minutes. Please confirm your status.",
                "WARNING", "IdleEvent", i.getId());
        if (cfg.isNotifyTeamLeaderOnIdle()) {
            String name = users.findById(s.getUserId()).map(User::getFullName).orElse("An employee");
            Set<UUID> leaders = teamLeadersOf(s.getUserId());
            if (!leaders.isEmpty()) {
                notifications.notifyAll(leaders, "TEAM_MEMBER_IDLE", "Team member inactive",
                        name + " has had no activity in SUMA for " + cfg.getIdleThresholdMinutes() + " minutes (unconfirmed).",
                        "INFO", "IdleEvent", i.getId());
                i.setTeamLeaderNotified(true);
            }
        }
        return i;
    }

    @Transactional
    public AttendanceDtos.IdleDto confirmIdle(AuthUser me, UUID idleId, String response, String breakCategory) {
        IdleEvent i = idles.findById(idleId).orElseThrow(() -> ApiException.notFound("Idle event"));
        if (!i.getUserId().equals(me.id())) throw ApiException.forbidden("This inactivity prompt belongs to another user");
        if (i.getResponse() != null) return toDto(i); // already answered: idempotent
        Instant now = Instant.now(clock);
        i.setResponse(response);
        i.setRespondedAt(now);
        i.setStatus("CONFIRMED");
        if (i.getEndedAt() == null) {
            i.setEndedAt(now);
            i.setDurationSeconds(Duration.between(i.getIdleStartedAt(), now).toSeconds());
        }
        event(me.id(), i.getSessionId(), "STATUS_CONFIRMED", now, "EMPLOYEE", "response=" + response + " idleEvent=" + i.getId(), me.id());
        event(me.id(), i.getSessionId(), "IDLE_END", now, "EMPLOYEE", "confirmed " + response, me.id());
        AttendanceSession s = sessions.findById(i.getSessionId()).orElse(null);
        if (s != null && s.isActive()) {
            s.setLastActivityAt(now);
            switch (response) {
                case "WORKING" -> s.setCurrentState(breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE").isPresent() ? "ON_BREAK" : "WORKING");
                case "AWAY" -> s.setCurrentState("AWAY");
                case "ON_BREAK" -> {
                    if (breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE").isEmpty()) {
                        AttendanceDtos.BreakDto b = startBreak(me, breakCategory, "Started from inactivity prompt", "IDLE_CONFIRMATION");
                        i.setLinkedBreakId(b.id());
                    }
                    s.setCurrentState("ON_BREAK");
                }
                default -> throw ApiException.badRequest("Invalid response");
            }
        }
        return toDto(i);
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.IdleStatus idleStatus(AuthUser me) {
        return idleStatus(me, sessions.findByAuthSessionId(me.sessionId()).orElse(null));
    }

    private AttendanceDtos.IdleStatus idleStatus(AuthUser me, AttendanceSession s) {
        int threshold = settings.effective(s == null ? null : s.getProjectId()).getIdleThresholdMinutes();
        IdleEvent open = s == null ? null : idles.findFirstBySessionIdAndEndedAtIsNull(s.getId()).orElse(null);
        return new AttendanceDtos.IdleStatus(threshold, s == null ? "OFFLINE" : s.getCurrentState(), open == null ? null : toDto(open),
                s == null ? null : s.getLastActivityAt(), false, SCOPE_NOTICE);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.IdleDto> idleHistory(AuthUser me, UUID userId, LocalDate from, LocalDate to) {
        UUID target = userId == null ? me.id() : userId;
        if (!scope.canViewAttendanceOf(me, target)) throw ApiException.forbidden("You cannot view this employee's attendance");
        LocalDate t = to == null ? LocalDate.now(clock) : to;
        LocalDate f = from == null ? t.minusDays(30) : from;
        return idles.findByUserIdAndWorkDateBetweenOrderByIdleStartedAtDesc(target, f, t).stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ today / me

    @Transactional(readOnly = true)
    public AttendanceDtos.TodayView today(AuthUser me) {
        AttendanceSession current = sessions.findByAuthSessionId(me.sessionId()).orElse(null);
        UUID projectId = current != null ? current.getProjectId() : primaryProject(me.id());
        AttendanceSettings cfg = settings.effective(projectId);
        ZoneId zone = ZoneId.of(cfg.getReportingTimezone());
        Instant now = Instant.now(clock);
        LocalDate date = LocalDate.ofInstant(now, zone);
        User u = users.findById(me.id()).orElseThrow();
        var ss = sessions.findByUserIdAndWorkDateOrderByLoginAtAsc(me.id(), date);
        var bs = breaks.findByUserIdAndWorkDateOrderByStartedAtAsc(me.id(), date);
        var is = idles.findByUserIdAndWorkDateOrderByIdleStartedAtAsc(me.id(), date);
        var day = AttendanceCalculator.computeDay(date, ss, bs, is,
                new AttendanceCalculator.Limits(cfg.getMaxDailyBreakMinutes(), cfg.getMaxBreaksPerDay(), cfg.getDefaultWorkHours()),
                settings::maxBreakMinutes, u.getWeeklyOffs(), zone, now);
        BreakRecord active = breaks.findFirstByUserIdAndStatus(me.id(), "ACTIVE").orElse(null);
        IdleEvent openIdle = current == null ? null : idles.findFirstBySessionIdAndEndedAtIsNull(current.getId()).orElse(null);
        return new AttendanceDtos.TodayView(date, zone.getId(), current == null ? null : toDto(current, now),
                current == null ? "OFFLINE" : current.getCurrentState(), active == null ? null : toDto(active),
                day.firstLoginAt(), day.sessionSeconds(), day.breakCount(), day.breakSeconds(), day.averageBreakSeconds(),
                day.idleCount(), day.idleSeconds(), bs.stream().map(this::toDto).toList(), is.stream().map(this::toDto).toList(),
                ss.stream().map(x -> toDto(x, now)).toList(), day.exceptions(), cfg.getIdleThresholdMinutes(),
                settings.maxBreakMinutes(projectId, "GENERAL"), cfg.getMaxDailyBreakMinutes(), cfg.getMaxBreaksPerDay(),
                settings.categories().stream().filter(BreakCategory::isActive)
                        .map(c -> new AttendanceDtos.CategoryDto(c.getCode(), c.getLabel(), c.isActive())).toList(),
                openIdle == null ? null : toDto(openIdle));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.SessionDto> sessionsOf(AuthUser me, UUID userId, LocalDate from, LocalDate to) {
        UUID target = userId == null ? me.id() : userId;
        if (!scope.canViewAttendanceOf(me, target)) throw ApiException.forbidden("You cannot view this employee's attendance");
        Instant now = Instant.now(clock);
        return sessions.findByUserIdInAndWorkDateBetweenOrderByLoginAtAsc(List.of(target), from, to).stream()
                .map(s -> toDto(s, now)).toList();
    }

    // ------------------------------------------------------------------ corrections

    @Transactional
    public AttendanceDtos.CorrectionDto correct(AuthUser admin, AttendanceDtos.CorrectionRequest r) {
        AttendanceCorrection c = new AttendanceCorrection();
        c.setTargetType(r.targetType());
        c.setTargetId(r.targetId());
        c.setField(r.field());
        c.setReason(r.reason());
        c.setCorrectedBy(admin.id());
        UUID userId;
        UUID sessionId;
        if ("SESSION".equals(r.targetType())) {
            AttendanceSession s = sessions.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("Attendance session"));
            if (s.isActive()) throw ApiException.badRequest("Active sessions cannot be corrected; end the session first");
            Instant v = parseInstant(r.value());
            switch (r.field()) {
                case "logoutAt" -> {
                    if (v.isBefore(s.getLoginAt())) throw ApiException.badRequest("Logout cannot be before login");
                    c.setOriginalValue(String.valueOf(s.getLogoutAt()));
                    s.setLogoutAt(v);
                    if ("MISSING_LOGOUT".equals(s.getStatus())) s.setStatus("LOGGED_OUT");
                }
                case "loginAt" -> {
                    if (s.getLogoutAt() != null && v.isAfter(s.getLogoutAt())) throw ApiException.badRequest("Login cannot be after logout");
                    c.setOriginalValue(String.valueOf(s.getLoginAt()));
                    s.setLoginAt(v);
                    s.setWorkDate(AttendanceCalculator.workDate(v, settings.zone(s.getProjectId())));
                }
                default -> throw ApiException.badRequest("Field not applicable to a session");
            }
            if (s.getLogoutAt() != null) s.setDurationSeconds(Duration.between(s.getLoginAt(), s.getLogoutAt()).toSeconds());
            c.setCorrectedValue(v.toString());
            userId = s.getUserId();
            sessionId = s.getId();
        } else {
            BreakRecord b = breaks.findById(r.targetId()).orElseThrow(() -> ApiException.notFound("Break"));
            if (b.isActive()) throw ApiException.badRequest("Active breaks cannot be corrected");
            switch (r.field()) {
                case "startedAt", "endedAt" -> {
                    Instant v = parseInstant(r.value());
                    Instant start = "startedAt".equals(r.field()) ? v : b.getStartedAt();
                    Instant end = "endedAt".equals(r.field()) ? v : b.getEndedAt();
                    if (end != null && end.isBefore(start)) throw ApiException.badRequest("Break end cannot be before start");
                    c.setOriginalValue(String.valueOf("startedAt".equals(r.field()) ? b.getStartedAt() : b.getEndedAt()));
                    b.setStartedAt(start);
                    b.setEndedAt(end);
                    b.setDurationSeconds(Duration.between(start, end).toSeconds());
                    Integer max = settings.maxBreakMinutes(b.getProjectId(), b.getBreakCategory());
                    b.setExceeded(max != null && b.getDurationSeconds() > max * 60L);
                    c.setCorrectedValue(v.toString());
                }
                case "breakCategory" -> {
                    if (settings.categories().stream().noneMatch(x -> x.getCode().equals(r.value()))) throw ApiException.badRequest("Unknown category");
                    c.setOriginalValue(b.getBreakCategory());
                    b.setBreakCategory(r.value());
                    c.setCorrectedValue(r.value());
                }
                default -> throw ApiException.badRequest("Field not applicable to a break");
            }
            userId = b.getUserId();
            sessionId = b.getSessionId();
        }
        corrections.save(c);
        event(userId, sessionId, "CORRECTION", Instant.now(clock), "ADMIN",
                r.targetType() + "." + r.field() + ": " + c.getOriginalValue() + " -> " + c.getCorrectedValue() + " (" + r.reason() + ")", admin.id());
        audit.log("ATTENDANCE_CORRECTED", r.targetType(), r.targetId(), r.field() + " " + c.getOriginalValue() + " -> " + c.getCorrectedValue() + " reason: " + r.reason());
        notifications.notify(userId, "ATTENDANCE_CORRECTED", "Attendance record corrected",
                "An administrator corrected " + r.field() + " on your attendance: " + r.reason(), "INFO", r.targetType(), r.targetId());
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.CorrectionDto> corrections() {
        return corrections.findTop200ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ helpers

    public AttendanceSession activeSession(AuthUser me) {
        AttendanceSession s = sessions.findByAuthSessionId(me.sessionId())
                .orElseThrow(() -> ApiException.conflict("No attendance session for the current login. Sign in again."));
        if (!s.isActive()) throw ApiException.conflict("Your attendance session has ended. Sign in again.");
        return s;
    }

    void event(UUID userId, UUID sessionId, String type, Instant at, String source, String details, UUID createdBy) {
        AttendanceEvent e = new AttendanceEvent();
        e.setUserId(userId);
        e.setSessionId(sessionId);
        e.setEventType(type);
        e.setOccurredAt(at);
        e.setSource(source);
        e.setDetails(details);
        e.setCreatedBy(createdBy);
        events.save(e);
    }

    UUID primaryProject(UUID userId) {
        List<UUID> ids = jdbc.queryForList("""
                select pm.project_id from project_members pm join projects p on p.id = pm.project_id
                where pm.user_id = ? and p.status = 'ACTIVE' order by pm.is_primary desc, pm.assigned_at asc limit 1
                """, UUID.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    UUID primaryTeam(UUID userId) {
        List<UUID> ids = jdbc.queryForList("""
                select t.id from team_members tm join teams t on t.id = tm.team_id where tm.user_id = ? and t.active
                order by tm.joined_at asc limit 1
                """, UUID.class, userId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Set<UUID> teamLeadersOf(UUID userId) {
        return new HashSet<>(jdbc.queryForList("""
                select t.team_leader_id from teams t join team_members tm on tm.team_id = t.id
                  where tm.user_id = ? and t.team_leader_id is not null and t.team_leader_id <> ?
                union select p.team_leader_id from projects p join project_members pm on pm.project_id = p.id
                  where pm.user_id = ? and p.team_leader_id is not null and p.team_leader_id <> ?
                """, UUID.class, userId, userId, userId, userId));
    }

    private static Instant parseInstant(String v) {
        try {
            return Instant.parse(v);
        } catch (DateTimeException e) {
            try {
                return OffsetDateTime.parse(v).toInstant();
            } catch (DateTimeException e2) {
                throw ApiException.badRequest("Timestamp must be ISO-8601 with offset, e.g. 2026-01-31T18:00:00Z");
            }
        }
    }

    private String label(String cat) {
        return settings.categories().stream().filter(c -> c.getCode().equals(cat)).map(BreakCategory::getLabel).findFirst().orElse(cat).toLowerCase();
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%dh %02dm", h, m) : String.format("%dm %02ds", m, s);
    }

    AttendanceDtos.BreakDto toDto(BreakRecord b) {
        Instant now = Instant.now(clock);
        long dur = b.getDurationSeconds() != null ? b.getDurationSeconds() : Duration.between(b.getStartedAt(), now).toSeconds();
        Integer max = settings.maxBreakMinutes(b.getProjectId(), b.getBreakCategory());
        boolean exceeded = b.isExceeded() || (max != null && dur > max * 60L);
        return new AttendanceDtos.BreakDto(b.getId(), b.getUserId(), b.getBreakCategory(), b.getStartedAt(), b.getEndedAt(), dur,
                b.getStatus(), exceeded, max, b.getStartSource(), b.getEndSource(), b.getNotes(), b.getWorkDate());
    }

    AttendanceDtos.IdleDto toDto(IdleEvent i) {
        return new AttendanceDtos.IdleDto(i.getId(), i.getIdleStartedAt(), i.getDetectedAt(), i.getEndedAt(), i.getDurationSeconds(),
                i.getStatus(), i.getResponse(), i.getRespondedAt(), i.getSource());
    }

    AttendanceDtos.SessionDto toDto(AttendanceSession s, Instant now) {
        long dur = s.getDurationSeconds() != null ? s.getDurationSeconds()
                : Duration.between(s.getLoginAt(), s.isActive() ? now : s.getLoginAt()).toSeconds();
        return new AttendanceDtos.SessionDto(s.getId(), s.getAuthSessionId(), s.getLoginAt(), s.getLogoutAt(), s.getLogoutSource(),
                s.getStatus(), s.getCurrentState(), s.getLoginIp(), s.getDevice(), s.getBrowser(), dur, s.getLastHeartbeatAt(), s.getWorkDate());
    }

    private AttendanceDtos.CorrectionDto toDto(AttendanceCorrection c) {
        String name = users.findById(c.getCorrectedBy()).map(User::getFullName).orElse(null);
        return new AttendanceDtos.CorrectionDto(c.getId(), c.getTargetType(), c.getTargetId(), c.getField(), c.getOriginalValue(),
                c.getCorrectedValue(), c.getReason(), c.getCorrectedBy(), name, c.getCreatedAt());
    }

}
