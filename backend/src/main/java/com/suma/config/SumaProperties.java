package com.suma.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "suma")
public record SumaProperties(
        Jwt jwt,
        boolean cookieSecure,
        List<String> corsOrigins,
        List<String> trustedProxies,
        String encryptionKey,
        String uploadsDir,
        String mailFrom,
        boolean schedulingEnabled,
        BootstrapAdmin bootstrapAdmin,
        boolean demoData,
        LoginRateLimit loginRateLimit) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlHours) {}

    public record BootstrapAdmin(String username, String email, String password) {}

    public record LoginRateLimit(int maxAttempts, int windowSeconds) {}

    public List<String> trustedProxies() {
        return trustedProxies == null ? List.of() : trustedProxies.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public List<String> corsOrigins() {
        return corsOrigins == null ? List.of() : corsOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
