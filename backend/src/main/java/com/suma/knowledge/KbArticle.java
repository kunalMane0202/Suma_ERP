package com.suma.knowledge;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kb_articles")
@Getter
@Setter
public class KbArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 250)
    private String title;
    @Column(nullable = false, unique = true, length = 260)
    private String slug;
    @Column(length = 500)
    private String summary;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "article_type", nullable = false, length = 20)
    private String articleType;
    @Column(name = "category_id")
    private UUID categoryId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(length = 500)
    private String tags;
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";
    @Column(name = "author_id")
    private UUID authorId;
    @Column(name = "view_count", nullable = false)
    private int viewCount;
    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;
}
