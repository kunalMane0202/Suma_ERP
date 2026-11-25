package com.suma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.common.ApiError;
import com.suma.common.CorrelationIdFilter;
import com.suma.ipmonitoring.SecurityEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;

/** Records authenticated-but-unauthorised access attempts (unauthorized endpoint access monitoring). */
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityEventRecorder recorder;
    private final ObjectMapper mapper;

    public RestAccessDeniedHandler(SecurityEventRecorder recorder, ObjectMapper mapper) {
        this.recorder = recorder;
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        AuthUser u = CurrentUser.orNull();
        recorder.anonymousAccess(request, "ACCESS_DENIED", 403, u == null ? null : u.id(), u == null ? null : u.username());
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 403, "ACCESS_DENIED",
                "You do not have permission to perform this action", request.getRequestURI(), CorrelationIdFilter.current(), null));
    }
}
