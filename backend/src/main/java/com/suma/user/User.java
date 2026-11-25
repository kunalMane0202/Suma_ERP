package com.suma.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "employee_code", nullable = false, unique = true, length = 40)
    private String employeeCode;
    @Column(nullable = false, unique = true, length = 80)
    private String username;
    @Column(nullable = false, unique = true, length = 200)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(length = 40)
    private String phone;
    @Column(name = "job_title", length = 100)
    private String jobTitle;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
    @Column(name = "theme_preference", nullable = false, length = 10)
    private String themePreference = "LIGHT";
    @Column(length = 60)
    private String timezone;
    @Column(name = "max_open_tickets")
    private Integer maxOpenTickets;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_weekly_offs", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "day_of_week")
    private Set<Short> weeklyOffs = new HashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public String roleCode() { return role.getCode(); }
}
