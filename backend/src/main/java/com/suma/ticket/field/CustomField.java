package com.suma.ticket.field;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Admin-defined extra ticket field. Values live in tickets.custom_fields (JSON) keyed by fieldKey. */
@Entity
@Table(name = "custom_fields")
@Getter
@Setter
public class CustomField {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType = "TICKET";
    @Column(name = "field_key", nullable = false, length = 60)
    private String fieldKey;
    @Column(nullable = false, length = 120)
    private String label;
    @Column(name = "field_type", nullable = false, length = 20)
    private String fieldType;
    /** Comma-separated choices for SELECT fields. */
    private String options;
    @Column(nullable = false)
    private boolean required;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
