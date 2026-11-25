package com.suma.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    @Query("select e.id from EmailOutbox e where e.status in ('PENDING','RETRYING') and e.nextAttemptAt <= :now order by e.nextAttemptAt")
    List<UUID> findDueIds(Instant now, Pageable p);

    Page<EmailOutbox> findAllByOrderByCreatedAtDesc(Pageable p);
    List<EmailOutbox> findByRelatedTypeAndRelatedId(String type, String id);
    long countByStatus(String status);
}
