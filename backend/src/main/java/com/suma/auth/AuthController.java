package com.suma.auth;

import com.suma.common.ClientIpResolver;
import com.suma.config.SumaProperties;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import com.suma.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    static final String REFRESH_COOKIE = "suma_rt";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private final AuthService auth;
    private final ClientIpResolver ipResolver;
    private final ProfileService profiles;
    private final UserRepository users;
    private final boolean secureCookie;
    private final long accessTtlSeconds;

    public AuthController(AuthService auth, ClientIpResolver ipResolver, ProfileService profiles, UserRepository users,
                          SumaProperties props) {
        this.auth = auth;
        this.ipResolver = ipResolver;
        this.profiles = profiles;
        this.users = users;
        this.secureCookie = props.cookieSecure();
        this.accessTtlSeconds = props.jwt().accessTtlMinutes() * 60;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with username/email and password", description = "Returns an access token; the refresh token is set as an HttpOnly cookie. Every attempt is recorded with source IP.")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        AuthService.LoginResult r = auth.login(req, ipResolver.clientInfo(http));
        return withCookie(r);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and obtain a new access token")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String rt, HttpServletRequest http) {
        return withCookie(auth.refresh(rt, ipResolver.clientInfo(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session (idempotent). source=BROWSER_UNLOAD for best-effort tab-close beacons.")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String rt,
                                       @RequestParam(name = "source", required = false) String source) {
        AuthUser u = CurrentUser.orNull();
        String src = "BROWSER_UNLOAD".equals(source) ? "BROWSER_UNLOAD" : "USER_LOGOUT";
        auth.logout(rt, u == null ? null : u.sessionId(), src);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public AuthDtos.MeResponse me() {
        AuthUser u = CurrentUser.get();
        User user = users.findById(u.id()).orElseThrow(() -> ApiException.notFound("User"));
        return profiles.me(user, u.sessionId());
    }

    @PutMapping("/me/preferences")
    @Transactional
    public AuthDtos.MeResponse preferences(@Valid @RequestBody AuthDtos.PreferencesRequest req) {
        AuthUser u = CurrentUser.get();
        User user = users.findById(u.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (req.themePreference() != null) user.setThemePreference(req.themePreference());
        if (req.timezone() != null) {
            try {
                java.time.ZoneId.of(req.timezone());
            } catch (Exception e) {
                throw ApiException.badRequest("Unknown timezone");
            }
            user.setTimezone(req.timezone());
        }
        return profiles.me(user, u.sessionId());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest req) {
        auth.changePassword(CurrentUser.get().id(), CurrentUser.get().sessionId(), req);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthDtos.TokenResponse> withCookie(AuthService.LoginResult r) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(r.refreshToken(), r.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new AuthDtos.TokenResponse(r.accessToken(), accessTtlSeconds, r.me()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(maxAge).build();
    }
}
