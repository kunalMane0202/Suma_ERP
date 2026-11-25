package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "auth_session_id", nullable = false, unique = true)
    private UUID authSessionId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "login_at", nullable = false)
    private Instant loginAt;
    @Column(name = "logout_at")
    private Instant logoutAt;
    @Column(name = "logout_source", length = 30)
    private String logoutSource;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "current_state", nullable = false, length = 20)
    private String currentState = "WORKING";
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
    @Column(name = "login_ip", length = 45)
    private String loginIp;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(length = 60)
    private String device;
    @Column(length = 60)
    private String browser;
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
