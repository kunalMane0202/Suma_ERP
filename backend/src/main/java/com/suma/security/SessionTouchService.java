package com.suma.security;

import com.suma.auth.SessionActivityLog;
import com.suma.auth.SessionActivityLogRepository;
import com.suma.auth.UserSessionRepository;
import com.suma.common.ClientIpResolver;
import com.suma.ipmonitoring.SecurityEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Throttled update of session last-seen time and detection of IP changes within a session (informational, never blocking). */
@Service
public class SessionTouchService {
    private static final Duration THROTTLE = Duration.ofSeconds(60);
    private final UserSessionRepository sessions;
    private final SessionActivityLogRepository activity;
    private final ClientIpResolver ipResolver;
    private final SecurityEventRecorder recorder;
    private final Clock clock;
    private final Map<UUID, Instant> lastTouch = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastIp = new ConcurrentHashMap<>();

    public SessionTouchService(UserSessionRepository sessions, SessionActivityLogRepository activity,
                               ClientIpResolver ipResolver, SecurityEventRecorder recorder, Clock clock) {
        this.sessions = sessions;
        this.activity = activity;
        this.ipResolver = ipResolver;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional
    public void touch(UUID sessionId, UUID userId, HttpServletRequest request) {
        Instant now = Instant.now(clock);
        String ip = ipResolver.resolve(request);
        String knownIp = lastIp.get(sessionId);
        Instant last = lastTouch.get(sessionId);
        boolean ipChanged = knownIp != null && !knownIp.equals(ip);
        if (!ipChanged && last != null && last.plus(THROTTLE).isAfter(now)) return;
        lastTouch.put(sessionId, now);
        lastIp.put(sessionId, ip);
        sessions.findById(sessionId).ifPresent(s -> {
            String previous = s.getLastIp() != null ? s.getLastIp() : s.getIpAddress();
            s.setLastSeenAt(now);
            if (previous != null && !previous.equals(ip)) {
                activity.save(SessionActivityLog.of(sessionId, userId, "IP_CHANGED", ip, null, "from " + previous));
                recorder.ipActivity(ip, userId, "IP_CHANGE", "Session IP changed from " + previous);
            }
            s.setLastIp(ip);
        });
    }

    public void forget(UUID sessionId) {
        lastTouch.remove(sessionId);
        lastIp.remove(sessionId);
    }
}
