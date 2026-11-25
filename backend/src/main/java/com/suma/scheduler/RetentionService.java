package com.suma.scheduler;

import com.suma.audit.AuditLogRepository;
import com.suma.ipmonitoring.AnonymousAccessLogRepository;
import com.suma.ipmonitoring.IpActivityLogRepository;
import com.suma.ipmonitoring.LoginActivityLogRepository;
import com.suma.notification.NotificationRepository;
import com.suma.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Applies configurable retention to security, audit and notification data. */
@Service
public class RetentionService {
    private final LoginActivityLogRepository logins;
    private final AnonymousAccessLogRepository anonymous;
    private final IpActivityLogRepository ip;
    private final AuditLogRepository audit;
    private final NotificationRepository notifications;
    private final SettingsService settings;
    private final Clock clock;

    public RetentionService(LoginActivityLogRepository logins, AnonymousAccessLogRepository anonymous, IpActivityLogRepository ip,
                            AuditLogRepository audit, NotificationRepository notifications, SettingsService settings, Clock clock) {
        this.logins = logins;
        this.anonymous = anonymous;
        this.ip = ip;
        this.audit = audit;
        this.notifications = notifications;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public int cleanup() {
        Instant now = Instant.now(clock);
        Instant sec = now.minus(Duration.ofDays(settings.getInt("retention.security_log_days", 365)));
        Instant aud = now.minus(Duration.ofDays(settings.getInt("retention.audit_log_days", 1095)));
        Instant ntf = now.minus(Duration.ofDays(settings.getInt("retention.notification_days", 180)));
        return logins.deleteOlderThan(sec) + anonymous.deleteOlderThan(sec) + ip.deleteOlderThan(sec)
                + audit.deleteOlderThan(aud) + notifications.deleteOlderThan(ntf);
    }
}
