package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "break_reminders")
@Getter
@Setter
public class BreakReminder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "break_id", nullable = false)
    private UUID breakId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "reminder_type", nullable = false, length = 20)
    private String reminderType;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo = 1;
    @Column(nullable = false, length = 20)
    private String channel = "IN_APP";
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
}
