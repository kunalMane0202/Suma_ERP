package com.suma.notification;

import com.suma.config.SumaProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Delivers queued emails via SMTP with exponential backoff. Each message is processed in its own transaction. */
@Service
public class EmailDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final EmailOutboxRepository outbox;
    private final JavaMailSender mailSender;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final String from;
    private final Clock clock;
    private volatile Duration baseBackoff = Duration.ofMinutes(15);

    public EmailDeliveryService(EmailOutboxRepository outbox, JavaMailSender mailSender, TransactionTemplate tx,
                                ApplicationEventPublisher events, SumaProperties props, Clock clock) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.tx = tx;
        this.events = events;
        this.from = props.mailFrom();
        this.clock = clock;
    }

    public void setBaseBackoff(Duration d) { this.baseBackoff = d; }

    /** Event published after each delivery attempt so related records (e.g. monthly report logs) can update their status. */
    public record EmailAttemptEvent(UUID emailId, String relatedType, String relatedId, String status, int attempts,
                                    String error, Instant nextAttemptAt, Instant sentAt) {}

    public int processDue(int batch) {
        List<UUID> ids = outbox.findDueIds(Instant.now(clock), PageRequest.of(0, batch));
        int sent = 0;
        for (UUID id : ids) {
            Boolean ok = tx.execute(s -> deliver(id));
            if (Boolean.TRUE.equals(ok)) sent++;
        }
        return ids.size();
    }

    private boolean deliver(UUID id) {
        EmailOutbox e = outbox.findById(id).orElse(null);
        if (e == null || "SENT".equals(e.getStatus()) || "FAILED".equals(e.getStatus())) return false;
        Instant now = Instant.now(clock);
        e.setAttempts(e.getAttempts() + 1);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            boolean multipart = e.getAttachmentContent() != null;
            MimeMessageHelper h = new MimeMessageHelper(msg, multipart, "UTF-8");
            h.setFrom(from);
            h.setTo(e.getRecipient());
            h.setSubject(e.getSubject());
            h.setText(e.getBody(), false);
            if (multipart) {
                h.addAttachment(e.getAttachmentName(), new ByteArrayResource(e.getAttachmentContent()), e.getAttachmentType());
            }
            mailSender.send(msg);
            e.setStatus("SENT");
            e.setSentAt(now);
            e.setLastError(null);
            if ("PasswordAction".equals(e.getRelatedType())) e.setBody("");
            log.info("Email {} sent to {}", e.getId(), mask(e.getRecipient()));
        } catch (Exception ex) {
            String err = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
            e.setLastError(err.length() > 1000 ? err.substring(0, 1000) : err);
            if (e.getAttempts() >= e.getMaxAttempts()) {
                e.setStatus("FAILED");
            } else {
                e.setStatus("RETRYING");
                long factor = 1L << Math.min(e.getAttempts() - 1, 6);
                e.setNextAttemptAt(now.plus(baseBackoff.multipliedBy(factor)));
            }
            log.warn("Email {} delivery attempt {} failed: {}", e.getId(), e.getAttempts(), e.getLastError());
        }
        events.publishEvent(new EmailAttemptEvent(e.getId(), e.getRelatedType(), e.getRelatedId(), e.getStatus(), e.getAttempts(),
                e.getLastError(), e.getNextAttemptAt(), e.getSentAt()));
        return "SENT".equals(e.getStatus());
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
