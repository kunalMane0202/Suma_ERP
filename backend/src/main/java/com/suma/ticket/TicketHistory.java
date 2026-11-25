package com.suma.ticket;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_history")
@Getter
@Setter
public class TicketHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "actor_name", length = 150)
    private String actorName;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(length = 60)
    private String field;
    @Column(name = "old_value", length = 500)
    private String oldValue;
    @Column(name = "new_value", length = 500)
    private String newValue;
    @Column(columnDefinition = "text")
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
