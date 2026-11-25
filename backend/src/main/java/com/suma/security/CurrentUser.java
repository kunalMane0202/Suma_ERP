package com.suma.security;

import com.suma.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthUser orNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthUser u ? u : null;
    }

    public static AuthUser get() {
        AuthUser u = orNull();
        if (u == null) throw ApiException.unauthorized("UNAUTHENTICATED", "Authentication required");
        return u;
    }

    public static UUID id() {
        AuthUser u = orNull();
        return u == null ? null : u.id();
    }
}
