package com.suma.attendance;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {
    private AttendanceDtos() {}

    public record StartBreakRequest(@Size(max = 30) String category, @Size(max = 500) String notes) {}

    public record HeartbeatRequest(Instant lastActivityAt, Boolean visible) {}

    public record IdleReportRequest(@NotNull Instant idleStartedAt) {}

    public record IdleConfirmRequest(@NotNull UUID idleEventId, @NotBlank @Pattern(regexp = "WORKING|ON_BREAK|AWAY") String response,
                                     @Size(max = 30) String breakCategory) {}

    public record BreakDto(UUID id, UUID userId, String category, Instant startedAt, Instant endedAt, long durationSeconds,
                           String status, boolean exceeded, Integer limitMinutes, String startSource, String endSource,
                           String notes, LocalDate workDate) {}

    public record IdleDto(UUID id, Instant idleStartedAt, Instant detectedAt, Instant endedAt, Long durationSeconds,
                          String status, String response, Instant respondedAt, String source) {}

    public record SessionDto(UUID id, UUID authSessionId, Instant loginAt, Instant logoutAt, String logoutSource, String status,
                             String currentState, String loginIp, String device, String browser, long durationSeconds,
                             Instant lastHeartbeatAt, LocalDate workDate) {}

    public record TodayView(LocalDate workDate, String timezone, SessionDto currentSession, String currentState,
                            BreakDto currentBreak, Instant firstLoginAt, long sessionSeconds, int breakCount, long breakSeconds,
                            long averageBreakSeconds, int idleCount, long idleSeconds, List<BreakDto> breaks, List<IdleDto> idles,
                            List<SessionDto> sessions, List<String> exceptions, int idleThresholdMinutes,
                            Integer maxBreakMinutes, Integer maxDailyBreakMinutes, Integer maxBreaksPerDay,
                            List<CategoryDto> breakCategories, IdleDto openIdle) {}

    public record CategoryDto(String code, String label, boolean active) {}

    public record IdleStatus(int thresholdMinutes, String currentState, IdleDto openIdle, Instant lastActivityAt,
                             boolean systemWideMonitoring, String scopeNotice) {}

    public record EventDto(UUID id, UUID userId, String userName, UUID sessionId, String eventType, Instant occurredAt,
                           String source, String details, UUID createdBy) {}

    public record SettingsRequest(@DecimalMin("0.5") @DecimalMax("24") BigDecimal defaultWorkHours,
                                  @Min(1) @Max(480) int idleThresholdMinutes, @Min(2) @Max(1440) int heartbeatTimeoutMinutes,
                                  @Min(1) @Max(72) int missingLogoutAfterHours, @Min(1) @Max(600) Integer maxBreakMinutes,
                                  @Min(1) @Max(1440) Integer maxDailyBreakMinutes, @Min(1) @Max(100) Integer maxBreaksPerDay,
                                  @Min(1) @Max(600) Integer breakReminderIntervalMinutes, @NotBlank @Size(max = 60) String reportingTimezone,
                                  boolean notifyTeamLeaderOnIdle, boolean monthlyReportEnabled, @Min(0) @Max(23) int monthlyReportHour,
                                  @Min(1) @Max(20) int emailMaxAttempts, @Min(1) @Max(1440) int emailRetryBackoffMinutes,
                                  @Min(30) @Max(3650) int retentionDays) {}

    public record SettingsResponse(UUID id, UUID projectId, BigDecimal defaultWorkHours, int idleThresholdMinutes,
                                   int heartbeatTimeoutMinutes, int missingLogoutAfterHours, Integer maxBreakMinutes,
                                   Integer maxDailyBreakMinutes, Integer maxBreaksPerDay, Integer breakReminderIntervalMinutes,
                                   String reportingTimezone, boolean notifyTeamLeaderOnIdle, boolean monthlyReportEnabled,
                                   int monthlyReportHour, int emailMaxAttempts, int emailRetryBackoffMinutes, int retentionDays,
                                   Instant updatedAt) {
        public static SettingsResponse of(AttendanceSettings s) {
            return new SettingsResponse(s.getId(), s.getProjectId(), s.getDefaultWorkHours(), s.getIdleThresholdMinutes(),
                    s.getHeartbeatTimeoutMinutes(), s.getMissingLogoutAfterHours(), s.getMaxBreakMinutes(), s.getMaxDailyBreakMinutes(),
                    s.getMaxBreaksPerDay(), s.getBreakReminderIntervalMinutes(), s.getReportingTimezone(), s.isNotifyTeamLeaderOnIdle(),
                    s.isMonthlyReportEnabled(), s.getMonthlyReportHour(), s.getEmailMaxAttempts(), s.getEmailRetryBackoffMinutes(),
                    s.getRetentionDays(), s.getUpdatedAt());
        }
    }

    public record BreakPolicyRequest(@NotBlank @Size(max = 120) String name, UUID projectId, @Size(max = 30) String breakCategory,
                                     @Min(1) @Max(600) Integer maxDurationMinutes, @Min(1) @Max(100) Integer maxPerDay,
                                     @Min(1) @Max(600) Integer reminderAfterMinutes, Boolean active) {}

    public record CorrectionRequest(@NotBlank @Pattern(regexp = "SESSION|BREAK") String targetType, @NotNull UUID targetId,
                                    @NotBlank @Pattern(regexp = "logoutAt|loginAt|startedAt|endedAt|breakCategory") String field,
                                    @NotBlank @Size(max = 100) String value, @NotBlank @Size(min = 5, max = 500) String reason) {}

    public record CorrectionDto(UUID id, String targetType, UUID targetId, String field, String originalValue,
                                String correctedValue, String reason, UUID correctedBy, String correctedByName, Instant createdAt) {}

    public record LiveStatusRow(UUID userId, String employeeCode, String fullName, String projectName, String teamName,
                                String status, Instant loginAt, Instant lastActivityAt, Instant breakStartedAt,
                                String breakCategory, long todayBreakSeconds, int todayBreakCount, boolean breakExceeded,
                                Instant idleSince) {}

    public record DashboardView(LocalDate date, String timezone, long employeesInScope, long loggedInToday, long currentlyActive, long onBreak,
                                long idle, long away, long loggedOut, long notLoggedIn, long missingLogout, long totalBreaksToday,
                                long averageBreakSeconds, long exceedingBreakLimits, long attendanceExceptions,
                                double compliancePercent, List<LiveStatusRow> live, List<HourBucket> loginsByHour) {}

    public record HourBucket(int hour, long logins, long breaks) {}
}
