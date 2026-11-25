package com.suma.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KbArticleRepository extends JpaRepository<KbArticle, UUID> {
    boolean existsBySlug(String slug);
}
