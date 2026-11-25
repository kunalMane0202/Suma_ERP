package com.suma.ipmonitoring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface IpActivityLogRepository extends JpaRepository<IpActivityLog, UUID>, JpaSpecificationExecutor<IpActivityLog> {
    long countByActivityTypeAndCreatedAtAfter(String type, Instant after);

    @Modifying
    @Query("delete from IpActivityLog l where l.createdAt < :before")
    int deleteOlderThan(Instant before);
}
