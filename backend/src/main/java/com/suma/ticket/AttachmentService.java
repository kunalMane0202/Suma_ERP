package com.suma.ticket;

import com.suma.common.ApiException;
import com.suma.config.SumaProperties;
import com.suma.security.AuthUser;
import com.suma.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secure attachment storage: allow-listed extensions, size limit, random server-side names, path traversal protection. */
@Service
public class AttachmentService {
    private final TicketService tickets;
    private final TicketAttachmentRepository repo;
    private final SettingsService settings;
    private final Path root;
    private final Clock clock;

    public AttachmentService(TicketService tickets, TicketAttachmentRepository repo, SettingsService settings, SumaProperties props, Clock clock) {
        this.tickets = tickets;
        this.repo = repo;
        this.settings = settings;
        this.root = Path.of(props.uploadsDir()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Transactional
    public TicketDtos.AttachmentDto upload(AuthUser me, UUID ticketId, MultipartFile file) {
        Ticket t = tickets.load(me, ticketId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("File is empty");
        long max = settings.getInt("attachments.max_size_mb", 10) * 1024L * 1024L;
        if (file.getSize() > max) throw ApiException.badRequest("File exceeds the maximum size");
        String original = sanitize(file.getOriginalFilename());
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        Set<String> allowed = Arrays.stream(settings.get("attachments.allowed_types", "pdf,png,jpg,jpeg,txt,log,csv").split(","))
                .map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
        if (!allowed.contains(ext)) throw ApiException.badRequest("File type ." + ext + " is not allowed");
        try {
            Path dir = root.resolve(ticketId.toString()).normalize();
            if (!dir.startsWith(root)) throw ApiException.badRequest("Invalid path");
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), md)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            TicketAttachment a = new TicketAttachment();
            a.setTicketId(ticketId);
            a.setUploadedBy(me.id());
            a.setFileName(original);
            a.setContentType(safeType(ext));
            a.setSizeBytes(Files.size(target));
            a.setStoragePath(root.relativize(target).toString());
            a.setSha256(HexFormat.of().formatHex(md.digest()));
            a.setCreatedAt(Instant.now(clock));
            repo.save(a);
            tickets.record(t, me.id(), "ATTACHMENT_ADDED", null, null, original, null);
            tickets.touch(t);
            return new TicketDtos.AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), null, a.getCreatedAt());
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not store attachment", e);
        }
    }

    public record Download(byte[] content, String fileName, String contentType) {}

    @Transactional(readOnly = true)
    public Download download(AuthUser me, UUID ticketId, UUID attachmentId) {
        tickets.load(me, ticketId);
        TicketAttachment a = repo.findById(attachmentId).filter(x -> x.getTicketId().equals(ticketId))
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        Path p = root.resolve(a.getStoragePath()).normalize();
        if (!p.startsWith(root)) throw ApiException.notFound("Attachment");
        try {
            return new Download(Files.readAllBytes(p), a.getFileName(), a.getContentType());
        } catch (IOException e) {
            throw ApiException.notFound("Attachment file");
        }
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) return "file";
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isEmpty() || base.startsWith(".")) base = "file" + base;
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String safeType(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "txt", "log" -> "text/plain";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}
