package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceDailySummaryRepository extends JpaRepository<AttendanceDailySummary, UUID> {
    Optional<AttendanceDailySummary> findByUserIdAndWorkDate(UUID userId, LocalDate date);
}
