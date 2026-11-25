package com.suma.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.common.ApiError;
import com.suma.common.ClientIpResolver;
import com.suma.common.CorrelationIdFilter;
import com.suma.ipmonitoring.IpRuleService;
import com.suma.ipmonitoring.SecurityEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/** Enforces administrator-configured IP deny / allow rules on API requests. */
public class IpRuleFilter extends OncePerRequestFilter {
    private final IpRuleService rules;
    private final ClientIpResolver resolver;
    private final SecurityEventRecorder recorder;
    private final ObjectMapper mapper;

    public IpRuleFilter(IpRuleService rules, ClientIpResolver resolver, SecurityEventRecorder recorder, ObjectMapper mapper) {
        this.rules = rules;
        this.resolver = resolver;
        this.recorder = recorder;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = resolver.resolve(request);
        IpRuleService.Decision d = rules.evaluate(ip);
        if (d != IpRuleService.Decision.ALLOW) {
            recorder.anonymousAccess(request, "BLOCKED_IP", 403, null, null);
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 403, "IP_BLOCKED",
                    "Access from this network address is not permitted", request.getRequestURI(), CorrelationIdFilter.current(), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
