package com.suma.settings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;
    @Column(columnDefinition = "text")
    private String value;
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "STRING";
    @Column(nullable = false, length = 40)
    private String category;
    @Column(length = 300)
    private String description;
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
