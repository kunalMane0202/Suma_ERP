package com.suma.sla;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "business_hours")
@Getter
@Setter
public class BusinessHours {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
}
