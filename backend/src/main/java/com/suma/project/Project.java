package com.suma.project;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "manager_id")
    private UUID managerId;
    @Column(name = "team_leader_id")
    private UUID teamLeaderId;
    @Column(name = "project_lead_id")
    private UUID projectLeadId;
    @Column(name = "project_lead_email", length = 200)
    private String projectLeadEmail;
    @Column(name = "project_lead_email_verified", nullable = false)
    private boolean projectLeadEmailVerified;
    @Column(name = "calendar_id")
    private UUID calendarId;
    @Column(name = "support_hours", length = 100)
    private String supportHours;
    @Column(nullable = false, length = 60)
    private String timezone = "UTC";
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
