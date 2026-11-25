package com.suma.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "auth_method", nullable = false, length = 30)
    private String authMethod = "PASSWORD";
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "ip_version")
    private Short ipVersion;
    @Column(name = "last_ip", length = 45)
    private String lastIp;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(length = 60)
    private String device;
    @Column(length = 60)
    private String browser;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "ended_reason", length = 200)
    private String endedReason;
    @Column(name = "ended_by")
    private UUID endedBy;

    public boolean isActive() { return "ACTIVE".equals(status); }
}
