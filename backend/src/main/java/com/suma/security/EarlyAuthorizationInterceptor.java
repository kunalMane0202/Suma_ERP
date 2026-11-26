package com.suma.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates a handler's {@code @PreAuthorize} expression before Spring MVC reads and validates the request body.
 * Method security alone runs after argument binding, so an unauthorised caller sending an invalid body got 400
 * (revealing validation rules) instead of 403. Method security stays in place as the authoritative check; this
 * only moves the denial earlier. Expressions in this code base use only hasAuthority(...); anything that cannot be
 * evaluated without method arguments is skipped here and left to method security.
 */
public class EarlyAuthorizationInterceptor implements HandlerInterceptor {
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private final Map<Method, Optional<Expression>> cache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;
        Optional<Expression> expr = cache.computeIfAbsent(hm.getMethod(), m -> {
            PreAuthorize pa = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
            if (pa == null) pa = AnnotatedElementUtils.findMergedAnnotation(hm.getBeanType(), PreAuthorize.class);
            if (pa == null || pa.value().contains("#")) return Optional.empty();
            return Optional.of(PARSER.parseExpression(pa.value()));
        });
        if (expr.isEmpty()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true; // unauthenticated requests are rejected by the filter chain (401)
        Boolean allowed;
        try {
            allowed = expr.get().getValue(new StandardEvaluationContext(new SecurityExpressionRoot(auth) {}), Boolean.class);
        } catch (RuntimeException e) {
            return true; // not evaluable here; method security decides
        }
        if (!Boolean.TRUE.equals(allowed)) throw new AccessDeniedException("Access denied");
        return true;
    }
}
