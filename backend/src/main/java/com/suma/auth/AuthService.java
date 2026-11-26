package com.suma.auth;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.common.ClientInfo;
import com.suma.config.SumaProperties;
import com.suma.ipmonitoring.IpRuleService;
import com.suma.security.JwtService;
import com.suma.security.SessionTouchService;
import com.suma.security.SessionValidator;
import com.suma.settings.SettingsService;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final SessionActivityLogRepository sessionActivity;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginActivityService loginActivity;
    private final LoginRateLimiter rateLimiter;
    private final IpRuleService ipRules;
    private final SettingsService settings;
    private final SessionValidator sessionValidator;
    private final SessionTouchService sessionTouch;
    private final List<SessionLifecycleListener> listeners;
    private final AuditService audit;
    private final ProfileService profiles;
    private final Duration refreshTtl;
    private final Clock clock;
    /** BCrypt hash of a random value; used to equalise timing for unknown usernames. */
    private final String dummyHash;

    public AuthService(UserRepository users, UserSessionRepository sessions, RefreshTokenRepository refreshTokens,
                       SessionActivityLogRepository sessionActivity, PasswordEncoder encoder, JwtService jwt,
                       LoginActivityService loginActivity, LoginRateLimiter rateLimiter, IpRuleService ipRules,
                       SettingsService settings, SessionValidator sessionValidator, SessionTouchService sessionTouch,
                       List<SessionLifecycleListener> listeners, AuditService audit, ProfileService profiles,
                       SumaProperties props, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.sessionActivity = sessionActivity;
        this.encoder = encoder;
        this.jwt = jwt;
        this.loginActivity = loginActivity;
        this.rateLimiter = rateLimiter;
        this.ipRules = ipRules;
        this.settings = settings;
        this.sessionValidator = sessionValidator;
        this.sessionTouch = sessionTouch;
        this.listeners = listeners;
        this.audit = audit;
        this.profiles = profiles;
        this.refreshTtl = Duration.ofHours(props.jwt().refreshTtlHours());
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public record LoginResult(String accessToken, String refreshToken, Duration refreshTtl, AuthDtos.MeResponse me) {}

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult login(AuthDtos.LoginRequest req, ClientInfo ci) {
        String username = req.username().trim();
        if (!rateLimiter.tryAcquire("login:" + ci.ip())) {
            loginActivity.recordFailure(username, null, "RATE_LIMITED", ci);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many login attempts. Please wait and try again.");
        }
        if (ipRules.evaluate(ci.ip()) != IpRuleService.Decision.ALLOW) {
            loginActivity.recordFailure(username, null, "BLOCKED_IP", ci);
            throw new ApiException(HttpStatus.FORBIDDEN, "IP_BLOCKED", "Login from this network address is not permitted");
        }
        Optional<User> found = users.findByUsernameIgnoreCase(username).or(() -> users.findByEmailIgnoreCase(username));
        if (found.isEmpty()) {
            encoder.matches(req.password(), dummyHash);
            loginActivity.recordFailure(username, null, "UNKNOWN_USER", ci);
            throw invalidCredentials();
        }
        User user = found.get();
        Instant now = Instant.now(clock);
        if ("INACTIVE".equals(user.getStatus())) {
            loginActivity.recordFailure(username, user.getId(), "ACCOUNT_DISABLED", ci);
            throw invalidCredentials();
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            loginActivity.recordFailure(username, user.getId(), "ACCOUNT_LOCKED", ci);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account temporarily locked after repeated failed logins. Try again later.");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failures);
            if (failures >= settings.getInt("security.max_failed_logins", 5)) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(settings.getInt("security.lock_minutes", 15))));
                user.setFailedLoginCount(0);
                audit.log("ACCOUNT_TEMP_LOCKED", "User", user.getId(), "Locked after " + failures + " failed logins from " + ci.ip());
            }
            users.save(user);
            loginActivity.recordFailure(username, user.getId(), "INVALID_CREDENTIALS", ci);
            throw invalidCredentials();
        }
        if ("LOCKED".equals(user.getStatus())) {
            loginActivity.recordFailure(username, user.getId(), "ACCOUNT_LOCKED", ci);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account is locked. Contact an administrator.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(ci.ip());

        UserSession s = new UserSession();
        s.setUserId(user.getId());
        s.setIpAddress(ci.ip());
        s.setIpVersion(ci.ipVersion());
        s.setLastIp(ci.ip());
        s.setUserAgent(ci.userAgent());
        s.setDevice(ci.device());
        s.setBrowser(ci.browser());
        s.setCreatedAt(now);
        s.setLastSeenAt(now);
        s.setExpiresAt(now.plus(refreshTtl));
        sessions.save(s);
        sessionActivity.save(SessionActivityLog.of(s.getId(), user.getId(), "CREATED", ci.ip(), user.getId(), null));
        loginActivity.recordSuccess(user.getId(), user.getUsername(), s.getId(), ci);
        for (SessionLifecycleListener l : listeners) l.onLogin(user, s, ci);

        String refresh = newRefreshToken(s, now);
        String access = jwt.issueAccessToken(user.getId(), user.getUsername(), user.roleCode(), s.getId());
        return new LoginResult(access, refresh, refreshTtl, profiles.me(user, s.getId()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String rawRefreshToken, ClientInfo ci) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw ApiException.unauthorized("NO_REFRESH_TOKEN", "Not signed in");
        Instant now = Instant.now(clock);
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid"));
        UserSession s = sessions.findById(token.getSessionId())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid"));
        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            // Reuse of a rotated token: possible theft. Revoke the whole session.
            if (s.isActive()) {
                endSession(s, "TERMINATED", "Refresh token reuse detected", null, "ADMIN_TERMINATED", now);
                sessionActivity.save(SessionActivityLog.of(s.getId(), s.getUserId(), "TOKEN_REUSE_DETECTED", ci.ip(), null, null));
                log.warn("Refresh token reuse detected for session {}", s.getId());
            }
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid");
        }
        if (!s.isActive()) throw ApiException.unauthorized("SESSION_ENDED", "Session has ended");
        if (token.getExpiresAt().isBefore(now) || s.getExpiresAt().isBefore(now)) {
            endSession(s, "EXPIRED", "Session expired", null, "SESSION_EXPIRY", now);
            throw ApiException.unauthorized("EXPIRED_SESSION", "Session expired");
        }
        User user = users.findById(s.getUserId()).orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session is no longer valid"));
        if (!user.isActive()) {
            endSession(s, "TERMINATED", "User not active", null, "ADMIN_TERMINATED", now);
            throw ApiException.unauthorized("ACCOUNT_DISABLED", "Account is not active");
        }
        token.setUsedAt(now);
        String refresh = newRefreshToken(s, now);
        s.setLastSeenAt(now);
        sessionActivity.save(SessionActivityLog.of(s.getId(), user.getId(), "REFRESHED", ci.ip(), user.getId(), null));
        String access = jwt.issueAccessToken(user.getId(), user.getUsername(), user.roleCode(), s.getId());
        return new LoginResult(access, refresh, Duration.between(now, s.getExpiresAt()), profiles.me(user, s.getId()));
    }

    /** Logout by refresh token (cookie) or by authenticated session id. Idempotent. */
    @Transactional
    public void logout(String rawRefreshToken, UUID sessionIdFromAccessToken, String source) {
        Instant now = Instant.now(clock);
        UUID sessionId = sessionIdFromAccessToken;
        if (sessionId == null && rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            sessionId = refreshTokens.findByTokenHash(hash(rawRefreshToken)).map(RefreshToken::getSessionId).orElse(null);
        }
        if (sessionId == null) return;
        sessions.findById(sessionId).filter(UserSession::isActive)
                .ifPresent(s -> endSession(s, "LOGGED_OUT", "User logout", s.getUserId(), source, now));
    }

    /** Ends a session, revokes its refresh tokens and closes attendance. */
    @Transactional
    public void endSession(UserSession s, String status, String reason, UUID actor, String attendanceSource, Instant at) {
        if (!s.isActive()) return;
        s.setStatus(status);
        s.setEndedAt(at);
        s.setEndedReason(reason);
        s.setEndedBy(actor);
        refreshTokens.revokeAllForSession(s.getId(), at);
        String action = switch (status) {
            case "LOGGED_OUT" -> "LOGGED_OUT";
            case "EXPIRED" -> "EXPIRED";
            default -> "TERMINATED";
        };
        sessionActivity.save(SessionActivityLog.of(s.getId(), s.getUserId(), action, s.getLastIp(), actor, reason));
        for (SessionLifecycleListener l : listeners) l.onSessionEnded(s.getId(), attendanceSource, at);
        sessionValidator.evict(s.getId());
        sessionTouch.forget(s.getId());
    }

    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, AuthDtos.ChangePasswordRequest req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) throw ApiException.badRequest("Current password is incorrect");
        u.setPasswordHash(encoder.encode(req.newPassword()));
        int ended = endOtherSessions(userId, currentSessionId, "Password changed", userId);
        audit.log("PASSWORD_CHANGED", "User", userId, ended > 0 ? "Signed out " + ended + " other session(s)" : null);
    }

    /** Ends every active session of a user except {@code keep} (may be null). Used after password changes and resets. */
    @Transactional
    public int endOtherSessions(UUID userId, UUID keep, String reason, UUID actor) {
        Instant now = Instant.now(clock);
        int n = 0;
        for (UserSession s : sessions.findByUserIdAndStatus(userId, "ACTIVE")) {
            if (s.getId().equals(keep)) continue;
            endSession(s, "TERMINATED", reason, actor, "ADMIN_TERMINATED", now);
            n++;
        }
        return n;
    }

    private String newRefreshToken(UserSession s, Instant now) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken t = new RefreshToken();
        t.setSessionId(s.getId());
        t.setUserId(s.getUserId());
        t.setTokenHash(hash(raw));
        t.setIssuedAt(now);
        t.setExpiresAt(s.getExpiresAt());
        refreshTokens.save(t);
        return raw;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalidCredentials() {
        // Same message for unknown user / wrong password / disabled account to avoid user enumeration.
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
    }
}
