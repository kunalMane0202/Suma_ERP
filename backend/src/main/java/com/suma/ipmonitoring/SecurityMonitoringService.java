package com.suma.ipmonitoring;

import com.suma.audit.AuditService;
import com.suma.auth.AuthService;
import com.suma.auth.SessionActivityLogRepository;
import com.suma.auth.UserSession;
import com.suma.auth.UserSessionRepository;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.AuthUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class SecurityMonitoringService {
    private final LoginActivityLogRepository logins;
    private final AnonymousAccessLogRepository anonymous;
    private final IpActivityLogRepository ipActivity;
    private final UserSessionRepository sessions;
    private final SessionActivityLogRepository sessionActivity;
    private final UserRepository users;
    private final AuthService auth;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SecurityMonitoringService(LoginActivityLogRepository logins, AnonymousAccessLogRepository anonymous,
                                     IpActivityLogRepository ipActivity, UserSessionRepository sessions,
                                     SessionActivityLogRepository sessionActivity, UserRepository users, AuthService auth,
                                     AuditService audit, JdbcTemplate jdbc, Clock clock) {
        this.logins = logins;
        this.anonymous = anonymous;
        this.ipActivity = ipActivity;
        this.sessions = sessions;
        this.sessionActivity = sessionActivity;
        this.users = users;
        this.auth = auth;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Filter(String ip, UUID userId, String username, Boolean success, String category, Instant from, Instant to) {}

    public record LoginDto(UUID id, UUID userId, String userName, String usernameAttempted, boolean success, String failureCategory,
                           String ipAddress, int ipVersion, UUID sessionId, String device, String browser, String userAgent,
                           String authMethod, int attemptCount, boolean suspicious, String suspiciousReasons, String geoLocation,
                           String correlationId, Instant createdAt) {}

    public record SessionDto(UUID id, UUID userId, String userName, String employeeCode, String role, String status, String ipAddress,
                             String lastIp, boolean ipChanged, String device, String browser, Instant createdAt, Instant lastSeenAt,
                             Instant expiresAt, Instant endedAt, String endedReason) {}

    public record Dashboard(Instant since, long successfulLogins, long failedLogins, long unknownUserAttempts,
                            long unauthenticatedRequests, long accessDenied, long blockedRequests, long uniqueIps,
                            long suspiciousEvents, long activeSessions, long activeUsers, long ipChanges,
                            List<LoginActivityLogRepository.IpAttemptCount> repeatedFailures, List<Map<String, Object>> trend,
                            List<Map<String, Object>> failureCategories, List<Map<String, Object>> topIps) {}

    @Transactional(readOnly = true)
    public Dashboard dashboard(int hours) {
        Instant since = Instant.now(clock).minus(Duration.ofHours(Math.max(1, Math.min(hours, 24 * 90))));
        Timestamp ts = Timestamp.from(since);
        List<Map<String, Object>> trend = jdbc.queryForList("""
                select date_trunc(case when ? <= 48 then 'hour' else 'day' end, created_at) as bucket,
                  count(*) filter (where success) as success, count(*) filter (where not success) as failed
                from login_activity_logs where created_at > ? group by 1 order by 1
                """, hours, ts);
        List<Map<String, Object>> categories = jdbc.queryForList("""
                select failure_category as category, count(*) as count from login_activity_logs
                where not success and created_at > ? group by 1 order by 2 desc
                """, ts);
        List<Map<String, Object>> topIps = jdbc.queryForList("""
                select ip_address as ip, count(*) as events, count(*) filter (where activity_type in ('LOGIN_FAILURE','UNKNOWN_USER')) as failures,
                  count(*) filter (where activity_type = 'ANONYMOUS_ACCESS') as anonymous, max(created_at) as last_seen
                from ip_activity_logs where created_at > ? group by ip_address order by events desc limit 10
                """, ts);
        return new Dashboard(since, logins.countBySuccessAndCreatedAtAfter(true, since), logins.countBySuccessAndCreatedAtAfter(false, since),
                logins.countByFailureCategoryAndCreatedAtAfter("UNKNOWN_USER", since), anonymous.countByCreatedAtAfter(since)
                - anonymous.countByCategoryAndCreatedAtAfter("ACCESS_DENIED", since) - anonymous.countByCategoryAndCreatedAtAfter("BLOCKED_IP", since),
                anonymous.countByCategoryAndCreatedAtAfter("ACCESS_DENIED", since), anonymous.countByCategoryAndCreatedAtAfter("BLOCKED_IP", since),
                logins.countDistinctIps(since), logins.countBySuspiciousTrueAndCreatedAtAfter(since), sessions.countByStatus("ACTIVE"),
                sessions.countDistinctActiveUsers(), ipActivity.countByActivityTypeAndCreatedAtAfter("IP_CHANGE", since),
                logins.repeatedFailures(since, 3), trend, categories, topIps);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginDto> loginActivity(Filter f, boolean suspiciousOnly, int page, int size) {
        Specification<LoginActivityLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.ip() != null) ps.add(cb.like(root.get("ipAddress"), f.ip() + "%"));
            if (f.userId() != null) ps.add(cb.equal(root.get("userId"), f.userId()));
            if (f.username() != null) ps.add(cb.like(cb.lower(root.get("usernameAttempted")), "%" + f.username().toLowerCase() + "%"));
            if (f.success() != null) ps.add(cb.equal(root.get("success"), f.success()));
            if (f.category() != null) ps.add(cb.equal(root.get("failureCategory"), f.category()));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThan(root.get("createdAt"), f.to()));
            if (suspiciousOnly) ps.add(cb.isTrue(root.get("suspicious")));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<LoginActivityLog> p = logins.findAll(spec, PageRequest.of(page, Math.min(size, 500), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, String> names = names(p.getContent().stream().map(LoginActivityLog::getUserId).filter(Objects::nonNull).toList());
        return PageResponse.of(p, l -> new LoginDto(l.getId(), l.getUserId(), names.get(l.getUserId()), l.getUsernameAttempted(), l.isSuccess(),
                l.getFailureCategory(), l.getIpAddress(), l.getIpVersion(), l.getSessionId(), l.getDevice(), l.getBrowser(), l.getUserAgent(),
                l.getAuthMethod(), l.getAttemptCount(), l.isSuspicious(), l.getSuspiciousReasons(), l.getGeoLocation(), l.getCorrelationId(), l.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AnonymousAccessLog> anonymousAccess(Filter f, int page, int size) {
        Specification<AnonymousAccessLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.ip() != null) ps.add(cb.like(root.get("ipAddress"), f.ip() + "%"));
            if (f.category() != null) ps.add(cb.equal(root.get("category"), f.category()));
            if (f.username() != null) ps.add(cb.like(cb.lower(root.get("username")), "%" + f.username().toLowerCase() + "%"));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThan(root.get("createdAt"), f.to()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(anonymous.findAll(spec, PageRequest.of(page, Math.min(size, 500), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Transactional(readOnly = true)
    public PageResponse<IpActivityLog> ipActivity(Filter f, String type, int page, int size) {
        Specification<IpActivityLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.ip() != null) ps.add(cb.like(root.get("ipAddress"), f.ip() + "%"));
            if (f.userId() != null) ps.add(cb.equal(root.get("userId"), f.userId()));
            if (type != null) ps.add(cb.equal(root.get("activityType"), type));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThan(root.get("createdAt"), f.to()));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(ipActivity.findAll(spec, PageRequest.of(page, Math.min(size, 500), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionDto> sessions(String status, UUID userId, int page, int size) {
        String st = status == null ? "ACTIVE" : status;
        Page<UserSession> p = userId == null ? sessions.findByStatusOrderByCreatedAtDesc(st, PageRequest.of(page, Math.min(size, 500)))
                : new org.springframework.data.domain.PageImpl<>(sessions.findByUserIdAndStatus(userId, st));
        Map<UUID, User> people = new HashMap<>();
        users.findByIdIn(p.getContent().stream().map(UserSession::getUserId).distinct().toList()).forEach(u -> people.put(u.getId(), u));
        return PageResponse.of(p, s -> {
            User u = people.get(s.getUserId());
            return new SessionDto(s.getId(), s.getUserId(), u == null ? null : u.getFullName(), u == null ? null : u.getEmployeeCode(),
                    u == null ? null : u.roleCode(), s.getStatus(), s.getIpAddress(), s.getLastIp(),
                    s.getLastIp() != null && !s.getLastIp().equals(s.getIpAddress()), s.getDevice(), s.getBrowser(), s.getCreatedAt(),
                    s.getLastSeenAt(), s.getExpiresAt(), s.getEndedAt(), s.getEndedReason());
        });
    }

    @Transactional(readOnly = true)
    public List<com.suma.auth.SessionActivityLog> sessionHistory(UUID sessionId) {
        return sessionActivity.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void terminate(AuthUser admin, UUID sessionId, String reason) {
        UserSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        if (!s.isActive()) throw ApiException.conflict("Session is not active");
        if (s.getId().equals(admin.sessionId())) throw ApiException.badRequest("Use sign out to end your own current session");
        String r = reason == null || reason.isBlank() ? "Terminated by administrator" : reason;
        auth.endSession(s, "TERMINATED", r, admin.id(), "ADMIN_TERMINATED", Instant.now(clock));
        audit.log("SESSION_TERMINATED", "UserSession", sessionId, "user=" + s.getUserId() + " ip=" + s.getLastIp() + " reason=" + r);
    }

    @Transactional
    public int terminateAllForUser(AuthUser admin, UUID userId, String reason) {
        int n = 0;
        for (UserSession s : sessions.findByUserIdAndStatus(userId, "ACTIVE")) {
            if (s.getId().equals(admin.sessionId())) continue;
            auth.endSession(s, "TERMINATED", reason == null ? "Terminated by administrator" : reason, admin.id(), "ADMIN_TERMINATED", Instant.now(clock));
            n++;
        }
        audit.log("USER_SESSIONS_TERMINATED", "User", userId, n + " session(s)");
        return n;
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> m = new HashMap<>();
        if (!ids.isEmpty()) users.findByIdIn(new HashSet<>(ids)).forEach(u -> m.put(u.getId(), u.getFullName()));
        return m;
    }
}
