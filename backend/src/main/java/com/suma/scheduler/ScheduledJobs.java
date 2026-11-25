package com.suma.scheduler;

import com.suma.attendance.AttendanceMaintenanceService;
import com.suma.attendance.AttendanceSettingsService;
import com.suma.attendance.MonthlyReportService;
import com.suma.notification.EmailDeliveryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * All recurring jobs. ShedLock guarantees a single concurrent execution per job across instances; each job is
 * idempotent and derives its work from database state, so missed runs (e.g. during a restart) are caught up next time.
 */
@Component
@ConditionalOnProperty(name = "suma.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledJobs {
    private final JobRunner runner;
    private final AttendanceMaintenanceService attendance;
    private final MonthlyReportService monthly;
    private final EmailDeliveryService email;
    private final AttendanceSettingsService settings;
    private final RetentionService retention;

    public ScheduledJobs(JobRunner runner, AttendanceMaintenanceService attendance, MonthlyReportService monthly,
                         EmailDeliveryService email, AttendanceSettingsService settings, RetentionService retention) {
        this.runner = runner;
        this.attendance = attendance;
        this.monthly = monthly;
        this.email = email;
        this.settings = settings;
        this.retention = retention;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    @SchedulerLock(name = "attendance-reconciliation", lockAtMostFor = "PT10M")
    public void reconcile() {
        runner.run("attendance-reconciliation", attendance::reconcileSessions);
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 90_000)
    @SchedulerLock(name = "idle-processing", lockAtMostFor = "PT5M")
    public void idle() {
        runner.run("idle-processing", attendance::detectIdleFromHeartbeats);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    @SchedulerLock(name = "break-reminders", lockAtMostFor = "PT5M")
    public void breakReminders() {
        runner.run("break-reminders", attendance::processBreakReminders);
    }

    @Scheduled(cron = "0 20 * * * *", zone = "UTC")
    @SchedulerLock(name = "daily-attendance-summary", lockAtMostFor = "PT30M")
    public void dailySummary() {
        runner.run("daily-attendance-summary", attendance::summariseYesterday);
    }

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    @SchedulerLock(name = "monthly-attendance-report", lockAtMostFor = "PT30M")
    public void monthlyReport() {
        runner.run("monthly-attendance-report", monthly::runScheduledCycle);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @SchedulerLock(name = "email-delivery", lockAtMostFor = "PT10M")
    public void emailDelivery() {
        email.setBaseBackoff(Duration.ofMinutes(settings.global().getEmailRetryBackoffMinutes()));
        runner.run("email-delivery", () -> email.processDue(25));
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @SchedulerLock(name = "retention-cleanup", lockAtMostFor = "PT1H")
    public void retentionCleanup() {
        runner.run("retention-cleanup", () -> attendance.retentionCleanup() + retention.cleanup());
    }
}
