package com.suma.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    @Query("select r from AutomationRule r where r.active = true and r.triggerEvent = :event and (r.projectId is null or r.projectId = :projectId) order by r.priority, r.createdAt")
    List<AutomationRule> applicable(String event, UUID projectId);
}
