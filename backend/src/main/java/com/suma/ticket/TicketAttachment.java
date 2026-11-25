package com.suma.ticket;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
public class TicketAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
