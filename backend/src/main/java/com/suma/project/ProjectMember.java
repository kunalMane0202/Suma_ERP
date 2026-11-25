package com.suma.project;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
@IdClass(ProjectMember.Key.class)
@Getter
@Setter
public class ProjectMember {
    @Id
    @Column(name = "project_id")
    private UUID projectId;
    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(nullable = false, length = 30)
    private String designation = "MEMBER";
    @Column(name = "allocation_percent", nullable = false)
    private int allocationPercent = 100;
    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public record Key(UUID projectId, UUID userId) implements Serializable {
        public Key() { this(null, null); }
    }
}
