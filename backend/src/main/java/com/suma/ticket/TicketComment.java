package com.suma.ticket;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
@Getter
@Setter
public class TicketComment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "author_id")
    private UUID authorId;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(nullable = false)
    private boolean internal;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
