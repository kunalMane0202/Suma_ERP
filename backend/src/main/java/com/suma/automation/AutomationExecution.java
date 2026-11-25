package com.suma.automation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_executions")
@Getter
@Setter
public class AutomationExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;
    @Column(name = "trigger_event", nullable = false, length = 40)
    private String triggerEvent;
    @Column(name = "entity_type", length = 40)
    private String entityType;
    @Column(name = "entity_id", length = 60)
    private String entityId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "actions_run", columnDefinition = "text")
    private String actionsRun;
    @Column(columnDefinition = "text")
    private String error;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
