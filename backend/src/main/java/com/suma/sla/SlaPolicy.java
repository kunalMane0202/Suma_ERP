package com.suma.sla;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sla_policies")
@Getter
@Setter
public class SlaPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(nullable = false, length = 20)
    private String priority;
    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;
    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;
    @Column(name = "calendar_id")
    private UUID calendarId;
    @Column(name = "business_hours_only", nullable = false)
    private boolean businessHoursOnly = true;
    @Column(name = "pause_on_pending", nullable = false)
    private boolean pauseOnPending = true;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
