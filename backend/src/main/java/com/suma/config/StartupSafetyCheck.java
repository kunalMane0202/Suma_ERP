package com.suma.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Warns loudly when development defaults are used, and refuses to start with them when secure cookies (production) are on. */
@Component
public class StartupSafetyCheck {
    private static final Logger log = LoggerFactory.getLogger(StartupSafetyCheck.class);
    static final String DEV_JWT = "dev-only-insecure-secret-change-me-0123456789abcdef";
    static final String DEV_KEY = "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktMzJieXRlcyE=";
    static final String DEV_ADMIN = "Admin@12345";
    private final SumaProperties props;
    private final boolean apiDocsEnabled;

    public StartupSafetyCheck(SumaProperties props, @org.springframework.beans.factory.annotation.Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled) {
        this.props = props;
        this.apiDocsEnabled = apiDocsEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        boolean devJwt = DEV_JWT.equals(props.jwt().secret());
        boolean devKey = DEV_KEY.equals(props.encryptionKey());
        boolean devAdmin = DEV_ADMIN.equals(props.bootstrapAdmin().password());
        if (props.cookieSecure() && (devJwt || devKey)) {
            throw new IllegalStateException("Refusing to start: SUMA_COOKIE_SECURE=true (production) but SUMA_JWT_SECRET or SUMA_ENCRYPTION_KEY still has the development default");
        }
        if (devJwt) log.warn("SECURITY: SUMA_JWT_SECRET is the development default. Set a random secret outside local development.");
        if (devKey) log.warn("SECURITY: SUMA_ENCRYPTION_KEY is the development default. Set a random key outside local development.");
        if (devAdmin) log.warn("SECURITY: bootstrap admin password is the development default. Set SUMA_ADMIN_PASSWORD and change it after first sign-in.");
        if (props.cookieSecure() && apiDocsEnabled) log.warn("SECURITY: API documentation (Swagger UI, /v3/api-docs) is public in a production configuration. Set SUMA_API_DOCS_ENABLED=false.");
        if (props.demoData()) log.warn("DEMO DATA is enabled (SUMA_DEMO_DATA=true). Do not use this configuration in production.");
    }
}
