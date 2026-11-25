package com.suma.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
@Getter
@Setter
public class EmailOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 200)
    private String recipient;
    @Column(nullable = false, length = 300)
    private String subject;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @Column(name = "attachment_name", length = 200)
    private String attachmentName;
    @Column(name = "attachment_type", length = 120)
    private String attachmentType;
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "attachment_content")
    private byte[] attachmentContent;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(nullable = false)
    private int attempts;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @Column(name = "related_type", length = 40)
    private String relatedType;
    @Column(name = "related_id", length = 60)
    private String relatedId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
}
