package com.suma.attendance;

import com.suma.auth.AuthService;
import com.suma.auth.UserSession;
import com.suma.auth.UserSessionRepository;
import com.suma.notification.NotificationService;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;

/** Background attendance processing. Each unit of work runs in its own transaction so one failure cannot block others. */
@Service
public class AttendanceMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceMaintenanceService.class);
    private final AttendanceSessionRepository sessions;
    private final BreakRecordRepository breaks;
    private final BreakReminderRepository reminders;
    private final IdleEventRepository idles;
    private final AttendanceDailySummaryRepository dailySummaries;
    private final AttendanceMonthlySummaryRepository monthlySummaries;
    private final AttendanceEventRepository events;
    private final AttendanceSettingsService settings;
    private final AttendanceService attendance;
    private final AttendanceReportService reports;
    private final UserSessionRepository authSessions;
    private final AuthService auth;
    private final NotificationService notifications;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AttendanceMaintenanceService(AttendanceSessionRepository sessions, BreakRecordRepository breaks, BreakReminderRepository reminders,
                                        IdleEventRepository idles, AttendanceDailySummaryRepository dailySummaries,
                                        AttendanceMonthlySummaryRepository monthlySummaries, AttendanceEventRepository events,
                                        AttendanceSettingsService settings, AttendanceService attendance, AttendanceReportService reports,
                                        UserSessionRepository authSessions, AuthService auth, NotificationService notifications,
                                        UserRepository users, TransactionTemplate tx, Clock clock) {
        this.sessions = sessions;
        this.breaks = breaks;
        this.reminders = reminders;
        this.idles = idles;
        this.dailySummaries = dailySummaries;
        this.monthlySummaries = monthlySummaries;
        this.events = events;
        this.settings = settings;
        this.attendance = attendance;
        this.reports = reports;
        this.authSessions = authSessions;
        this.auth = auth;
        this.notifications = notifications;
        this.users = users;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Reconciles incomplete sessions:
     * 1) auth sessions past their expiry → SESSION_EXPIRY;
     * 2) attendance sessions without a heartbeat for the configured timeout (browser closed / disconnected) → MISSING_LOGOUT
     *    closed at the last heartbeat, never at "now", so absent time is not counted as worked;
     * 3) sessions open longer than the configured maximum → MISSING_LOGOUT.
     */
    public int reconcileSessions() {
        Instant now = Instant.now(clock);
        int n = 0;
        for (UserSession s : authSessions.findByStatusAndExpiresAtBefore("ACTIVE", now)) {
            tx.executeWithoutResult(t -> authSessions.findById(s.getId()).filter(UserSession::isActive).ifPresent(x -> {
                Instant end = sessions.findByAuthSessionId(x.getId()).map(a -> a.getLastHeartbeatAt() != null && a.getLastHeartbeatAt().isBefore(x.getExpiresAt())
                        ? a.getLastHeartbeatAt() : x.getExpiresAt()).orElse(x.getExpiresAt());
                auth.endSession(x, "EXPIRED", "Session expired", null, "SESSION_EXPIRY", end);
            }));
            n++;
        }
        for (AttendanceSession a : sessions.findByStatus("ACTIVE")) {
            AttendanceSettings cfg = settings.effective(a.getProjectId());
            Instant lastSeen = a.getLastHeartbeatAt() != null ? a.getLastHeartbeatAt() : a.getLoginAt();
            boolean stale = lastSeen.isBefore(now.minus(Duration.ofMinutes(cfg.getHeartbeatTimeoutMinutes())));
            boolean tooLong = a.getLoginAt().isBefore(now.minus(Duration.ofHours(cfg.getMissingLogoutAfterHours())));
            if (!stale && !tooLong) continue;
            String reason = stale ? "No heartbeat since " + lastSeen + " (browser closed or disconnected)" : "Session exceeded maximum duration";
            String source = stale ? "HEARTBEAT_TIMEOUT" : "RECONCILIATION";
            Instant end = stale ? lastSeen : now;
            tx.executeWithoutResult(t -> {
                AttendanceSession fresh = sessions.findById(a.getId()).orElse(null);
                if (fresh == null || !fresh.isActive()) return;
                UserSession us = authSessions.findById(fresh.getAuthSessionId()).orElse(null);
                if (us != null && us.isActive()) {
                    auth.endSession(us, "EXPIRED", reason, null, source, end);
                } else {
                    attendance.close(fresh, source, end);
                }
                ZoneId zone = settings.zone(fresh.getProjectId());
                notifications.notify(fresh.getUserId(), "MISSING_LOGOUT", "Missing logout recorded",
                        "Your session from " + fresh.getWorkDate() + " ended without a logout and was closed at "
                                + end.atZone(zone).toLocalTime().withNano(0) + " (" + zone.getId() + "). Contact your team leader if this is incorrect.",
                        "WARNING", "AttendanceSession", fresh.getId());
            });
            n++;
        }
        return n;
    }

    /**
     * Server-side fallback for idle detection: the browser keeps sending heartbeats with its last activity time; if the
     * last activity is older than the threshold and the browser did not report idleness itself, an idle event is recorded.
     */
    public int detectIdleFromHeartbeats() {
        Instant now = Instant.now(clock);
        int n = 0;
        for (AttendanceSession a : sessions.findByStatus("ACTIVE")) {
            if (!"WORKING".equals(a.getCurrentState())) continue;
            AttendanceSettings cfg = settings.effective(a.getProjectId());
            Instant threshold = now.minus(Duration.ofMinutes(cfg.getIdleThresholdMinutes()));
            Instant lastActivity = a.getLastActivityAt() != null ? a.getLastActivityAt() : a.getLoginAt();
            boolean heartbeatFresh = a.getLastHeartbeatAt() != null && a.getLastHeartbeatAt().isAfter(now.minus(Duration.ofMinutes(3)));
            if (!heartbeatFresh || !lastActivity.isBefore(threshold)) continue;
            Boolean created = tx.execute(t -> {
                AttendanceSession fresh = sessions.findById(a.getId()).orElse(null);
                if (fresh == null || !fresh.isActive() || idles.findFirstBySessionIdAndEndedAtIsNull(fresh.getId()).isPresent()) return false;
                try {
                    attendance.createIdle(fresh, lastActivity, now, "SERVER_HEARTBEAT");
                    return true;
                } catch (DataIntegrityViolationException e) {
                    t.setRollbackOnly();
                    return false;
                }
            });
            if (Boolean.TRUE.equals(created)) n++;
        }
        return n;
    }

    /** Break reminders, exceeded-limit alerts and follow-ups. Uses unique (break, type, sequence) to avoid duplicates. */
    public int processBreakReminders() {
        Instant now = Instant.now(clock);
        int n = 0;
        for (BreakRecord b : breaks.findByStatus("ACTIVE")) {
            long elapsedMin = Duration.between(b.getStartedAt(), now).toMinutes();
            Integer interval = settings.reminderMinutes(b.getProjectId(), b.getBreakCategory());
            Integer max = settings.maxBreakMinutes(b.getProjectId(), b.getBreakCategory());
            if (interval != null && elapsedMin >= interval) {
                int seq = (int) (elapsedMin / interval);
                if (send(b, "REMINDER", seq, now, "Break reminder",
                        "You have been on break for " + elapsedMin + " minutes. Remember to end your break when you are back.", "INFO", false)) n++;
            }
            if (max != null && elapsedMin >= max) {
                if (send(b, "EXCEEDED", 1, now, "Break limit exceeded",
                        "Your break has exceeded the configured limit of " + max + " minutes (" + elapsedMin + " minutes so far).", "WARNING", true)) n++;
                if (interval != null && elapsedMin >= max + interval) {
                    int seq = (int) ((elapsedMin - max) / interval);
                    if (send(b, "FOLLOW_UP", seq, now, "Break follow-up",
                            "Your break is still in progress after " + elapsedMin + " minutes.", "WARNING", true)) n++;
                }
            }
        }
        return n;
    }

    private boolean send(BreakRecord b, String type, int seq, Instant now, String title, String message, String severity, boolean leaders) {
        try {
            Boolean r = tx.execute(t -> {
                if (reminders.countByBreakIdAndReminderType(b.getId(), type) >= seq) return false;
                BreakReminder rm = new BreakReminder();
                rm.setBreakId(b.getId());
                rm.setUserId(b.getUserId());
                rm.setReminderType(type);
                rm.setSequenceNo(seq);
                rm.setSentAt(now);
                reminders.saveAndFlush(rm);
                if ("EXCEEDED".equals(type)) breaks.findById(b.getId()).ifPresent(x -> x.setExceeded(true));
                notifications.notify(b.getUserId(), "BREAK_" + type, title, message, severity, "BreakRecord", b.getId());
                if (leaders) {
                    String name = users.findById(b.getUserId()).map(User::getFullName).orElse("An employee");
                    notifications.notifyAll(attendance.teamLeadersOf(b.getUserId()), "TEAM_BREAK_" + type, title + ": " + name,
                            name + " — " + message, severity, "BreakRecord", b.getId());
                }
                attendance.event(b.getUserId(), b.getSessionId(), "REMINDER_SENT", now, "SYSTEM", type + " #" + seq, null);
                return true;
            });
            return Boolean.TRUE.equals(r);
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /**
     * Persists daily summaries for completed days (default: yesterday) and notifies employees and team leaders of
     * attendance exceptions the first time a day is summarised.
     */
    public int summariseDay(LocalDate date) {
        var report = reports.daily(null, date, new AttendanceReportService.Filter(null, null, null));
        int n = 0;
        for (var row : report.rows()) {
            var d = row.day();
            if (!d.present() && d.exceptions().isEmpty()) continue;
            Boolean created = tx.execute(t -> {
                var existing = dailySummaries.findByUserIdAndWorkDate(row.employee().userId(), date);
                AttendanceDailySummary s = existing.orElseGet(AttendanceDailySummary::new);
                s.setUserId(row.employee().userId());
                s.setWorkDate(date);
                s.setProjectId(row.employee().projectId());
                s.setTeamId(row.employee().teamId());
                s.setFirstLoginAt(d.firstLoginAt());
                s.setLastLogoutAt(d.lastLogoutAt());
                s.setSessionCount(d.sessions().size());
                s.setSessionSeconds(d.sessionSeconds());
                s.setBreakCount(d.breakCount());
                s.setBreakSeconds(d.breakSeconds());
                s.setIdleCount(d.idleCount());
                s.setIdleSeconds(d.idleSeconds());
                s.setExceededBreakCount(d.exceededBreakCount());
                s.setMissingLogout(d.missingLogout());
                s.setExceptions(d.exceptions().isEmpty() ? null : String.join(",", d.exceptions()));
                s.setComputedAt(Instant.now(clock));
                dailySummaries.save(s);
                return existing.isEmpty();
            });
            if (Boolean.TRUE.equals(created) && !d.exceptions().isEmpty()
                    && !(d.exceptions().size() == 1 && d.exceptions().contains("NO_LOGIN_RECORD"))) {
                tx.executeWithoutResult(t -> {
                    String list = String.join(", ", d.exceptions());
                    notifications.notify(row.employee().userId(), "ATTENDANCE_EXCEPTION", "Attendance exceptions for " + date, list,
                            "WARNING", "AttendanceDailySummary", date);
                    notifications.notifyAll(attendance.teamLeadersOf(row.employee().userId()), "TEAM_ATTENDANCE_EXCEPTION",
                            "Attendance exceptions: " + row.employee().fullName(), date + ": " + list, "INFO", "AttendanceDailySummary", date);
                });
            }
            n++;
        }
        return n;
    }

    public int summariseYesterday() {
        ZoneId zone = ZoneId.of(settings.global().getReportingTimezone());
        LocalDate yesterday = LocalDate.now(clock.withZone(zone)).minusDays(1);
        int n = summariseDay(yesterday);
        if (yesterday.getDayOfMonth() == yesterday.lengthOfMonth()) n += summariseMonth(YearMonth.from(yesterday));
        return n;
    }

    public int summariseMonth(YearMonth ym) {
        var report = reports.monthly(null, ym.getYear(), ym.getMonthValue(), new AttendanceReportService.Filter(null, null, null), null);
        for (var m : report.employees()) {
            tx.executeWithoutResult(t -> {
                AttendanceMonthlySummary s = monthlySummaries.findByUserIdAndPeriodYearAndPeriodMonth(m.employee().userId(), ym.getYear(), ym.getMonthValue())
                        .orElseGet(AttendanceMonthlySummary::new);
                s.setUserId(m.employee().userId());
                s.setProjectId(m.employee().projectId());
                s.setPeriodYear(ym.getYear());
                s.setPeriodMonth(ym.getMonthValue());
                s.setDaysPresent(m.daysPresent());
                s.setSessionSeconds(m.sessionSeconds());
                s.setBreakCount(m.breakCount());
                s.setBreakSeconds(m.breakSeconds());
                s.setIdleCount(m.idleCount());
                s.setIdleSeconds(m.idleSeconds());
                s.setExceptionCount(m.exceptionCount());
                s.setMissingLogoutCount(m.missingLogoutCount());
                s.setComputedAt(Instant.now(clock));
                monthlySummaries.save(s);
            });
        }
        return report.employees().size();
    }

    public int retentionCleanup() {
        int days = settings.global().getRetentionDays();
        Instant cutoff = Instant.now(clock).minus(Duration.ofDays(days));
        Integer n = tx.execute(t -> events.deleteOlderThan(cutoff));
        return n == null ? 0 : n;
    }

}
