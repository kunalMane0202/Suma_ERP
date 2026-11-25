package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdleEventRepository extends JpaRepository<IdleEvent, UUID> {
    Optional<IdleEvent> findFirstBySessionIdAndEndedAtIsNull(UUID sessionId);
    List<IdleEvent> findByUserIdAndWorkDateOrderByIdleStartedAtAsc(UUID userId, LocalDate date);
    List<IdleEvent> findByUserIdAndWorkDateBetweenOrderByIdleStartedAtDesc(UUID userId, LocalDate from, LocalDate to);
    List<IdleEvent> findByUserIdInAndWorkDateBetweenOrderByIdleStartedAtAsc(Collection<UUID> users, LocalDate from, LocalDate to);
    List<IdleEvent> findByWorkDateBetweenOrderByIdleStartedAtAsc(LocalDate from, LocalDate to);
    List<IdleEvent> findByEndedAtIsNull();
}
