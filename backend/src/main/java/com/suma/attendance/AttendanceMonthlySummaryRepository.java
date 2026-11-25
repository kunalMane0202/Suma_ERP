package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceMonthlySummaryRepository extends JpaRepository<AttendanceMonthlySummary, UUID> {
    Optional<AttendanceMonthlySummary> findByUserIdAndPeriodYearAndPeriodMonth(UUID userId, int year, int month);
}
