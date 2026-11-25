package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_report_logs")
@Getter
@Setter
public class AttendanceReportLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;
    @Column(name = "period_year", nullable = false)
    private int periodYear;
    @Column(name = "period_month", nullable = false)
    private int periodMonth;
    @Column(name = "period_day", nullable = false)
    private int periodDay;
    @Column(name = "recipient_email", length = 200)
    private String recipientEmail;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "employee_count", nullable = false)
    private int employeeCount;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "generated_at")
    private Instant generatedAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "file_name", length = 200)
    private String fileName;
    @Column(name = "triggered_by", nullable = false, length = 20)
    private String triggeredBy = "SCHEDULER";
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
