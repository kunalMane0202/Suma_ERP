package com.suma.security;

/** Request attribute carrying why authentication failed, used by the entry point to classify anonymous access. */
public final class AuthErrorAttribute {
    public static final String NAME = "suma.authError";
    private AuthErrorAttribute() {}
}
