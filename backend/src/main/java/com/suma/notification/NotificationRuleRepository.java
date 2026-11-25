package com.suma.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {
    @Query("select r from NotificationRule r where r.active = true and r.eventType = :event and (r.projectId is null or r.projectId = :projectId)")
    List<NotificationRule> applicable(String event, UUID projectId);
}
