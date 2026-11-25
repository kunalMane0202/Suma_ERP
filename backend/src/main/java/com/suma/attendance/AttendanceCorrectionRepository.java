package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {
    List<AttendanceCorrection> findByTargetIdOrderByCreatedAtAsc(UUID targetId);
    List<AttendanceCorrection> findTop200ByOrderByCreatedAtDesc();
}
