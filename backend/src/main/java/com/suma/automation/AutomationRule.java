package com.suma.automation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_rules")
@Getter
@Setter
public class AutomationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(name = "trigger_event", nullable = false, length = 40)
    private String triggerEvent;
    @Column(nullable = false, columnDefinition = "text")
    private String conditions = "[]";
    @Column(nullable = false, columnDefinition = "text")
    private String actions = "[]";
    @Column(name = "project_id")
    private UUID projectId;
    @Column(nullable = false)
    private int priority = 100;
    @Column(name = "stop_processing", nullable = false)
    private boolean stopProcessing;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
