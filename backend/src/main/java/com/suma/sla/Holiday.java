package com.suma.sla;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "holidays")
@Getter
@Setter
public class Holiday {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;
    @Column(nullable = false, length = 150)
    private String name;
}
