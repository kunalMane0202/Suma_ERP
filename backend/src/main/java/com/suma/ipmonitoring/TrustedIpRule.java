package com.suma.ipmonitoring;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trusted_ip_rules")
@Getter
@Setter
public class TrustedIpRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 60)
    private String cidr;
    @Column(name = "rule_type", nullable = false, length = 10)
    private String ruleType;
    @Column(length = 300)
    private String description;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
