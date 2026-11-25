package com.suma.ipmonitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anonymous_access_logs")
@Getter
@Setter
public class AnonymousAccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;
    @Column(name = "ip_version", nullable = false)
    private short ipVersion;
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;
    @Column(nullable = false, length = 500)
    private String endpoint;
    @Column(nullable = false, length = 30)
    private String category;
    @Column(name = "status_code", nullable = false)
    private int statusCode;
    @Column(name = "user_id")
    private UUID userId;
    @Column(length = 120)
    private String username;
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
