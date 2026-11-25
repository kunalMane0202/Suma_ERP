package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceReportLogRepository extends JpaRepository<AttendanceReportLog, UUID> {
    Optional<AttendanceReportLog> findByProjectIdAndReportTypeAndPeriodYearAndPeriodMonthAndPeriodDay(UUID projectId, String type, int year, int month, int day);

    @Query("select l from AttendanceReportLog l where (:restrict = false or l.projectId in :projects) order by l.createdAt desc")
    List<AttendanceReportLog> recent(boolean restrict, Collection<UUID> projects);

    List<AttendanceReportLog> findByStatusInAndNextRetryAtBefore(Collection<String> statuses, Instant before);
}
