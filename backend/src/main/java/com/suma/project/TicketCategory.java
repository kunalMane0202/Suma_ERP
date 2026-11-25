package com.suma.project;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_categories")
@Getter
@Setter
public class TicketCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "parent_id")
    private UUID parentId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 500)
    private String keywords;
    @Column(name = "default_team_id")
    private UUID defaultTeamId;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
