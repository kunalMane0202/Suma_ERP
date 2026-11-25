package com.suma.security;

import com.suma.auth.UserSession;
import com.suma.auth.UserSessionRepository;
import com.suma.user.Permission;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates that the session referenced by an access token is still ACTIVE and resolves current permissions.
 * Results are cached briefly; terminating a session evicts it so revocation is effective immediately on this node.
 */
@Component
public class SessionValidator {
    private static final Duration CACHE_TTL = Duration.ofSeconds(20);
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public SessionValidator(UserSessionRepository sessions, UserRepository users, Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
    }

    public enum Result { OK, REVOKED, EXPIRED, USER_INACTIVE }

    public record Validation(Result result, AuthUser user) {}

    private record Cached(Validation v, Instant at) {}

    @Transactional(readOnly = true)
    public Validation validate(UUID sessionId, UUID userId) {
        Instant now = Instant.now(clock);
        Cached c = cache.get(sessionId);
        if (c != null && c.at.plus(CACHE_TTL).isAfter(now)) return c.v;
        Validation v = load(sessionId, userId, now);
        cache.put(sessionId, new Cached(v, now));
        return v;
    }

    private Validation load(UUID sessionId, UUID userId, Instant now) {
        Optional<UserSession> s = sessions.findById(sessionId);
        if (s.isEmpty() || !s.get().getUserId().equals(userId)) return new Validation(Result.REVOKED, null);
        UserSession session = s.get();
        if ("EXPIRED".equals(session.getStatus()) || session.getExpiresAt().isBefore(now)) return new Validation(Result.EXPIRED, null);
        if (!session.isActive()) return new Validation(Result.REVOKED, null);
        User u = users.findById(userId).orElse(null);
        if (u == null || !u.isActive()) return new Validation(Result.USER_INACTIVE, null);
        Set<String> perms = u.getRole().getPermissions().stream().map(Permission::getCode).collect(Collectors.toUnmodifiableSet());
        return new Validation(Result.OK, new AuthUser(u.getId(), u.getUsername(), u.getRole().getCode(), sessionId, perms));
    }

    public void evict(UUID sessionId) { cache.remove(sessionId); }

    public void evictAll() { cache.clear(); }
}
