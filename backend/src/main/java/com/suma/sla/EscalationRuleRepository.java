package com.suma.sla;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EscalationRuleRepository extends JpaRepository<EscalationRule, UUID> {
    @Query("select r from EscalationRule r where r.active = true and (r.projectId = :projectId or r.projectId is null) and (r.priority = :priority or r.priority is null) order by r.escalationLevel")
    List<EscalationRule> applicable(UUID projectId, String priority);
}
