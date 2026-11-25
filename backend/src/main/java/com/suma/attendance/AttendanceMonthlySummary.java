package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_monthly_summary")
@Getter
@Setter
public class AttendanceMonthlySummary {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "period_year", nullable = false)
    private int periodYear;
    @Column(name = "period_month", nullable = false)
    private int periodMonth;
    @Column(name = "days_present", nullable = false)
    private int daysPresent;
    @Column(name = "session_seconds", nullable = false)
    private long sessionSeconds;
    @Column(name = "break_count", nullable = false)
    private int breakCount;
    @Column(name = "break_seconds", nullable = false)
    private long breakSeconds;
    @Column(name = "idle_count", nullable = false)
    private int idleCount;
    @Column(name = "idle_seconds", nullable = false)
    private long idleSeconds;
    @Column(name = "exception_count", nullable = false)
    private int exceptionCount;
    @Column(name = "missing_logout_count", nullable = false)
    private int missingLogoutCount;
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
