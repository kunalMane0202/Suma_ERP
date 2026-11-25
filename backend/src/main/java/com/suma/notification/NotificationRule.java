package com.suma.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_rules")
@Getter
@Setter
public class NotificationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;
    @Column(nullable = false, length = 20)
    private String channel;
    @Column(name = "recipient_role", length = 40)
    private String recipientRole;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
