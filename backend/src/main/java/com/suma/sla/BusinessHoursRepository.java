package com.suma.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BusinessHoursRepository extends JpaRepository<BusinessHours, UUID> {
    List<BusinessHours> findByCalendarIdOrderByDayOfWeekAsc(UUID calendarId);
    void deleteByCalendarId(UUID calendarId);
}
