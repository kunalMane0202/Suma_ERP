package com.suma.config;

import com.suma.attendance.*;
import com.suma.auth.UserSession;
import com.suma.auth.UserSessionRepository;
import com.suma.automation.AutomationRule;
import com.suma.automation.AutomationRuleRepository;
import com.suma.client.Client;
import com.suma.client.ClientRepository;
import com.suma.knowledge.KbArticle;
import com.suma.knowledge.KbArticleRepository;
import com.suma.knowledge.KbCategory;
import com.suma.knowledge.KbCategoryRepository;
import com.suma.project.*;
import com.suma.sla.*;
import com.suma.team.Team;
import com.suma.team.TeamRepository;
import com.suma.ticket.Ticket;
import com.suma.ticket.TicketDtos;
import com.suma.ticket.TicketRepository;
import com.suma.ticket.TicketService;
import com.suma.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Optional demonstration data for local development and UI review (suma.demo-data=true). Never enable in production.
 * All demo accounts use the password "Password@123" and @demo.suma.local email addresses (not deliverable).
 */
@Component
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "Password@123";
    private final SumaProperties props;
    private final ClientRepository clients;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TicketCategoryRepository categories;
    private final TeamRepository teams;
    private final UserRepository users;
    private final RoleRepository roles;
    private final ShiftRepository shifts;
    private final SlaPolicyRepository slaPolicies;
    private final BusinessCalendarRepository calendars;
    private final BusinessHoursRepository businessHours;
    private final EscalationRuleRepository escalations;
    private final TicketService ticketService;
    private final TicketRepository tickets;
    private final UserSessionRepository userSessions;
    private final AttendanceSessionRepository attSessions;
    private final BreakRecordRepository breaks;
    private final IdleEventRepository idles;
    private final AttendanceSettingsRepository attSettings;
    private final KbArticleRepository articles;
    private final KbCategoryRepository kbCategories;
    private final AutomationRuleRepository rules;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final Random random = new Random(42);

    public DemoDataSeeder(SumaProperties props, ClientRepository clients, ProjectRepository projects, ProjectMemberRepository members,
                          TicketCategoryRepository categories, TeamRepository teams, UserRepository users, RoleRepository roles,
                          ShiftRepository shifts, SlaPolicyRepository slaPolicies, BusinessCalendarRepository calendars,
                          BusinessHoursRepository businessHours, EscalationRuleRepository escalations, TicketService ticketService,
                          TicketRepository tickets, UserSessionRepository userSessions, AttendanceSessionRepository attSessions,
                          BreakRecordRepository breaks, IdleEventRepository idles, AttendanceSettingsRepository attSettings,
                          KbArticleRepository articles, KbCategoryRepository kbCategories, AutomationRuleRepository rules,
                          PasswordEncoder encoder, TransactionTemplate tx, JdbcTemplate jdbc) {
        this.props = props;
        this.clients = clients;
        this.projects = projects;
        this.members = members;
        this.categories = categories;
        this.teams = teams;
        this.users = users;
        this.roles = roles;
        this.shifts = shifts;
        this.slaPolicies = slaPolicies;
        this.calendars = calendars;
        this.businessHours = businessHours;
        this.escalations = escalations;
        this.ticketService = ticketService;
        this.tickets = tickets;
        this.userSessions = userSessions;
        this.attSessions = attSessions;
        this.breaks = breaks;
        this.idles = idles;
        this.attSettings = attSettings;
        this.articles = articles;
        this.kbCategories = kbCategories;
        this.rules = rules;
        this.encoder = encoder;
        this.tx = tx;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.demoData() || clients.count() > 0) return;
        log.warn("Seeding DEMO data (suma.demo-data=true). Do not enable this in production.");
        tx.executeWithoutResult(s -> seed());
        log.info("Demo data seeded. Demo accounts use password {}", DEMO_PASSWORD);
    }

    private void seed() {
        String hash = encoder.encode(DEMO_PASSWORD);
        Shift day = shift("General (09:00-18:00)", LocalTime.of(9, 0), LocalTime.of(18, 0));
        Shift early = shift("Early (06:00-15:00)", LocalTime.of(6, 0), LocalTime.of(15, 0));

        User manager = user("EMP-1001", "manager", "Priya Menon", "MANAGER", "Delivery Manager", hash, day);
        User tl1 = user("EMP-1002", "tl.arjun", "Arjun Rao", "TEAM_LEADER", "Team Leader - Applications", hash, day);
        User tl2 = user("EMP-1003", "tl.sara", "Sara Thomas", "TEAM_LEADER", "Team Leader - Infrastructure", hash, early);
        User lead = user("EMP-1004", "lead.kiran", "Kiran Shah", "L3", "Project Lead / Senior Engineer", hash, day);
        List<User> engineers = new ArrayList<>();
        String[][] eng = {{"EMP-2001", "l1.neha", "Neha Gupta", "L1"}, {"EMP-2002", "l1.rahul", "Rahul Verma", "L1"},
                {"EMP-2003", "l1.anita", "Anita D'Souza", "L1"}, {"EMP-2004", "l2.vikram", "Vikram Singh", "L2"},
                {"EMP-2005", "l2.meera", "Meera Iyer", "L2"}, {"EMP-2006", "l3.omar", "Omar Khan", "L3"},
                {"EMP-2007", "l1.joseph", "Joseph Fernandes", "L1"}, {"EMP-2008", "l2.divya", "Divya Nair", "L2"}};
        for (String[] e : eng) engineers.add(user(e[0], e[1], e[2], e[3], e[3] + " Support Engineer", hash, e[3].equals("L1") && random.nextBoolean() ? early : day));

        BusinessCalendar cal = new BusinessCalendar();
        cal.setName("India Business Hours");
        cal.setTimezone("Asia/Kolkata");
        calendars.save(cal);
        for (int d = 1; d <= 6; d++) {
            BusinessHours h = new BusinessHours();
            h.setCalendarId(cal.getId());
            h.setDayOfWeek((short) d);
            h.setStartTime(LocalTime.of(9, 0));
            h.setEndTime(d == 6 ? LocalTime.of(13, 0) : LocalTime.of(18, 0));
            businessHours.save(h);
        }

        Client acme = client("ACME", "Acme Retail Pvt Ltd", "Retail", manager);
        Client globex = client("GLOBEX", "Globex Financial Services", "Banking", manager);
        Client initech = client("INITECH", "Initech Healthcare", "Healthcare", manager);

        Project p1 = project(acme, "ACME-ERP", "Acme ERP Support", manager, tl1, lead, cal);
        Project p2 = project(globex, "GLX-CORE", "Globex Core Banking L2", manager, tl2, null, null);
        Project p3 = project(initech, "INI-HIS", "Initech Hospital Information System", manager, tl1, lead, cal);
        p2.setProjectLeadEmail("glx.lead@demo.suma.local");
        p2.setProjectLeadEmailVerified(false);

        member(p1, manager, "MANAGER", false); member(p1, tl1, "TEAM_LEADER", true); member(p1, lead, "PROJECT_LEAD", true);
        member(p2, manager, "MANAGER", true); member(p2, tl2, "TEAM_LEADER", true);
        member(p3, tl1, "TEAM_LEADER", false); member(p3, lead, "PROJECT_LEAD", false); member(p3, manager, "MANAGER", false);
        for (int i = 0; i < engineers.size(); i++) {
            Project p = i < 4 ? p1 : i < 6 ? p2 : p3;
            member(p, engineers.get(i), "MEMBER", true);
            if (i == 3) member(p3, engineers.get(i), "MEMBER", false);
        }

        Team appTeam = team("Application Support", p1, tl1, List.of(engineers.get(0), engineers.get(1), engineers.get(2), engineers.get(3), lead));
        Team infraTeam = team("Infrastructure & Core Banking", p2, tl2, List.of(engineers.get(4), engineers.get(5)));
        Team hisTeam = team("HIS Clinical Apps", p3, tl1, List.of(engineers.get(6), engineers.get(7)));

        Map<String, TicketCategory> cats = new HashMap<>();
        for (Project p : List.of(p1, p2, p3)) {
            TicketCategory access = category(p, null, "Access & Accounts", "password,login,access,locked,account", null);
            TicketCategory app = category(p, null, "Application Issue", "error,exception,crash,bug,screen,report", null);
            TicketCategory infra = category(p, null, "Infrastructure", "server,network,disk,cpu,vpn,database,slow", null);
            category(p, access.getId(), "Password reset", "password,reset", null);
            category(p, app.getId(), "Report failure", "report,export", null);
            cats.put(p.getCode() + "-ACCESS", access);
            cats.put(p.getCode() + "-APP", app);
            cats.put(p.getCode() + "-INFRA", infra);
        }

        int[][] targets = {{30, 240}, {60, 480}, {240, 1440}, {480, 2880}};
        String[] pr = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
        for (int i = 0; i < 4; i++) {
            SlaPolicy sp = new SlaPolicy();
            sp.setName(pr[i].charAt(0) + pr[i].substring(1).toLowerCase() + " (demo default)");
            sp.setPriority(pr[i]);
            sp.setResponseMinutes(targets[i][0]);
            sp.setResolutionMinutes(targets[i][1]);
            sp.setBusinessHoursOnly(i >= 2);
            slaPolicies.save(sp);
        }
        EscalationRule er = new EscalationRule();
        er.setTriggerType("RESOLUTION_PERCENT");
        er.setThresholdPercent(80);
        er.setEscalationLevel(1);
        er.setNotifyRole("TEAM_LEADER");
        escalations.save(er);
        EscalationRule er2 = new EscalationRule();
        er2.setTriggerType("RESOLUTION_BREACH");
        er2.setEscalationLevel(2);
        er2.setNotifyRole("MANAGER");
        escalations.save(er2);
        slaPolicies.flush();

        AutomationRule ar = new AutomationRule();
        ar.setName("Auto-categorise new tickets");
        ar.setDescription("Assigns a category from keywords in the title/description");
        ar.setTriggerEvent("TICKET_CREATED");
        ar.setConditions("[{\"field\":\"categoryId\",\"op\":\"empty\"}]");
        ar.setActions("[{\"type\":\"AUTO_CATEGORIZE\"}]");
        ar.setPriority(10);
        rules.save(ar);
        AutomationRule ar2 = new AutomationRule();
        ar2.setName("Critical tickets: assign least-loaded engineer");
        ar2.setTriggerEvent("TICKET_CREATED");
        ar2.setConditions("[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"CRITICAL\"},{\"field\":\"assigneeId\",\"op\":\"empty\"}]");
        ar2.setActions("[{\"type\":\"ASSIGN_LEAST_LOADED\"},{\"type\":\"NOTIFY_ROLE\",\"role\":\"TEAM_LEADER\",\"message\":\"Critical ticket auto-assigned\"}]");
        ar2.setPriority(20);
        rules.save(ar2);

        // ---- tickets spread over the last 30 days
        String[] titles = {"Unable to login to ERP portal - account locked", "Sales report export fails with timeout",
                "VPN disconnects frequently for branch users", "Password reset request for finance team", "Invoice screen throws NullPointer error",
                "Database server CPU at 95%", "New user access for warehouse module", "Monthly GST report shows wrong totals",
                "Patient registration page slow", "Printer mapping not working after upgrade", "Disk space alert on app server",
                "Payment gateway callback errors", "Lab results interface down", "User cannot access shared drive", "Batch job failed overnight"};
        Instant now = Instant.now();
        List<Project> ps = List.of(p1, p2, p3);
        for (int i = 0; i < 70; i++) {
            Project p = ps.get(i % 3);
            List<User> pool = engineers.stream().filter(u -> members.findById(new ProjectMember.Key(p.getId(), u.getId())).isPresent()).toList();
            String priority = pr[Math.min(3, random.nextInt(10) < 1 ? 0 : random.nextInt(10) < 3 ? 1 : random.nextInt(10) < 7 ? 2 : 3)];
            Ticket t = ticketService.createInternal(new TicketDtos.CreateRequest(p.getId(), titles[i % titles.length] + " #" + (i + 1),
                    "Reported by client user. Steps to reproduce and screenshots attached in the client portal.", priority, null, null,
                    null, null, "Client User " + (i % 7 + 1), "client.user" + (i % 7 + 1) + "@demo.suma.local", null), i % 5 == 0 ? "EMAIL" : "MANUAL", null, null, manager.getId());
            Instant created = now.minus(Duration.ofHours(random.nextInt(24 * 30) + 1));
            // backdate creation and SLA targets consistently
            long shift = Duration.between(created, t.getCreatedAt()).toSeconds();
            t.setCreatedAt(created);
            if (t.getResponseDueAt() != null) t.setResponseDueAt(t.getResponseDueAt().minusSeconds(shift));
            if (t.getResolutionDueAt() != null) t.setResolutionDueAt(t.getResolutionDueAt().minusSeconds(shift));
            if (!pool.isEmpty() && random.nextInt(10) < 8) {
                User a = pool.get(random.nextInt(pool.size()));
                ticketService.assignInternal(t, a.getId(), p.getCode().equals("ACME-ERP") ? appTeam.getId() : p.getCode().equals("GLX-CORE") ? infraTeam.getId() : hisTeam.getId(), tl1.getId(), null);
                int roll = random.nextInt(10);
                Instant response = created.plus(Duration.ofMinutes(10 + random.nextInt(300)));
                if (roll >= 2 && response.isBefore(now)) {
                    t.setFirstResponseAt(response);
                    t.setResponseBreached(t.getResponseDueAt() != null && response.isAfter(t.getResponseDueAt()));
                    t.setStatus(roll < 4 ? "IN_PROGRESS" : roll < 5 ? "PENDING" : "RESOLVED");
                    if ("PENDING".equals(t.getStatus())) t.setSlaPausedAt(now.minus(Duration.ofHours(2)));
                    if ("RESOLVED".equals(t.getStatus())) {
                        Instant resolved = response.plus(Duration.ofMinutes(30 + random.nextInt(2400)));
                        if (resolved.isAfter(now)) resolved = now.minus(Duration.ofMinutes(5));
                        t.setResolvedAt(resolved);
                        t.setResolutionBreached(t.getResolutionDueAt() != null && resolved.isAfter(t.getResolutionDueAt()));
                        if (roll >= 8 && resolved.isBefore(now.minus(Duration.ofDays(2)))) {
                            t.setStatus("CLOSED");
                            t.setClosedAt(resolved.plus(Duration.ofDays(1)));
                        }
                    }
                }
            }
            if (t.isOpen() && t.getResolutionDueAt() != null && t.getResolutionDueAt().isBefore(now) && t.getSlaPausedAt() == null) t.setResolutionBreached(true);
            if (t.isOpen() && t.getFirstResponseAt() == null && t.getResponseDueAt() != null && t.getResponseDueAt().isBefore(now)) t.setResponseBreached(true);
            t.setUpdatedAt(t.getResolvedAt() != null ? t.getResolvedAt() : created.plus(Duration.ofMinutes(20)));
            tickets.saveAndFlush(t);
            jdbc.update("update tickets set created_at = ? where id = ?", Timestamp.from(created), t.getId());
            jdbc.update("update ticket_history set created_at = ? where ticket_id = ? and action = 'CREATED'", Timestamp.from(created), t.getId());
        }

        // ---- attendance: global settings with demo thresholds and 14 days of history
        AttendanceSettings g = attSettings.findGlobal().orElseThrow();
        g.setReportingTimezone("Asia/Kolkata");
        g.setMaxBreakMinutes(30);
        g.setMaxDailyBreakMinutes(75);
        g.setMaxBreaksPerDay(4);
        g.setBreakReminderIntervalMinutes(15);
        g.setDefaultWorkHours(new java.math.BigDecimal("8.00"));
        g.setNotifyTeamLeaderOnIdle(true);
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        List<User> staff = new ArrayList<>(engineers);
        staff.add(tl1);
        staff.add(tl2);
        staff.add(lead);
        LocalDate today = LocalDate.now(zone);
        for (User u : staff) {
            UUID projectId = members.findByUserIdOrderByPrimaryDescAssignedAtAsc(u.getId()).stream().findFirst().map(ProjectMember::getProjectId).orElse(null);
            for (int d = 14; d >= 1; d--) {
                LocalDate date = today.minusDays(d);
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                if (random.nextInt(20) == 0) continue; // occasional absence
                LocalTime start = (u.getShift() != null ? u.getShift().getStartTime() : LocalTime.of(9, 0)).plusMinutes(random.nextInt(35) - 10);
                Instant login = date.atTime(start).atZone(zone).toInstant();
                boolean missing = random.nextInt(25) == 0;
                Instant logout = login.plus(Duration.ofMinutes(8 * 60 + random.nextInt(90) - 30));
                UserSession us = new UserSession();
                us.setUserId(u.getId());
                us.setStatus(missing ? "EXPIRED" : "LOGGED_OUT");
                us.setIpAddress("10.20." + (random.nextInt(4) + 1) + "." + (random.nextInt(200) + 10));
                us.setLastIp(us.getIpAddress());
                us.setIpVersion((short) 4);
                us.setDevice("Windows (desktop)");
                us.setBrowser(random.nextBoolean() ? "Chrome" : "Edge");
                us.setCreatedAt(login);
                us.setLastSeenAt(logout);
                us.setExpiresAt(login.plus(Duration.ofHours(14)));
                us.setEndedAt(logout);
                userSessions.saveAndFlush(us);
                AttendanceSession as = new AttendanceSession();
                as.setUserId(u.getId());
                as.setAuthSessionId(us.getId());
                as.setProjectId(projectId);
                as.setWorkDate(date);
                as.setLoginAt(login);
                as.setLogoutAt(missing ? logout.minus(Duration.ofHours(2)) : logout);
                as.setLogoutSource(missing ? "HEARTBEAT_TIMEOUT" : "USER_LOGOUT");
                as.setStatus(missing ? "MISSING_LOGOUT" : "LOGGED_OUT");
                as.setCurrentState("OFFLINE");
                as.setLoginIp(us.getIpAddress());
                as.setDevice(us.getDevice());
                as.setBrowser(us.getBrowser());
                as.setLastHeartbeatAt(as.getLogoutAt());
                as.setLastActivityAt(as.getLogoutAt());
                as.setDurationSeconds(Duration.between(login, as.getLogoutAt()).toSeconds());
                attSessions.save(as);
                int nBreaks = 1 + random.nextInt(4);
                Instant cursor = login.plus(Duration.ofMinutes(90));
                for (int b = 0; b < nBreaks; b++) {
                    int mins = b == 1 ? 25 + random.nextInt(20) : 5 + random.nextInt(18);
                    Instant bs = cursor.plus(Duration.ofMinutes(random.nextInt(60)));
                    Instant be = bs.plus(Duration.ofMinutes(mins));
                    if (be.isAfter(as.getLogoutAt())) break;
                    BreakRecord br = new BreakRecord();
                    br.setUserId(u.getId());
                    br.setSessionId(as.getId());
                    br.setProjectId(projectId);
                    br.setBreakCategory(b == 1 ? "MEAL" : "GENERAL");
                    br.setWorkDate(date);
                    br.setStartedAt(bs);
                    br.setEndedAt(be);
                    br.setDurationSeconds((long) mins * 60);
                    br.setStatus("COMPLETED");
                    br.setStartSource("EMPLOYEE");
                    br.setEndSource("EMPLOYEE");
                    br.setExceeded(mins > 30);
                    breaks.save(br);
                    cursor = be.plus(Duration.ofMinutes(60 + random.nextInt(60)));
                }
                if (random.nextInt(4) == 0) {
                    IdleEvent ie = new IdleEvent();
                    ie.setUserId(u.getId());
                    ie.setSessionId(as.getId());
                    ie.setWorkDate(date);
                    Instant is = login.plus(Duration.ofMinutes(200 + random.nextInt(120)));
                    ie.setIdleStartedAt(is);
                    ie.setDetectedAt(is.plus(Duration.ofMinutes(20)));
                    ie.setEndedAt(is.plus(Duration.ofMinutes(22 + random.nextInt(15))));
                    ie.setDurationSeconds(Duration.between(is, ie.getEndedAt()).toSeconds());
                    boolean answered = random.nextBoolean();
                    ie.setStatus(answered ? "CONFIRMED" : "AUTO_RESOLVED");
                    ie.setResponse(answered ? (random.nextBoolean() ? "WORKING" : "AWAY") : null);
                    ie.setRespondedAt(answered ? ie.getEndedAt() : null);
                    idles.save(ie);
                }
                jdbc.update("insert into login_activity_logs (id, user_id, username_attempted, success, ip_address, ip_version, session_id, device, browser, created_at) values (?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), u.getId(), u.getUsername(), true, us.getIpAddress(), 4, us.getId(), us.getDevice(), us.getBrowser(), Timestamp.from(login));
                jdbc.update("insert into ip_activity_logs (id, ip_address, ip_version, user_id, activity_type, details, created_at) values (?,?,?,?,?,?,?)",
                        UUID.randomUUID(), us.getIpAddress(), 4, u.getId(), "LOGIN_SUCCESS", "demo history", Timestamp.from(login));
            }
        }
        // a few failed / unknown attempts for the security dashboard
        String[] ips = {"203.0.113.24", "198.51.100.7", "2001:db8::7f", "192.0.2.150"};
        for (int i = 0; i < 18; i++) {
            String ip = ips[i % ips.length];
            boolean unknown = i % 3 == 0;
            Instant at = now.minus(Duration.ofMinutes(random.nextInt(60 * 20)));
            jdbc.update("insert into login_activity_logs (id, username_attempted, user_id, success, failure_category, ip_address, ip_version, browser, device, attempt_count, suspicious, suspicious_reasons, created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), unknown ? "administrator" : engineers.get(i % engineers.size()).getUsername(),
                    unknown ? null : engineers.get(i % engineers.size()).getId(), false, unknown ? "UNKNOWN_USER" : "INVALID_CREDENTIALS",
                    ip, ip.contains(":") ? 6 : 4, "curl", "Other (desktop)", 1 + i / 4, i > 12, i > 12 ? "REPEATED_FAILURES_FROM_IP" : null, Timestamp.from(at));
            jdbc.update("insert into ip_activity_logs (id, ip_address, ip_version, activity_type, details, created_at) values (?,?,?,?,?,?)",
                    UUID.randomUUID(), ip, ip.contains(":") ? 6 : 4, unknown ? "UNKNOWN_USER" : "LOGIN_FAILURE", "demo", Timestamp.from(at));
        }
        for (int i = 0; i < 9; i++) {
            jdbc.update("insert into anonymous_access_logs (id, ip_address, ip_version, http_method, endpoint, category, status_code, user_agent, created_at) values (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), ips[i % ips.length], ips[i % ips.length].contains(":") ? 6 : 4, i % 2 == 0 ? "GET" : "POST",
                    i % 3 == 0 ? "/api/v1/users" : i % 3 == 1 ? "/api/v1/security/active-sessions" : "/api/v1/tickets",
                    i % 4 == 0 ? "INVALID_TOKEN" : "MISSING_AUTH", 401, "python-requests/2.31", Timestamp.from(now.minus(Duration.ofMinutes(30L * i + 7))));
        }

        // ---- knowledge base
        KbCategory sop = kbCat("Standard Operating Procedures");
        KbCategory trouble = kbCat("Troubleshooting");
        KbCategory faq = kbCat("FAQ");
        article("Password reset SOP", "SOP", sop, null, manager, "access,password,reset",
                "## Purpose\nReset a user's ERP password safely.\n\n## Steps\n1. Verify the requester's identity using the client's approved channel.\n2. Open the ERP admin console and locate the user.\n3. Trigger a password reset; never send passwords over chat.\n4. Record the ticket number in the reset note.\n5. Confirm with the user and resolve the ticket.");
        article("VPN disconnects: troubleshooting guide", "TROUBLESHOOTING", trouble, p2, tl2, "vpn,network",
                "## Symptoms\nUsers are disconnected every few minutes.\n\n## Checks\n- Confirm client version >= 5.2\n- Check MTU setting (1400 recommended)\n- Review firewall idle timeout\n\n## Resolution\nApply the standard VPN profile and ask the user to reconnect.");
        article("How are SLA targets calculated?", "FAQ", faq, null, manager, "sla,faq",
                "SLA timers run on the project's business calendar. Time spent in PENDING is excluded when the policy pauses on pending. Response SLA stops at the first response; resolution SLA stops when the ticket is resolved.");
        article("Report export timeout fix", "RESOLUTION", trouble, p1, lead, "report,export,timeout",
                "Large exports should be scheduled for off-peak hours. Increase the report service timeout to 120 seconds and enable pagination for exports above 50,000 rows.");
    }

    private Shift shift(String name, LocalTime s, LocalTime e) {
        Shift x = new Shift();
        x.setName(name);
        x.setStartTime(s);
        x.setEndTime(e);
        x.setTimezone("Asia/Kolkata");
        return shifts.save(x);
    }

    private User user(String code, String username, String name, String role, String title, String hash, Shift shift) {
        User u = new User();
        u.setEmployeeCode(code);
        u.setUsername(username);
        u.setEmail(username + "@demo.suma.local");
        u.setFullName(name);
        u.setJobTitle(title);
        u.setRole(roles.findByCode(role).orElseThrow());
        u.setPasswordHash(hash);
        u.setShift(shift);
        u.setTimezone("Asia/Kolkata");
        u.getWeeklyOffs().add((short) 7);
        users.saveAndFlush(u);
        jdbc.update("update users set created_at = now() - interval '120 days' where id = ?", u.getId());
        return u;
    }

    private Client client(String code, String name, String industry, User am) {
        Client c = new Client();
        c.setCode(code);
        c.setName(name);
        c.setIndustry(industry);
        c.setEmail("support@" + code.toLowerCase() + ".demo.suma.local");
        c.setAccountManagerId(am.getId());
        return clients.save(c);
    }

    private Project project(Client c, String code, String name, User manager, User tl, User lead, BusinessCalendar cal) {
        Project p = new Project();
        p.setClientId(c.getId());
        p.setCode(code);
        p.setName(name);
        p.setDescription("Demo project for " + c.getName());
        p.setManagerId(manager.getId());
        p.setTeamLeaderId(tl.getId());
        if (lead != null) {
            p.setProjectLeadId(lead.getId());
            p.setProjectLeadEmail(lead.getEmail());
            p.setProjectLeadEmailVerified(true);
        }
        p.setCalendarId(cal == null ? null : cal.getId());
        p.setSupportHours("Mon-Sat 09:00-18:00 IST");
        p.setTimezone("Asia/Kolkata");
        p.setStartDate(LocalDate.now().minusMonths(6));
        return projects.save(p);
    }

    private void member(Project p, User u, String designation, boolean primary) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(p.getId());
        m.setUserId(u.getId());
        m.setDesignation(designation);
        m.setPrimary(primary);
        members.save(m);
    }

    private Team team(String name, Project p, User leader, List<User> ms) {
        Team t = new Team();
        t.setName(name);
        t.setProjectId(p.getId());
        t.setTeamLeaderId(leader.getId());
        ms.forEach(m -> t.getMemberIds().add(m.getId()));
        return teams.save(t);
    }

    private TicketCategory category(Project p, UUID parent, String name, String keywords, UUID team) {
        TicketCategory c = new TicketCategory();
        c.setProjectId(p.getId());
        c.setParentId(parent);
        c.setName(name);
        c.setKeywords(keywords);
        c.setDefaultTeamId(team);
        return categories.save(c);
    }

    private KbCategory kbCat(String name) {
        KbCategory c = new KbCategory();
        c.setName(name);
        return kbCategories.save(c);
    }

    private void article(String title, String type, KbCategory cat, Project p, User author, String tags, String content) {
        KbArticle a = new KbArticle();
        a.setTitle(title);
        a.setSlug(title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", ""));
        a.setArticleType(type);
        a.setCategoryId(cat.getId());
        a.setProjectId(p == null ? null : p.getId());
        a.setTags(tags);
        a.setStatus("PUBLISHED");
        a.setAuthorId(author.getId());
        a.setSummary(content.length() > 140 ? content.substring(0, 140).replace("#", "").trim() + "..." : content);
        a.setContent(content);
        a.setViewCount(random.nextInt(80));
        a.setHelpfulCount(random.nextInt(20));
        articles.save(a);
    }
}
