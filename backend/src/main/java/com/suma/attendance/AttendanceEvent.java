package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Immutable attendance history entry (login, logout, breaks, idle, confirmations, reminders, corrections). */
@Entity
@Table(name = "attendance_events")
@Getter
@Setter
public class AttendanceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "session_id")
    private UUID sessionId;
    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, length = 30)
    private String source;
    @Column(columnDefinition = "text")
    private String details;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
