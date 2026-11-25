package com.suma.ipmonitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ip_activity_logs")
@Getter
@Setter
public class IpActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;
    @Column(name = "ip_version", nullable = false)
    private short ipVersion;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "activity_type", nullable = false, length = 30)
    private String activityType;
    @Column(length = 500)
    private String details;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
