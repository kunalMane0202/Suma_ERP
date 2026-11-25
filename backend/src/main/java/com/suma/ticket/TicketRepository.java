package com.suma.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    Optional<Ticket> findByExternalSystemAndExternalId(String system, String externalId);
    boolean existsByExternalSystemAndExternalId(String system, String externalId);

    @Query("select t from Ticket t where t.status not in ('RESOLVED','CLOSED') and t.firstResponseAt is null and t.responseBreached = false and t.responseDueAt < :now and t.slaPausedAt is null")
    List<Ticket> responseBreaches(Instant now);

    @Query("select t from Ticket t where t.status not in ('RESOLVED','CLOSED') and t.resolutionBreached = false and t.resolutionDueAt < :now and t.slaPausedAt is null")
    List<Ticket> resolutionBreaches(Instant now);

    @Query("select t from Ticket t where t.status not in ('RESOLVED','CLOSED') and t.resolutionDueAt is not null and t.slaPausedAt is null")
    List<Ticket> openWithSla();

    @Query("select t from Ticket t where t.status = 'PENDING' and t.updatedAt < :before")
    List<Ticket> pendingSince(Instant before);

    @Query("select t from Ticket t where t.status = 'RESOLVED' and t.resolvedAt < :before")
    List<Ticket> resolvedBefore(Instant before);
}
