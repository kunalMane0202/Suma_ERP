package com.suma.ipmonitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface AnonymousAccessLogRepository extends JpaRepository<AnonymousAccessLog, UUID>, JpaSpecificationExecutor<AnonymousAccessLog> {
    long countByCreatedAtAfter(Instant after);
    long countByCategoryAndCreatedAtAfter(String category, Instant after);

    @Modifying
    @Query("delete from AnonymousAccessLog l where l.createdAt < :before")
    int deleteOlderThan(Instant before);
}
