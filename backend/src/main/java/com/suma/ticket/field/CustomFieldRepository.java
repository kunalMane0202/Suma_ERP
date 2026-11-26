package com.suma.ticket.field;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {
    @Query("select f from CustomField f where f.entityType = 'TICKET' and f.active = true and (f.projectId is null or f.projectId = :projectId) order by f.createdAt")
    List<CustomField> applicable(UUID projectId);

    @Query("select f from CustomField f where f.entityType = 'TICKET' order by f.active desc, f.createdAt")
    List<CustomField> allForTickets();

    boolean existsByEntityTypeAndFieldKeyAndProjectId(String entityType, String fieldKey, UUID projectId);
}
