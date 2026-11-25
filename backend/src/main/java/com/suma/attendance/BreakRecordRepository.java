package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BreakRecordRepository extends JpaRepository<BreakRecord, UUID> {
    Optional<BreakRecord> findFirstByUserIdAndStatus(UUID userId, String status);
    List<BreakRecord> findByStatus(String status);
    List<BreakRecord> findBySessionIdAndStatus(UUID sessionId, String status);
    List<BreakRecord> findByUserIdAndWorkDateOrderByStartedAtAsc(UUID userId, LocalDate date);
    List<BreakRecord> findByUserIdAndWorkDateBetweenOrderByStartedAtDesc(UUID userId, LocalDate from, LocalDate to);
    List<BreakRecord> findByUserIdInAndWorkDateBetweenOrderByStartedAtAsc(Collection<UUID> users, LocalDate from, LocalDate to);
    List<BreakRecord> findByWorkDateBetweenOrderByStartedAtAsc(LocalDate from, LocalDate to);
}
