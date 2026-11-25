package com.suma.notification;

import com.suma.user.User;
import com.suma.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * In-app notifications and templated emails. Emails are written to the outbox in the caller's transaction and
 * delivered asynchronously by {@link EmailDeliveryService} with retries.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final EmailTemplateRepository templates;
    private final NotificationRuleRepository rules;
    private final UserRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, EmailOutboxRepository outbox, EmailTemplateRepository templates,
                               NotificationRuleRepository rules, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.templates = templates;
        this.rules = rules;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public Notification notify(UUID userId, String type, String title, String message, String severity, String entityType, Object entityId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        n.setMessage(message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
        n.setSeverity(severity == null ? "INFO" : severity);
        n.setEntityType(entityType);
        n.setEntityId(entityId == null ? null : entityId.toString());
        n.setCreatedAt(Instant.now(clock));
        return notifications.save(n);
    }

    @Transactional
    public void notifyAll(Collection<UUID> userIds, String type, String title, String message, String severity, String entityType, Object entityId) {
        userIds.stream().distinct().forEach(u -> notify(u, type, title, message, severity, entityType, entityId));
    }

    /** Sends email to a user if a notification rule for this event/project asks for EMAIL or BOTH. */
    @Transactional
    public void notifyWithRules(UUID userId, String event, UUID projectId, String title, String message, String severity,
                                String entityType, Object entityId) {
        notify(userId, event, title, message, severity, entityType, entityId);
        boolean email = rules.applicable(event, projectId).stream().anyMatch(r -> !"IN_APP".equals(r.getChannel()));
        if (email) {
            users.findById(userId).map(User::getEmail).ifPresent(addr ->
                    enqueueEmail(addr, title, message, null, null, null, entityType, entityId == null ? null : entityId.toString(), 5));
        }
    }

    @Transactional
    public EmailOutbox enqueueEmail(String to, String subject, String body, String attachmentName, String attachmentType,
                                    byte[] attachment, String relatedType, String relatedId, int maxAttempts) {
        EmailOutbox e = new EmailOutbox();
        e.setRecipient(to);
        e.setSubject(subject.length() > 300 ? subject.substring(0, 300) : subject);
        e.setBody(body);
        e.setAttachmentName(attachmentName);
        e.setAttachmentType(attachmentType);
        e.setAttachmentContent(attachment);
        e.setMaxAttempts(maxAttempts);
        Instant now = Instant.now(clock);
        e.setNextAttemptAt(now);
        e.setCreatedAt(now);
        e.setRelatedType(relatedType);
        e.setRelatedId(relatedId);
        return outbox.save(e);
    }

    public String[] render(String templateCode, Map<String, String> vars, String defaultSubject, String defaultBody) {
        EmailTemplate t = templates.findById(templateCode).orElse(null);
        String subject = t == null ? defaultSubject : t.getSubject();
        String body = t == null ? defaultBody : t.getBody().replace("\\n", "\n");
        for (var e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            subject = subject.replace("{{" + e.getKey() + "}}", v);
            body = body.replace("{{" + e.getKey() + "}}", v);
        }
        return new String[]{subject, body};
    }

    @Transactional(readOnly = true)
    public long unread(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Notification> list(UUID userId, boolean unreadOnly, int page, int size) {
        var p = PageRequest.of(page, Math.min(size, 100));
        return unreadOnly ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, p)
                : notifications.findByUserIdOrderByCreatedAtDesc(userId, p);
    }
}
