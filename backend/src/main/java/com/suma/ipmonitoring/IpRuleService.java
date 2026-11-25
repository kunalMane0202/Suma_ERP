package com.suma.ipmonitoring;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.common.ClientIpResolver;
import com.suma.security.CurrentUser;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configurable IP allow/deny policy. DENY rules block matching IPs. If at least one ALLOW rule is active, login is
 * restricted to allow-listed ranges. Rules never trigger automatically from IP changes.
 */
@Service
public class IpRuleService {
    private final TrustedIpRuleRepository repo;
    private final AuditService audit;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    private record CompiledRule(IpAddressMatcher matcher, String type, Instant expiresAt) {}
    private record Snapshot(List<CompiledRule> rules, Instant loadedAt) {}

    public IpRuleService(TrustedIpRuleRepository repo, AuditService audit, Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    public enum Decision { ALLOW, DENY_LISTED, NOT_ALLOW_LISTED }

    public Decision evaluate(String ip) {
        Snapshot s = snapshot.get();
        Instant now = Instant.now(clock);
        if (s == null || s.loadedAt.plusSeconds(30).isBefore(now)) {
            s = reload();
        }
        boolean anyAllow = false, allowed = false;
        for (CompiledRule r : s.rules) {
            if (r.expiresAt != null && r.expiresAt.isBefore(now)) continue;
            boolean matches;
            try {
                matches = r.matcher.matches(ip);
            } catch (IllegalArgumentException e) {
                matches = false;
            }
            if ("DENY".equals(r.type) && matches) return Decision.DENY_LISTED;
            if ("ALLOW".equals(r.type)) {
                anyAllow = true;
                if (matches) allowed = true;
            }
        }
        return anyAllow && !allowed ? Decision.NOT_ALLOW_LISTED : Decision.ALLOW;
    }

    public Snapshot reload() {
        List<CompiledRule> compiled = repo.findByActiveTrue().stream()
                .map(r -> new CompiledRule(new IpAddressMatcher(r.getCidr()), r.getRuleType(), r.getExpiresAt())).toList();
        Snapshot s = new Snapshot(compiled, Instant.now(clock));
        snapshot.set(s);
        return s;
    }

    @Transactional(readOnly = true)
    public List<TrustedIpRule> list() {
        return repo.findAll();
    }

    @Transactional
    public TrustedIpRule create(String cidr, String type, String description, Instant expiresAt) {
        String normalized = validateCidr(cidr);
        if (!"ALLOW".equals(type) && !"DENY".equals(type)) throw ApiException.badRequest("ruleType must be ALLOW or DENY");
        if (repo.existsByCidrAndRuleType(normalized, type)) throw ApiException.conflict("Rule already exists");
        if ("ALLOW".equals(type) && repo.findByActiveTrue().stream().noneMatch(r -> "ALLOW".equals(r.getRuleType()))) {
            // first allow rule turns on allow-list mode; the caller's own IP should be covered to avoid lock-out
            audit.log("IP_ALLOWLIST_ENABLED", "TrustedIpRule", normalized, "Allow-list mode enabled");
        }
        TrustedIpRule r = new TrustedIpRule();
        r.setCidr(normalized);
        r.setRuleType(type);
        r.setDescription(description);
        r.setExpiresAt(expiresAt);
        r.setCreatedBy(CurrentUser.id());
        repo.save(r);
        audit.log("IP_RULE_CREATED", "TrustedIpRule", r.getId(), type + " " + normalized);
        snapshot.set(null);
        return r;
    }

    @Transactional
    public TrustedIpRule setActive(UUID id, boolean active) {
        TrustedIpRule r = repo.findById(id).orElseThrow(() -> ApiException.notFound("IP rule"));
        r.setActive(active);
        audit.log(active ? "IP_RULE_ENABLED" : "IP_RULE_DISABLED", "TrustedIpRule", id, r.getRuleType() + " " + r.getCidr());
        snapshot.set(null);
        return r;
    }

    @Transactional
    public void delete(UUID id) {
        TrustedIpRule r = repo.findById(id).orElseThrow(() -> ApiException.notFound("IP rule"));
        repo.delete(r);
        audit.log("IP_RULE_DELETED", "TrustedIpRule", id, r.getRuleType() + " " + r.getCidr());
        snapshot.set(null);
    }

    static String validateCidr(String cidr) {
        if (cidr == null || cidr.isBlank()) throw ApiException.badRequest("CIDR is required");
        String c = cidr.trim();
        String addr = c.contains("/") ? c.substring(0, c.indexOf('/')) : c;
        String normalized = ClientIpResolver.normalize(addr);
        if (normalized == null) throw ApiException.badRequest("Invalid IP address or CIDR: " + cidr);
        String result = c.contains("/") ? normalized + c.substring(c.indexOf('/')) : normalized;
        try {
            new IpAddressMatcher(result);
            if (c.contains("/")) {
                int bits = Integer.parseInt(c.substring(c.indexOf('/') + 1));
                int max = normalized.contains(":") ? 128 : 32;
                if (bits < 0 || bits > max) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid IP address or CIDR: " + cidr);
        }
        return result;
    }
}
