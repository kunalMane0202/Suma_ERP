package com.suma.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KbCategoryRepository extends JpaRepository<KbCategory, UUID> {
    boolean existsByNameIgnoreCase(String name);
}
