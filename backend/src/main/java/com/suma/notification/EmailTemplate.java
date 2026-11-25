package com.suma.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_templates")
@Getter
@Setter
public class EmailTemplate {
    @Id
    @Column(length = 60)
    private String code;
    @Column(nullable = false, length = 300)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(length = 300)
    private String description;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
