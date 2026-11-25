package com.suma.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessCalendarRepository extends JpaRepository<BusinessCalendar, UUID> {
    Optional<BusinessCalendar> findFirstByDefaultCalendarTrue();
}
