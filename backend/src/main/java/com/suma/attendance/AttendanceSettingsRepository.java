package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AttendanceSettingsRepository extends JpaRepository<AttendanceSettings, UUID> {
    @Query("select s from AttendanceSettings s where s.projectId is null")
    Optional<AttendanceSettings> findGlobal();

    Optional<AttendanceSettings> findByProjectId(UUID projectId);
}
