package com.suma.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/** Authenticated principal built from a validated access token. */
public record AuthUser(UUID id, String username, String role, UUID sessionId, Set<String> permissions) implements Serializable {
    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean has(String permission) { return permissions.contains(permission); }
}
