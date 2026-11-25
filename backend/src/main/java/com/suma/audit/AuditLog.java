package com.suma.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "actor_username", length = 80)
    private String actorUsername;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "entity_type", length = 60)
    private String entityType;
    @Column(name = "entity_id", length = 60)
    private String entityId;
    @Column(columnDefinition = "text")
    private String details;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
