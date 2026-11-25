package com.suma.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BreakReminderRepository extends JpaRepository<BreakReminder, UUID> {
    List<BreakReminder> findByBreakIdOrderBySentAtAsc(UUID breakId);
    long countByBreakIdAndReminderType(UUID breakId, String type);
}
