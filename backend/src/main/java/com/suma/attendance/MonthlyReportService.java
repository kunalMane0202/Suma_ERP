package com.suma.attendance;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.notification.EmailDeliveryService;
import com.suma.notification.NotificationService;
import com.suma.project.Project;
import com.suma.project.ProjectRepository;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;

/**
 * Monthly consolidated attendance report per project, emailed to the project's verified Project Lead address.
 * Idempotency: a unique (project, MONTHLY, year, month) row in attendance_report_logs is claimed before anything is
 * generated, so a period is never generated or sent twice by the scheduler (even across restarts or multiple nodes).
 */
@Service
public class MonthlyReportService {
    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);
    public static final String RELATED_TYPE = "ATTENDANCE_REPORT";
    private final AttendanceReportLogRepository logs;
    private final AttendanceReportService reports;
    private final AttendanceExportService exporter;
    private final AttendanceSettingsService settings;
    private final ProjectRepository projects;
    private final NotificationService notifications;
    private final AccessScopeService scope;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final TransactionTemplate newTx;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MonthlyReportService(AttendanceReportLogRepository logs, AttendanceReportService reports, AttendanceExportService exporter,
                                AttendanceSettingsService settings, ProjectRepository projects, NotificationService notifications,
                                AccessScopeService scope, UserRepository users, AuditService audit, TransactionTemplate tx,
                                JdbcTemplate jdbc, Clock clock) {
        this.logs = logs;
        this.reports = reports;
        this.exporter = exporter;
        this.settings = settings;
        this.projects = projects;
        this.notifications = notifications;
        this.scope = scope;
        this.users = users;
        this.audit = audit;
        this.tx = tx;
        this.newTx = new TransactionTemplate(tx.getTransactionManager());
        this.newTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Previous calendar month relative to {@code now} in the reporting timezone, or empty if the run time has not arrived. */
    public Optional<YearMonth> duePeriod(Instant now) {
        AttendanceSettings cfg = settings.global();
        if (!cfg.isMonthlyReportEnabled()) return Optional.empty();
        ZonedDateTime local = now.atZone(ZoneId.of(cfg.getReportingTimezone()));
        boolean reached = local.getDayOfMonth() > 1 || local.getHour() >= cfg.getMonthlyReportHour();
        return reached ? Optional.of(YearMonth.from(local).minusMonths(1)) : Optional.empty();
    }

    /** Scheduler entry point. Safe to call repeatedly: projects already claimed for the period are skipped. */
    public int runScheduledCycle() {
        Optional<YearMonth> period = duePeriod(Instant.now(clock));
        if (period.isEmpty()) return 0;
        YearMonth ym = period.get();
        int processed = 0;
        List<UUID> projectIds = jdbc.queryForList("""
                select p.id from projects p where p.status = 'ACTIVE'
                and exists (select 1 from project_members pm where pm.project_id = p.id)
                and not exists (select 1 from attendance_report_logs l where l.project_id = p.id and l.report_type = 'MONTHLY'
                                and l.period_year = ? and l.period_month = ? and l.period_day = 0)
                """, UUID.class, ym.getYear(), ym.getMonthValue());
        for (UUID projectId : projectIds) {
            Optional<UUID> claimed = claim(projectId, ym, "SCHEDULER");
            if (claimed.isPresent()) {
                tx.executeWithoutResult(s -> generateAndQueue(claimed.get()));
                processed++;
            }
        }
        if (processed > 0) log.info("Monthly attendance reports for {} queued for {} project(s)", ym, processed);
        return processed;
    }

    /** Inserts the idempotency row in its own transaction. Empty if this period was already claimed. */
    Optional<UUID> claim(UUID projectId, YearMonth ym, String trigger) {
        try {
            return Optional.ofNullable(newTx.execute(s -> {
                AttendanceReportLog l = new AttendanceReportLog();
                l.setProjectId(projectId);
                l.setReportType("MONTHLY");
                l.setPeriodYear(ym.getYear());
                l.setPeriodMonth(ym.getMonthValue());
                l.setPeriodDay(0);
                l.setStatus("PENDING");
                l.setTriggeredBy(trigger);
                logs.saveAndFlush(l);
                return l.getId();
            }));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    /** Generates the Excel report and places it in the email outbox, or records why it was not sent. */
    void generateAndQueue(UUID logId) {
        AttendanceReportLog l = logs.findById(logId).orElseThrow();
        Project p = projects.findById(l.getProjectId()).orElseThrow();
        Instant now = Instant.now(clock);
        l.setRecipientEmail(p.getProjectLeadEmail());
        if (p.getProjectLeadEmail() == null) {
            l.setStatus("SKIPPED_NO_RECIPIENT");
            l.setLastError("No Project Lead email configured for project " + p.getCode());
            notifyAdmins("Monthly attendance report not sent", "Project " + p.getCode() + " has no Project Lead email configured.", l.getId());
            return;
        }
        if (!p.isProjectLeadEmailVerified()) {
            l.setStatus("SKIPPED_UNVERIFIED");
            l.setLastError("Project Lead email " + p.getProjectLeadEmail() + " is not verified");
            notifyAdmins("Monthly attendance report not sent", "Project Lead email for " + p.getCode() + " is not verified.", l.getId());
            return;
        }
        var report = reports.monthly(null, l.getPeriodYear(), l.getPeriodMonth(),
                new AttendanceReportService.Filter(p.getId(), null, null), p.getName());
        var export = exporter.monthly(report, "xlsx", p.getCode().toLowerCase());
        String leadName = p.getProjectLeadId() == null ? "Project Lead" : users.findById(p.getProjectLeadId()).map(u -> u.getFullName()).orElse("Project Lead");
        String period = YearMonth.of(l.getPeriodYear(), l.getPeriodMonth()).toString();
        String[] mail = notifications.render("MONTHLY_ATTENDANCE", Map.of("name", leadName, "project", p.getName(), "period", period,
                        "employeeCount", String.valueOf(report.employees().size())),
                "Monthly attendance report - " + p.getName() + " - " + period, "Attached is the monthly attendance report.");
        int maxAttempts = settings.global().getEmailMaxAttempts();
        notifications.enqueueEmail(p.getProjectLeadEmail(), mail[0], mail[1], export.fileName(), export.contentType(), export.content(),
                RELATED_TYPE, l.getId().toString(), maxAttempts);
        l.setStatus("GENERATED");
        l.setGeneratedAt(now);
        l.setEmployeeCount(report.employees().size());
        l.setFileName(export.fileName());
    }

    /** Keeps the report log in sync with email delivery attempts (sent / retrying / failed). */
    @EventListener
    public void onEmailAttempt(EmailDeliveryService.EmailAttemptEvent e) {
        if (!RELATED_TYPE.equals(e.relatedType()) || e.relatedId() == null) return;
        logs.findById(UUID.fromString(e.relatedId())).ifPresent(l -> {
            l.setAttempts(e.attempts());
            l.setLastAttemptAt(Instant.now(clock));
            switch (e.status()) {
                case "SENT" -> {
                    l.setStatus("SENT");
                    l.setSentAt(e.sentAt());
                    l.setLastError(null);
                    l.setNextRetryAt(null);
                    Project p = projects.findById(l.getProjectId()).orElse(null);
                    if (p != null && p.getProjectLeadId() != null) {
                        notifications.notify(p.getProjectLeadId(), "MONTHLY_REPORT_DELIVERED", "Monthly attendance report delivered",
                                "The " + l.getPeriodYear() + "-" + String.format("%02d", l.getPeriodMonth()) + " report for " + p.getName()
                                        + " was emailed to " + l.getRecipientEmail(), "INFO", "AttendanceReportLog", l.getId());
                    }
                }
                case "RETRYING" -> {
                    l.setStatus("RETRYING");
                    l.setLastError(e.error());
                    l.setNextRetryAt(e.nextAttemptAt());
                }
                case "FAILED" -> {
                    l.setStatus("FAILED");
                    l.setLastError(e.error());
                    l.setNextRetryAt(null);
                    notifyAdmins("Monthly attendance report delivery failed",
                            "Delivery to " + l.getRecipientEmail() + " failed after " + e.attempts() + " attempts: " + e.error(), l.getId());
                }
                default -> { }
            }
            logs.save(l);
        });
    }

    // ------------------------------------------------------------------ manual operations

    @Transactional
    public AttendanceReportLog generateManual(AuthUser caller, UUID projectId, int year, int month) {
        requireProject(caller, projectId);
        YearMonth ym = YearMonth.of(year, month);
        ZoneId zone = ZoneId.of(settings.global().getReportingTimezone());
        if (!ym.isBefore(YearMonth.now(clock.withZone(zone)))) throw ApiException.badRequest("Only completed months can be sent as monthly reports");
        Optional<UUID> claimed = claim(projectId, ym, "MANUAL");
        if (claimed.isEmpty()) throw ApiException.conflict("A monthly report for this project and period already exists. Use resend if required.");
        generateAndQueue(claimed.get());
        AttendanceReportLog l = logs.findById(claimed.get()).orElseThrow();
        audit.log("MONTHLY_REPORT_GENERATED", "AttendanceReportLog", l.getId(), projectId + " " + ym + " status=" + l.getStatus());
        return l;
    }

    /** Explicit resend. A report that was already SENT is only re-sent with force=true (audited). */
    @Transactional
    public AttendanceReportLog resend(AuthUser caller, UUID logId, boolean force) {
        AttendanceReportLog l = logs.findById(logId).orElseThrow(() -> ApiException.notFound("Report"));
        requireProject(caller, l.getProjectId());
        if ("SENT".equals(l.getStatus()) && !force) throw ApiException.conflict("This report was already delivered. Pass force=true to send it again.");
        if (("GENERATED".equals(l.getStatus()) || "RETRYING".equals(l.getStatus())) && !force)
            throw ApiException.conflict("Delivery of this report is already in progress.");
        l.setTriggeredBy("MANUAL");
        l.setStatus("PENDING");
        l.setLastError(null);
        l.setAttempts(0);
        generateAndQueue(l.getId());
        audit.log("MONTHLY_REPORT_RESENT", "AttendanceReportLog", logId, "force=" + force + " status=" + l.getStatus());
        return l;
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportLog> list(AuthUser caller) {
        Set<UUID> projects = scope.attendanceVisibleProjectIds(caller);
        return logs.recent(projects != null, projects == null || projects.isEmpty() ? Set.of(new UUID(0, 0)) : projects);
    }

    @Transactional(readOnly = true)
    public AttendanceReportLog get(AuthUser caller, UUID id) {
        AttendanceReportLog l = logs.findById(id).orElseThrow(() -> ApiException.notFound("Report"));
        requireProject(caller, l.getProjectId());
        return l;
    }

    @Transactional(readOnly = true)
    public AttendanceExportService.Export download(AuthUser caller, UUID id, String format) {
        AttendanceReportLog l = get(caller, id);
        Project p = projects.findById(l.getProjectId()).orElseThrow();
        var report = reports.monthly(null, l.getPeriodYear(), l.getPeriodMonth(), new AttendanceReportService.Filter(p.getId(), null, null), p.getName());
        return exporter.monthly(report, format, p.getCode().toLowerCase());
    }

    private void requireProject(AuthUser caller, UUID projectId) {
        Set<UUID> visible = scope.attendanceVisibleProjectIds(caller);
        if (visible != null && !visible.contains(projectId)) throw ApiException.forbidden("You cannot access reports for this project");
    }

    private void notifyAdmins(String title, String message, UUID logId) {
        List<UUID> admins = jdbc.queryForList("select u.id from users u join roles r on r.id = u.role_id where r.code = 'ADMIN' and u.status = 'ACTIVE'", UUID.class);
        notifications.notifyAll(admins, "MONTHLY_REPORT_FAILED", title, message, "WARNING", "AttendanceReportLog", logId);
    }
}
