package com.suma.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
    Page<AutomationExecution> findAllByOrderByCreatedAtDesc(Pageable p);
}
