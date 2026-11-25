package com.suma.sla;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "escalation_rules")
@Getter
@Setter
public class EscalationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(length = 20)
    private String priority;
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;
    @Column(name = "threshold_percent")
    private Integer thresholdPercent;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(name = "notify_role", length = 40)
    private String notifyRole;
    @Column(name = "notify_user_id")
    private UUID notifyUserId;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
