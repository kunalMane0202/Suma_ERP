package com.suma.ipmonitoring;

import com.suma.common.ClientInfo;
import com.suma.common.ClientIpResolver;
import com.suma.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists security events. Uses REQUIRES_NEW so failed-login evidence survives even if the surrounding
 * request transaction is rolled back. Never records passwords, tokens or request bodies.
 */
@Service
public class SecurityEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(SecurityEventRecorder.class);
    private final AnonymousAccessLogRepository anonymous;
    private final IpActivityLogRepository ipActivity;
    private final ClientIpResolver ipResolver;
    private final Clock clock;

    public SecurityEventRecorder(AnonymousAccessLogRepository anonymous, IpActivityLogRepository ipActivity,
                                 ClientIpResolver ipResolver, Clock clock) {
        this.anonymous = anonymous;
        this.ipActivity = ipActivity;
        this.ipResolver = ipResolver;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anonymousAccess(HttpServletRequest request, String category, int status, UUID userId, String username) {
        try {
            ClientInfo ci = ipResolver.clientInfo(request);
            AnonymousAccessLog l = new AnonymousAccessLog();
            l.setIpAddress(ci.ip());
            l.setIpVersion(ci.ipVersion());
            l.setHttpMethod(request.getMethod());
            String uri = request.getRequestURI();
            l.setEndpoint(uri.length() > 500 ? uri.substring(0, 500) : uri); // path only: query strings may carry secrets
            l.setCategory(category);
            l.setStatusCode(status);
            l.setUserId(userId);
            l.setUsername(truncate(username, 120));
            l.setUserAgent(ci.userAgent());
            l.setCorrelationId(CorrelationIdFilter.current());
            l.setCreatedAt(Instant.now(clock));
            anonymous.save(l);
            String type = switch (category) {
                case "ACCESS_DENIED" -> "ACCESS_DENIED";
                case "BLOCKED_IP" -> "BLOCKED";
                default -> "ANONYMOUS_ACCESS";
            };
            saveIp(ci.ip(), ci.ipVersion(), userId, type, category + " " + request.getMethod() + " " + l.getEndpoint());
        } catch (Exception e) {
            log.warn("Could not record anonymous access event: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ipActivity(String ip, UUID userId, String type, String details) {
        saveIp(ip, ClientIpResolver.version(ip), userId, type, details);
    }

    private void saveIp(String ip, short version, UUID userId, String type, String details) {
        IpActivityLog l = new IpActivityLog();
        l.setIpAddress(ip);
        l.setIpVersion(version);
        l.setUserId(userId);
        l.setActivityType(type);
        l.setDetails(truncate(details, 500));
        l.setCreatedAt(Instant.now(clock));
        ipActivity.save(l);
    }

    static String truncate(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
