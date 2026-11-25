package com.suma.audit;

import com.suma.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
public class AuditController {
    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public PageResponse<AuditLog> list(@RequestParam(required = false) String action, @RequestParam(required = false) String entityType,
                                       @RequestParam(required = false) String entityId, @RequestParam(required = false) UUID actorId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (action != null && !action.isBlank()) ps.add(cb.like(root.get("action"), "%" + action.toUpperCase() + "%"));
            if (entityType != null && !entityType.isBlank()) ps.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null && !entityId.isBlank()) ps.add(cb.equal(root.get("entityId"), entityId));
            if (actorId != null) ps.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
