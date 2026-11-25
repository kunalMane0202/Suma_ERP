package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "break_records")
@Getter
@Setter
public class BreakRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "break_category", nullable = false, length = 30)
    private String breakCategory;
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "start_source", nullable = false, length = 30)
    private String startSource;
    @Column(name = "end_source", length = 30)
    private String endSource;
    @Column(nullable = false)
    private boolean exceeded;
    @Column(length = 500)
    private String notes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isActive() { return "ACTIVE".equals(status); }
}
