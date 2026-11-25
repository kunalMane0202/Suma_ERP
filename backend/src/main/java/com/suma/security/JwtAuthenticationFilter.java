package com.suma.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final SessionValidator validator;
    private final SessionTouchService touch;

    public JwtAuthenticationFilter(JwtService jwt, SessionValidator validator, SessionTouchService touch) {
        this.jwt = jwt;
        this.validator = validator;
        this.touch = touch;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.ParsedToken t = jwt.parse(header.substring(7).trim());
            if (!t.valid()) {
                request.setAttribute(AuthErrorAttribute.NAME, t.error() == JwtService.TokenError.EXPIRED ? "EXPIRED_SESSION" : "INVALID_TOKEN");
                if (t.username() != null) request.setAttribute(AuthErrorAttribute.NAME + ".user", t.username());
            } else {
                SessionValidator.Validation v = validator.validate(t.sessionId(), t.userId());
                switch (v.result()) {
                    case OK -> {
                        AuthUser u = v.user();
                        List<SimpleGrantedAuthority> auths = new ArrayList<>();
                        auths.add(new SimpleGrantedAuthority("ROLE_" + u.role()));
                        u.permissions().forEach(p -> auths.add(new SimpleGrantedAuthority(p)));
                        var auth = new UsernamePasswordAuthenticationToken(u, null, auths);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        touch.touch(u.sessionId(), u.id(), request);
                    }
                    case EXPIRED -> request.setAttribute(AuthErrorAttribute.NAME, "EXPIRED_SESSION");
                    case REVOKED, USER_INACTIVE -> {
                        request.setAttribute(AuthErrorAttribute.NAME, "REVOKED_SESSION");
                        request.setAttribute(AuthErrorAttribute.NAME + ".user", t.username());
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }
}
