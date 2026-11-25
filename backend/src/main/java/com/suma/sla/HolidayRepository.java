package com.suma.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {
    List<Holiday> findByCalendarIdOrderByHolidayDateAsc(UUID calendarId);
}
