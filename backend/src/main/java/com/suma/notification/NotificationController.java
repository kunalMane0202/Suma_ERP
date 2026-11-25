package com.suma.notification;

import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationRepository repo;
    private final EmailOutboxRepository outbox;
    private final NotificationRuleRepository rules;

    public NotificationController(NotificationService service, NotificationRepository repo, EmailOutboxRepository outbox, NotificationRuleRepository rules) {
        this.service = service;
        this.repo = repo;
        this.outbox = outbox;
        this.rules = rules;
    }

    @GetMapping
    public PageResponse<Notification> list(@RequestParam(defaultValue = "false") boolean unread, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(CurrentUser.get().id(), unread, page, size));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unread(CurrentUser.get().id()));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public void markRead(@PathVariable UUID id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Notification"));
        if (!n.getUserId().equals(CurrentUser.get().id())) throw ApiException.notFound("Notification");
        if (n.getReadAt() == null) n.setReadAt(Instant.now());
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAll() {
        return Map.of("updated", repo.markAllRead(CurrentUser.get().id(), Instant.now()));
    }

    public record OutboxDto(UUID id, String recipient, String subject, String status, int attempts, int maxAttempts,
                            Instant nextAttemptAt, String lastError, String relatedType, String relatedId, String attachmentName,
                            Instant createdAt, Instant sentAt) {}

    @GetMapping("/email-log")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    @Transactional(readOnly = true)
    public PageResponse<OutboxDto> emailLog(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(outbox.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200))),
                e -> new OutboxDto(e.getId(), e.getRecipient(), e.getSubject(), e.getStatus(), e.getAttempts(), e.getMaxAttempts(),
                        e.getNextAttemptAt(), e.getLastError(), e.getRelatedType(), e.getRelatedId(), e.getAttachmentName(), e.getCreatedAt(), e.getSentAt()));
    }

    @PostMapping("/email-log/{id}/retry")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    @Transactional
    public void retry(@PathVariable UUID id) {
        EmailOutbox e = outbox.findById(id).orElseThrow(() -> ApiException.notFound("Email"));
        if ("SENT".equals(e.getStatus())) throw ApiException.conflict("Email already sent");
        e.setStatus("RETRYING");
        e.setMaxAttempts(Math.max(e.getMaxAttempts(), e.getAttempts() + 1));
        e.setNextAttemptAt(Instant.now());
    }

    public record RuleRequest(UUID projectId, @NotBlank String eventType, @NotBlank @Pattern(regexp = "IN_APP|EMAIL|BOTH") String channel,
                              String recipientRole, Integer escalationLevel, Boolean active) {}

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public List<NotificationRule> rules() {
        return rules.findAll();
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public NotificationRule createRule(@Valid @RequestBody RuleRequest r) {
        NotificationRule n = new NotificationRule();
        n.setProjectId(r.projectId());
        n.setEventType(r.eventType());
        n.setChannel(r.channel());
        n.setRecipientRole(r.recipientRole());
        n.setEscalationLevel(r.escalationLevel() == null ? 0 : r.escalationLevel());
        if (r.active() != null) n.setActive(r.active());
        return rules.save(n);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('NOTIFICATION_MANAGE')")
    public void deleteRule(@PathVariable UUID id) {
        rules.deleteById(id);
    }
}
