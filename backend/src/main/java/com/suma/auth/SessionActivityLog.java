package com.suma.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_activity_logs")
@Getter
@Setter
public class SessionActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(length = 300)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public static SessionActivityLog of(UUID sessionId, UUID userId, String action, String ip, UUID actor, String reason) {
        SessionActivityLog l = new SessionActivityLog();
        l.sessionId = sessionId; l.userId = userId; l.action = action; l.ipAddress = ip; l.actorId = actor; l.reason = reason;
        return l;
    }
}
