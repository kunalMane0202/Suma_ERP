package com.suma.client;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_contacts")
@Getter
@Setter
public class ClientContact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 200)
    private String email;
    @Column(length = 40)
    private String phone;
    @Column(name = "role_title", length = 100)
    private String roleTitle;
    @Column(name = "is_primary", nullable = false)
    private boolean primary;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
