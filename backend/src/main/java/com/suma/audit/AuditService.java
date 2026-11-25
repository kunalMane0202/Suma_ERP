package com.suma.audit;

import com.suma.common.CorrelationIdFilter;
import com.suma.common.RequestContext;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuditService {
    private final AuditLogRepository repo;
    private final RequestContext requestContext;
    private final Clock clock;

    public AuditService(AuditLogRepository repo, RequestContext requestContext, Clock clock) {
        this.repo = repo;
        this.requestContext = requestContext;
        this.clock = clock;
    }

    /** Records an audit entry in the caller's transaction (rolled back together with the business change). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, String entityType, Object entityId, String details) {
        AuthUser actor = CurrentUser.orNull();
        AuditLog a = new AuditLog();
        if (actor != null) {
            a.setActorId(actor.id());
            a.setActorUsername(actor.username());
        } else {
            a.setActorUsername("system");
        }
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId == null ? null : entityId.toString());
        a.setDetails(details);
        a.setIpAddress(requestContext.currentIp());
        a.setCorrelationId(CorrelationIdFilter.current());
        a.setCreatedAt(Instant.now(clock));
        repo.save(a);
    }
}
