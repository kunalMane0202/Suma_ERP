package com.suma.client;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Getter
@Setter
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 100)
    private String industry;
    @Column(length = 200)
    private String email;
    @Column(length = 40)
    private String phone;
    @Column(length = 500)
    private String address;
    @Column(length = 200)
    private String website;
    @Column(name = "account_manager_id")
    private UUID accountManagerId;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(columnDefinition = "text")
    private String configuration;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
