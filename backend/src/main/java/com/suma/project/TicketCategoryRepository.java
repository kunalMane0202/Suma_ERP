package com.suma.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TicketCategoryRepository extends JpaRepository<TicketCategory, UUID> {
    @Query("select c from TicketCategory c where c.active = true and (c.projectId = :projectId or c.projectId is null) order by c.name")
    List<TicketCategory> forProject(UUID projectId);
}
