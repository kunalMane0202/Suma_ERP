package com.suma.ticket;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_number", nullable = false, unique = true, length = 30)
    private String ticketNumber;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false, length = 300)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false, length = 20)
    private String status = "NEW";
    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";
    @Column(name = "category_id")
    private UUID categoryId;
    @Column(name = "subcategory_id")
    private UUID subcategoryId;
    @Column(nullable = false, length = 20)
    private String source = "MANUAL";
    @Column(name = "external_system", length = 40)
    private String externalSystem;
    @Column(name = "external_id", length = 120)
    private String externalId;
    @Column(name = "reporter_name", length = 150)
    private String reporterName;
    @Column(name = "reporter_email", length = 200)
    private String reporterEmail;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "assignee_id")
    private UUID assigneeId;
    @Column(name = "team_id")
    private UUID teamId;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount;
    @Column(name = "first_response_at")
    private Instant firstResponseAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "response_due_at")
    private Instant responseDueAt;
    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;
    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;
    @Column(name = "sla_paused_seconds", nullable = false)
    private long slaPausedSeconds;
    @Column(name = "response_breached", nullable = false)
    private boolean responseBreached;
    @Column(name = "resolution_breached", nullable = false)
    private boolean resolutionBreached;
    @Column(name = "sla_policy_id")
    private UUID slaPolicyId;
    @Column(name = "custom_fields", columnDefinition = "text")
    private String customFields;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public boolean isOpen() {
        return !"RESOLVED".equals(status) && !"CLOSED".equals(status);
    }
}
