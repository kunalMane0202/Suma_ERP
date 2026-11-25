package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_daily_summary")
@Getter
@Setter
public class AttendanceDailySummary {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "first_login_at")
    private Instant firstLoginAt;
    @Column(name = "last_logout_at")
    private Instant lastLogoutAt;
    @Column(name = "session_count", nullable = false)
    private int sessionCount;
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
    @Column(name = "exceeded_break_count", nullable = false)
    private int exceededBreakCount;
    @Column(name = "missing_logout", nullable = false)
    private boolean missingLogout;
    @Column(columnDefinition = "text")
    private String exceptions;
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
