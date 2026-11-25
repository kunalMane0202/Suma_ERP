package com.suma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.common.ApiError;
import com.suma.common.CorrelationIdFilter;
import com.suma.ipmonitoring.SecurityEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.Instant;

/** Returns 401 JSON and records the unauthenticated request (missing auth / invalid / expired / revoked). */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final SecurityEventRecorder recorder;
    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(SecurityEventRecorder recorder, ObjectMapper mapper) {
        this.recorder = recorder;
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        Object attr = request.getAttribute(AuthErrorAttribute.NAME);
        String category = attr == null ? "MISSING_AUTH" : attr.toString();
        Object user = request.getAttribute(AuthErrorAttribute.NAME + ".user");
        recorder.anonymousAccess(request, category, 401, null, user == null ? null : user.toString());
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String message = switch (category) {
            case "EXPIRED_SESSION" -> "Session expired";
            case "REVOKED_SESSION" -> "Session is no longer valid";
            case "INVALID_TOKEN" -> "Invalid authentication token";
            default -> "Authentication required";
        };
        mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 401, category, message,
                request.getRequestURI(), CorrelationIdFilter.current(), null));
    }
}
