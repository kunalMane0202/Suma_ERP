package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Application-level inactivity. Kept separate from breaks; never converted to a break without the employee's confirmation. */
@Entity
@Table(name = "idle_events")
@Getter
@Setter
public class IdleEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "idle_started_at", nullable = false)
    private Instant idleStartedAt;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    @Column(nullable = false, length = 20)
    private String status = "UNCONFIRMED";
    @Column(length = 20)
    private String response;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @Column(nullable = false, length = 30)
    private String source = "BROWSER";
    @Column(name = "team_leader_notified", nullable = false)
    private boolean teamLeaderNotified;
    @Column(name = "linked_break_id")
    private UUID linkedBreakId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
