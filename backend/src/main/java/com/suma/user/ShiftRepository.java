package com.suma.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
