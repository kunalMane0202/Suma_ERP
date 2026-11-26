package com.suma.auth;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.notification.NotificationService;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import com.suma.user.User;
import com.suma.user.UserAdminService;
import com.suma.user.UserDtos;
import com.suma.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountAccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final String GENERIC_REQUEST_MESSAGE = "If the request is eligible, you will receive further instructions by email.";

    private final RegistrationRequestRepository requests;
    private final PasswordActionTokenRepository tokens;
    private final UserRepository users;
    private final UserAdminService userAdmin;
    private final NotificationService notifications;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter rateLimiter;
    private final AuthService auth;
    private final AuditService audit;
    private final Clock clock;
    private final String publicUrl;

    public AccountAccessService(RegistrationRequestRepository requests, PasswordActionTokenRepository tokens,
                                UserRepository users, UserAdminService userAdmin, NotificationService notifications,
                                PasswordEncoder encoder, LoginRateLimiter rateLimiter, AuthService auth,
                                AuditService audit, Clock clock,
                                @Value("${suma.public-url:http://localhost:5173}") String publicUrl) {
        this.requests = requests;
        this.tokens = tokens;
        this.users = users;
        this.userAdmin = userAdmin;
        this.notifications = notifications;
        this.encoder = encoder;
        this.rateLimiter = rateLimiter;
        this.auth = auth;
        this.audit = audit;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void requestRegistration(AccountAccessDtos.RegistrationRequest req, String ip) {
        rateLimit("account-request:", ip);
        String email = normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email) || requests.existsByEmailIgnoreCaseAndStatus(email, "PENDING")) return;

        RegistrationRequestEntity request = new RegistrationRequestEntity();
        request.setFullName(req.fullName().trim());
        request.setEmail(email);
        request.setCreatedAt(Instant.now(clock));
        requests.save(request);
    }

    @Transactional
    public void requestPasswordReset(String emailAddress, String ip) {
        rateLimit("password-reset:", ip);
        users.findByEmailIgnoreCase(normalizeEmail(emailAddress))
                .filter(User::isActive)
                .ifPresent(user -> sendToken(user, "RESET"));
    }

    @Transactional
    public void completePasswordAction(AccountAccessDtos.PasswordCompletionRequest req, String ip) {
        rateLimit("password-complete:", ip);
        Instant now = Instant.now(clock);
        PasswordActionToken token = tokens.findForUpdateByTokenHash(AuthService.hash(req.token()))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> ApiException.badRequest("This password link is invalid or expired"));
        User user = users.findById(token.getUserId()).orElseThrow(() -> ApiException.badRequest("This password link is invalid or expired"));

        if ("SETUP".equals(token.getPurpose())) {
            if (!"INACTIVE".equals(user.getStatus())) throw ApiException.badRequest("This password link is invalid or expired");
            user.setStatus("ACTIVE");
            audit.log("ACCOUNT_ACTIVATED", "User", user.getId(), "Initial password set");
        } else if ("RESET".equals(token.getPurpose())) {
            if (!user.isActive()) throw ApiException.badRequest("This password link is invalid or expired");
            int ended = auth.endOtherSessions(user.getId(), null, "Password reset by user", null);
            audit.log("PASSWORD_RESET", "User", user.getId(), ended > 0 ? "Signed out " + ended + " session(s)" : null);
        } else {
            throw ApiException.badRequest("This password link is invalid or expired");
        }

        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        token.setUsedAt(now);
        tokens.invalidateUnused(user.getId(), token.getPurpose(), now);
    }

    @Transactional(readOnly = true)
    public List<AccountAccessDtos.RegistrationRequestSummary> pendingRequests() {
        requireAdmin();
        return requests.findTop100ByStatusOrderByCreatedAtAsc("PENDING").stream()
                .map(r -> new AccountAccessDtos.RegistrationRequestSummary(r.getId(), r.getFullName(), r.getEmail(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void approve(UUID id, AccountAccessDtos.ApproveRequest req) {
        AuthUser actor = requireAdmin();
        RegistrationRequestEntity request = requests.findById(id).orElseThrow(() -> ApiException.notFound("Account request"));
        if (!"PENDING".equals(request.getStatus())) throw ApiException.conflict("Account request has already been reviewed");

        String temporaryPassword = "Aa1" + randomToken();
        UserDtos.UserRequest create = new UserDtos.UserRequest(req.employeeCode(), req.username(), request.getEmail(),
                request.getFullName(), null, null, req.role(), null, Set.of(), null, null, temporaryPassword, "INACTIVE");
        UserDtos.UserResponse created = userAdmin.create(create);
        User user = users.findById(created.id()).orElseThrow(() -> ApiException.notFound("User"));

        request.setStatus("APPROVED");
        request.setReviewedAt(Instant.now(clock));
        request.setReviewedBy(actor.id());
        request.setCreatedUserId(user.getId());
        sendToken(user, "SETUP");
        audit.log("ACCOUNT_REQUEST_APPROVED", "AccountRegistrationRequest", id, "Created user " + user.getId());
    }

    @Transactional
    public void reject(UUID id) {
        AuthUser actor = requireAdmin();
        RegistrationRequestEntity request = requests.findById(id).orElseThrow(() -> ApiException.notFound("Account request"));
        if (!"PENDING".equals(request.getStatus())) throw ApiException.conflict("Account request has already been reviewed");
        request.setStatus("REJECTED");
        request.setReviewedAt(Instant.now(clock));
        request.setReviewedBy(actor.id());
        audit.log("ACCOUNT_REQUEST_REJECTED", "AccountRegistrationRequest", id, null);
    }

    private void sendToken(User user, String purpose) {
        Instant now = Instant.now(clock);
        tokens.invalidateUnused(user.getId(), purpose, now);
        String rawToken = randomToken();
        PasswordActionToken token = new PasswordActionToken();
        token.setUserId(user.getId());
        token.setTokenHash(AuthService.hash(rawToken));
        token.setPurpose(purpose);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokens.save(token);

        String link = publicUrl + "/set-password#token=" + rawToken;
        String subject = "SETUP".equals(purpose) ? "Set up your SUMA account" : "Reset your SUMA password";
        String action = "SETUP".equals(purpose) ? "set up your account" : "reset your password";
        String body = "Hello " + user.getFullName() + ",\n\nUse the following link to " + action + ":\n" + link
                + "\n\nThis single-use link expires in 60 minutes. If you did not request this, you can ignore this email.";
        notifications.enqueueEmail(user.getEmail(), subject, body, null, null, null, "PasswordAction", token.getId().toString(), 5);
    }

    private AuthUser requireAdmin() {
        AuthUser actor = CurrentUser.orNull();
        if (actor == null || !actor.isAdmin()) throw ApiException.forbidden("Only administrators can review account requests");
        return actor;
    }

    private void rateLimit(String prefix, String ip) {
        if (!rateLimiter.tryAcquire(prefix + ip)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests. Please wait and try again.");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
