package com.suma.attendance;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance_corrections")
@Getter
@Setter
public class AttendanceCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;
    @Column(name = "target_id", nullable = false)
    private UUID targetId;
    @Column(nullable = false, length = 40)
    private String field;
    @Column(name = "original_value", length = 100)
    private String originalValue;
    @Column(name = "corrected_value", length = 100)
    private String correctedValue;
    @Column(nullable = false, length = 500)
    private String reason;
    @Column(name = "corrected_by", nullable = false)
    private UUID correctedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
