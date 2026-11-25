package com.suma.auth;

import com.suma.common.ClientInfo;
import com.suma.common.CorrelationIdFilter;
import com.suma.ipmonitoring.IpActivityLog;
import com.suma.ipmonitoring.IpActivityLogRepository;
import com.suma.ipmonitoring.LoginActivityLog;
import com.suma.ipmonitoring.LoginActivityLogRepository;
import com.suma.settings.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Records successful and failed logins with source IP and computes suspicious-login indicators. */
@Service
public class LoginActivityService {
    private final LoginActivityLogRepository logs;
    private final IpActivityLogRepository ipLogs;
    private final UserSessionRepository sessions;
    private final SettingsService settings;
    private final Clock clock;

    public LoginActivityService(LoginActivityLogRepository logs, IpActivityLogRepository ipLogs,
                                UserSessionRepository sessions, SettingsService settings, Clock clock) {
        this.logs = logs;
        this.ipLogs = ipLogs;
        this.sessions = sessions;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginActivityLog recordFailure(String usernameAttempted, UUID userId, String category, ClientInfo ci) {
        Instant now = Instant.now(clock);
        Instant windowStart = now.minus(Duration.ofMinutes(settings.getInt("security.failed_attempt_window_minutes", 15)));
        int threshold = settings.getInt("security.failed_attempt_alert_threshold", 5);
        long ipFailures = logs.countByIpAddressAndSuccessFalseAndCreatedAtAfter(ci.ip(), windowStart) + 1;
        long userFailures = usernameAttempted == null ? 0
                : logs.countByUsernameAttemptedIgnoreCaseAndSuccessFalseAndCreatedAtAfter(usernameAttempted, windowStart) + 1;
        List<String> reasons = new ArrayList<>();
        if (ipFailures >= threshold) reasons.add("REPEATED_FAILURES_FROM_IP");
        if (userFailures >= threshold) reasons.add("REPEATED_FAILURES_FOR_USER");
        if ("UNKNOWN_USER".equals(category)) reasons.add("UNKNOWN_USERNAME");

        LoginActivityLog l = base(ci, now);
        l.setUserId(userId);
        l.setUsernameAttempted(truncate(usernameAttempted));
        l.setSuccess(false);
        l.setFailureCategory(category);
        l.setAttemptCount((int) Math.max(ipFailures, userFailures));
        l.setSuspicious(reasons.stream().anyMatch(r -> r.startsWith("REPEATED")));
        l.setSuspiciousReasons(reasons.isEmpty() ? null : String.join(",", reasons));
        logs.save(l);
        ip(ci, userId, "UNKNOWN_USER".equals(category) ? "UNKNOWN_USER" : "LOGIN_FAILURE",
                category + (usernameAttempted != null ? " username=" + truncate(usernameAttempted) : ""), now);
        if (l.isSuspicious()) ip(ci, userId, "SUSPICIOUS", String.join(",", reasons), now);
        return l;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LoginActivityLog recordSuccess(UUID userId, String username, UUID sessionId, ClientInfo ci) {
        Instant now = Instant.now(clock);
        Instant windowStart = now.minus(Duration.ofMinutes(settings.getInt("security.failed_attempt_window_minutes", 15)));
        int threshold = settings.getInt("security.failed_attempt_alert_threshold", 5);
        List<String> reasons = new ArrayList<>();
        boolean suspicious = false;
        var previous = logs.findTop5ByUserIdAndSuccessTrueOrderByCreatedAtDesc(userId);
        if (!previous.isEmpty() && previous.stream().noneMatch(p -> ci.ip().equals(p.getIpAddress()))) {
            reasons.add("NEW_IP"); // informational only; never blocks
        }
        if (logs.countByUsernameAttemptedIgnoreCaseAndSuccessFalseAndCreatedAtAfter(username, windowStart) >= threshold) {
            reasons.add("SUCCESS_AFTER_REPEATED_FAILURES");
            suspicious = true;
        }
        boolean otherIpActive = sessions.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .anyMatch(s -> !s.getId().equals(sessionId) && s.getIpAddress() != null && !s.getIpAddress().equals(ci.ip()));
        if (otherIpActive) reasons.add("CONCURRENT_SESSION_DIFFERENT_IP");

        LoginActivityLog l = base(ci, now);
        l.setUserId(userId);
        l.setUsernameAttempted(truncate(username));
        l.setSuccess(true);
        l.setSessionId(sessionId);
        l.setSuspicious(suspicious);
        l.setSuspiciousReasons(reasons.isEmpty() ? null : String.join(",", reasons));
        logs.save(l);
        ip(ci, userId, "LOGIN_SUCCESS", "session " + sessionId, now);
        if (reasons.contains("NEW_IP")) ip(ci, userId, "IP_CHANGE", "Login from an IP not seen in recent logins", now);
        if (suspicious) ip(ci, userId, "SUSPICIOUS", String.join(",", reasons), now);
        return l;
    }

    private LoginActivityLog base(ClientInfo ci, Instant now) {
        LoginActivityLog l = new LoginActivityLog();
        l.setIpAddress(ci.ip());
        l.setIpVersion(ci.ipVersion());
        l.setUserAgent(ci.userAgent());
        l.setDevice(ci.device());
        l.setBrowser(ci.browser());
        l.setCorrelationId(CorrelationIdFilter.current());
        l.setCreatedAt(now);
        return l;
    }

    private void ip(ClientInfo ci, UUID userId, String type, String details, Instant now) {
        IpActivityLog l = new IpActivityLog();
        l.setIpAddress(ci.ip());
        l.setIpVersion(ci.ipVersion());
        l.setUserId(userId);
        l.setActivityType(type);
        l.setDetails(details.length() > 500 ? details.substring(0, 500) : details);
        l.setCreatedAt(now);
        ipLogs.save(l);
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 120 ? s.substring(0, 120) : s;
    }
}
