package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "break_policies")
@Getter
@Setter
public class BreakPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "break_category", length = 30)
    private String breakCategory;
    @Column(name = "max_duration_minutes")
    private Integer maxDurationMinutes;
    @Column(name = "max_per_day")
    private Integer maxPerDay;
    @Column(name = "reminder_after_minutes")
    private Integer reminderAfterMinutes;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
