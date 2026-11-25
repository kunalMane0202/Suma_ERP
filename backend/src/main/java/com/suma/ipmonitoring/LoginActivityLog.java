package com.suma.ipmonitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_activity_logs")
@Getter
@Setter
public class LoginActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "username_attempted", length = 120)
    private String usernameAttempted;
    @Column(nullable = false)
    private boolean success;
    @Column(name = "failure_category", length = 30)
    private String failureCategory;
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;
    @Column(name = "ip_version", nullable = false)
    private short ipVersion;
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(length = 60)
    private String device;
    @Column(length = 60)
    private String browser;
    @Column(name = "auth_method", nullable = false, length = 30)
    private String authMethod = "PASSWORD";
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;
    @Column(nullable = false)
    private boolean suspicious;
    @Column(name = "suspicious_reasons", length = 500)
    private String suspiciousReasons;
    @Column(name = "geo_location", length = 120)
    private String geoLocation;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
