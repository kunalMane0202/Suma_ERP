package com.suma.automation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "requested_by")
    private UUID requestedBy;
    @Column(name = "approver_id")
    private UUID approverId;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(length = 500)
    private String reason;
    @Column(name = "decision_note", length = 500)
    private String decisionNote;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
