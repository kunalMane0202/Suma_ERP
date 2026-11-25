package com.suma.automation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
    List<ApprovalRequest> findByApproverIdAndStatusOrderByCreatedAtDesc(UUID approverId, String status);
    List<ApprovalRequest> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
    List<ApprovalRequest> findTop100ByOrderByCreatedAtDesc();
}
