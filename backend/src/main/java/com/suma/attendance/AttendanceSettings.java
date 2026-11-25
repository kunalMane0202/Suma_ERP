package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_settings")
@Getter
@Setter
public class AttendanceSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id", unique = true)
    private UUID projectId;
    @Column(name = "default_work_hours", precision = 4, scale = 2)
    private BigDecimal defaultWorkHours;
    @Column(name = "idle_threshold_minutes", nullable = false)
    private int idleThresholdMinutes = 20;
    @Column(name = "heartbeat_timeout_minutes", nullable = false)
    private int heartbeatTimeoutMinutes = 30;
    @Column(name = "missing_logout_after_hours", nullable = false)
    private int missingLogoutAfterHours = 16;
    @Column(name = "max_break_minutes")
    private Integer maxBreakMinutes;
    @Column(name = "max_daily_break_minutes")
    private Integer maxDailyBreakMinutes;
    @Column(name = "max_breaks_per_day")
    private Integer maxBreaksPerDay;
    @Column(name = "break_reminder_interval_minutes")
    private Integer breakReminderIntervalMinutes;
    @Column(name = "reporting_timezone", nullable = false, length = 60)
    private String reportingTimezone = "UTC";
    @Column(name = "notify_team_leader_on_idle", nullable = false)
    private boolean notifyTeamLeaderOnIdle;
    @Column(name = "monthly_report_enabled", nullable = false)
    private boolean monthlyReportEnabled = true;
    @Column(name = "monthly_report_hour", nullable = false)
    private int monthlyReportHour = 6;
    @Column(name = "email_max_attempts", nullable = false)
    private int emailMaxAttempts = 5;
    @Column(name = "email_retry_backoff_minutes", nullable = false)
    private int emailRetryBackoffMinutes = 15;
    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 730;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
