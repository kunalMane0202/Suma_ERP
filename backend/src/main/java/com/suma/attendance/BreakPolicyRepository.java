package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BreakPolicyRepository extends JpaRepository<BreakPolicy, UUID> {
    @Query("select p from BreakPolicy p where p.active = true and (p.projectId = :projectId or p.projectId is null)")
    List<BreakPolicy> applicable(UUID projectId);
}
