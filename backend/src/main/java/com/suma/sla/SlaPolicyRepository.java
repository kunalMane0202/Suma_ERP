package com.suma.sla;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    @Query("select p from SlaPolicy p where p.active = true and p.priority = :priority and (p.projectId = :projectId or p.projectId is null) order by p.projectId nulls last")
    List<SlaPolicy> applicable(UUID projectId, String priority);
}
