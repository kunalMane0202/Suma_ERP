package com.suma.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 1000)
    private String message;
    @Column(nullable = false, length = 10)
    private String severity = "INFO";
    @Column(name = "entity_type", length = 40)
    private String entityType;
    @Column(name = "entity_id", length = 60)
    private String entityId;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
