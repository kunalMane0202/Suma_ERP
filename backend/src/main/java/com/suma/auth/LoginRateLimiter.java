package com.suma.auth;

import com.suma.config.SumaProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory sliding-window limiter for authentication endpoints, keyed by client IP. */
@Component
public class LoginRateLimiter {
    private final int max;
    private final int windowSeconds;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public LoginRateLimiter(SumaProperties props, Clock clock) {
        this.max = props.loginRateLimit().maxAttempts();
        this.windowSeconds = props.loginRateLimit().windowSeconds();
        this.clock = clock;
    }

    /** @return true if the request is allowed */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }

    public void reset() { hits.clear(); }
}
