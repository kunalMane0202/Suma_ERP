package com.suma.ipmonitoring;

import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security")
@Tag(name = "Login Security & IP Activity Monitoring")
public class SecurityController {
    private final SecurityMonitoringService service;
    private final IpRuleService ipRules;

    public SecurityController(SecurityMonitoringService service, IpRuleService ipRules) {
        this.service = service;
        this.ipRules = ipRules;
    }

    private static SecurityMonitoringService.Filter filter(String ip, UUID userId, String username, Boolean success, String category, Instant from, Instant to) {
        return new SecurityMonitoringService.Filter(blank(ip), userId, blank(username), success, blank(category), from, to);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public SecurityMonitoringService.Dashboard dashboard(@RequestParam(defaultValue = "24") int hours) {
        return service.dashboard(hours);
    }

    @GetMapping("/login-activity")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<SecurityMonitoringService.LoginDto> loginActivity(@RequestParam(required = false) String ip, @RequestParam(required = false) UUID userId,
                                                                          @RequestParam(required = false) String username, @RequestParam(required = false) Boolean success,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.loginActivity(filter(ip, userId, username, success, null, from, to), false, page, size);
    }

    @GetMapping("/failed-logins")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<SecurityMonitoringService.LoginDto> failedLogins(@RequestParam(required = false) String ip, @RequestParam(required = false) String username,
                                                                         @RequestParam(required = false) String category,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.loginActivity(filter(ip, null, username, false, category, from, to), false, page, size);
    }

    @GetMapping("/suspicious-activity")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<SecurityMonitoringService.LoginDto> suspicious(@RequestParam(required = false) String ip,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.loginActivity(filter(ip, null, null, null, null, from, to), true, page, size);
    }

    @GetMapping("/anonymous-access")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<AnonymousAccessLog> anonymous(@RequestParam(required = false) String ip, @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String username,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.anonymousAccess(filter(ip, null, username, null, category, from, to), page, size);
    }

    @GetMapping("/ip-activity")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<IpActivityLog> ipActivity(@RequestParam(required = false) String ip, @RequestParam(required = false) UUID userId,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.ipActivity(filter(ip, userId, null, null, null, from, to), blank(type), page, size);
    }

    @GetMapping("/active-sessions")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public PageResponse<SecurityMonitoringService.SessionDto> sessions(@RequestParam(required = false) String status, @RequestParam(required = false) UUID userId,
                                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.sessions(blank(status), userId, page, size);
    }

    @GetMapping("/sessions/{id}/history")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public List<com.suma.auth.SessionActivityLog> sessionHistory(@PathVariable UUID id) {
        return service.sessionHistory(id);
    }

    public record TerminateRequest(@Size(max = 200) String reason) {}

    @PostMapping("/sessions/{id}/terminate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Terminate an active session (revokes refresh tokens, closes attendance with source ADMIN_TERMINATED)")
    public void terminate(@PathVariable UUID id, @RequestBody(required = false) TerminateRequest r) {
        service.terminate(CurrentUser.get(), id, r == null ? null : r.reason());
    }

    @PostMapping("/users/{userId}/terminate-sessions")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public Map<String, Integer> terminateUser(@PathVariable UUID userId, @RequestBody(required = false) TerminateRequest r) {
        return Map.of("terminated", service.terminateAllForUser(CurrentUser.get(), userId, r == null ? null : r.reason()));
    }

    // ---------- IP rules ----------
    public record IpRuleRequest(@NotBlank @Size(max = 60) String cidr, @NotBlank @Pattern(regexp = "ALLOW|DENY") String ruleType,
                                @Size(max = 300) String description, Instant expiresAt) {}

    @GetMapping("/ip-rules")
    @PreAuthorize("hasAuthority('SECURITY_VIEW')")
    public List<TrustedIpRule> rules() {
        return ipRules.list();
    }

    @PostMapping("/ip-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public TrustedIpRule createRule(@Valid @RequestBody IpRuleRequest r) {
        return ipRules.create(r.cidr(), r.ruleType(), r.description(), r.expiresAt());
    }

    @PatchMapping("/ip-rules/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public TrustedIpRule toggle(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) throw ApiException.badRequest("active is required");
        return ipRules.setActive(id, active);
    }

    @DeleteMapping("/ip-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public void deleteRule(@PathVariable UUID id) {
        ipRules.delete(id);
    }

    // ---------- export ----------
    @GetMapping("/reports/export")
    @PreAuthorize("hasAuthority('SECURITY_VIEW') and hasAuthority('REPORT_EXPORT')")
    @Operation(summary = "Export login activity as CSV (type=logins|failed|anonymous|ip)")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "logins") String type, @RequestParam(required = false) String ip,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<String> headers;
        List<List<String>> rows;
        switch (type) {
            case "logins", "failed" -> {
                var p = service.loginActivity(filter(ip, null, null, "failed".equals(type) ? false : null, null, from, to), false, 0, 500);
                headers = List.of("Timestamp (UTC)", "User", "Username attempted", "Success", "Failure category", "IP", "IP version",
                        "Device", "Browser", "Suspicious", "Reasons", "Correlation ID");
                rows = p.content().stream().map(l -> List.of(l.createdAt().toString(), s(l.userName()), s(l.usernameAttempted()),
                        String.valueOf(l.success()), s(l.failureCategory()), l.ipAddress(), String.valueOf(l.ipVersion()), s(l.device()),
                        s(l.browser()), String.valueOf(l.suspicious()), s(l.suspiciousReasons()), s(l.correlationId()))).toList();
            }
            case "anonymous" -> {
                var p = service.anonymousAccess(filter(ip, null, null, null, null, from, to), 0, 500);
                headers = List.of("Timestamp (UTC)", "IP", "Method", "Endpoint", "Category", "Status", "Username", "User agent", "Correlation ID");
                rows = p.content().stream().map(l -> List.of(l.getCreatedAt().toString(), l.getIpAddress(), l.getHttpMethod(), l.getEndpoint(),
                        l.getCategory(), String.valueOf(l.getStatusCode()), s(l.getUsername()), s(l.getUserAgent()), s(l.getCorrelationId()))).toList();
            }
            case "ip" -> {
                var p = service.ipActivity(filter(ip, null, null, null, null, from, to), null, 0, 500);
                headers = List.of("Timestamp (UTC)", "IP", "IP version", "User ID", "Activity", "Details");
                rows = p.content().stream().map(l -> List.of(l.getCreatedAt().toString(), l.getIpAddress(), String.valueOf(l.getIpVersion()),
                        l.getUserId() == null ? "" : l.getUserId().toString(), l.getActivityType(), s(l.getDetails()))).toList();
            }
            default -> throw ApiException.badRequest("Unknown export type");
        }
        byte[] csv = csv(headers, rows);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("security-" + type + ".csv").build().toString())
                .contentType(MediaType.parseMediaType("text/csv")).body(csv);
    }

    private static byte[] csv(List<String> headers, List<List<String>> rows) {
        try (var out = new ByteArrayOutputStream(); var w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             var p = new org.apache.commons.csv.CSVPrinter(w, org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build())) {
            for (var r : rows) p.printRecord(r.stream().map(SecurityController::safe).toList());
            p.flush();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String safe(String v) {
        return com.suma.common.SpreadsheetSafety.cell(v);
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
