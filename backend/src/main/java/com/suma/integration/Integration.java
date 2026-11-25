package com.suma.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integrations")
@Getter
@Setter
public class Integration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 120)
    private String name;
    @Column(name = "connector_type", nullable = false, length = 20)
    private String connectorType;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "base_url", length = 500)
    private String baseUrl;
    @Column(name = "auth_type", nullable = false, length = 20)
    private String authType = "NONE";
    @Column(name = "credentials_encrypted", columnDefinition = "text")
    private String credentialsEncrypted;
    @Column(length = 1000)
    private String query;
    @Column(name = "field_mapping", columnDefinition = "text")
    private String fieldMapping;
    @Column(name = "sync_interval_minutes", nullable = false)
    private int syncIntervalMinutes = 15;
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;
    @Column(name = "last_status", length = 20)
    private String lastStatus;
    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
