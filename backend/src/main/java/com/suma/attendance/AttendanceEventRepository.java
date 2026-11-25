package com.suma.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {
    @Query("""
            select e from AttendanceEvent e where (:restrict = false or e.userId in :users)
            and (cast(:userId as uuid) is null or e.userId = :userId)
            and (:type is null or e.eventType = :type)
            and e.occurredAt >= :from and e.occurredAt < :to order by e.occurredAt desc
            """)
    Page<AttendanceEvent> search(boolean restrict, Collection<UUID> users, UUID userId, String type, Instant from, Instant to, Pageable p);

    @Modifying
    @Query("delete from AttendanceEvent e where e.occurredAt < :before")
    int deleteOlderThan(Instant before);
}
