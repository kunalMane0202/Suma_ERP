package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {
    Optional<AttendanceSession> findByAuthSessionId(UUID authSessionId);
    List<AttendanceSession> findByStatus(String status);
    List<AttendanceSession> findByUserIdAndWorkDateOrderByLoginAtAsc(UUID userId, LocalDate date);
    List<AttendanceSession> findByUserIdAndStatusOrderByLoginAtDesc(UUID userId, String status);
    List<AttendanceSession> findByWorkDateBetweenOrderByLoginAtAsc(LocalDate from, LocalDate to);
    List<AttendanceSession> findByUserIdInAndWorkDateBetweenOrderByLoginAtAsc(Collection<UUID> users, LocalDate from, LocalDate to);

    @Query("select s from AttendanceSession s where s.status = 'ACTIVE' and coalesce(s.lastHeartbeatAt, s.loginAt) < :before")
    List<AttendanceSession> findStaleActive(Instant before);
}
