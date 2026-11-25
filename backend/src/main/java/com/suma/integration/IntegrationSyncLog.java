package com.suma.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_sync_logs")
@Getter
@Setter
public class IntegrationSyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempt = 1;
    @Column(nullable = false)
    private int fetched;
    @Column(nullable = false)
    private int created;
    @Column(nullable = false)
    private int duplicates;
    @Column(nullable = false)
    private int failed;
    @Column(columnDefinition = "text")
    private String error;
}
