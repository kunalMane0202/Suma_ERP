package com.suma.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Access to the current HTTP request's client information from service code (null-safe outside requests). */
@Component
public class RequestContext {
    private final ClientIpResolver resolver;

    public RequestContext(ClientIpResolver resolver) {
        this.resolver = resolver;
    }

    public HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    public String currentIp() {
        HttpServletRequest r = currentRequest();
        return r == null ? null : resolver.resolve(r);
    }

    public ClientInfo clientInfo() {
        HttpServletRequest r = currentRequest();
        return r == null ? ClientInfo.system() : resolver.clientInfo(r);
    }
}
