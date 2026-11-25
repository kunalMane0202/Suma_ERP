package com.suma.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.notification.NotificationService;
import com.suma.project.ProjectRepository;
import com.suma.ticket.TicketDtos;
import com.suma.ticket.TicketRepository;
import com.suma.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class IntegrationService {
    private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
    private final IntegrationRepository repo;
    private final IntegrationSyncLogRepository logs;
    private final Map<String, TicketConnector> connectors = new HashMap<>();
    private final CredentialCipher cipher;
    private final TicketService tickets;
    private final TicketRepository ticketRepo;
    private final ProjectRepository projects;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public IntegrationService(IntegrationRepository repo, IntegrationSyncLogRepository logs, List<TicketConnector> connectorList,
                              CredentialCipher cipher, TicketService tickets, TicketRepository ticketRepo, ProjectRepository projects,
                              NotificationService notifications, AuditService audit, TransactionTemplate tx, ObjectMapper mapper,
                              JdbcTemplate jdbc, Clock clock) {
        this.repo = repo;
        this.logs = logs;
        connectorList.forEach(c -> connectors.put(c.type(), c));
        this.cipher = cipher;
        this.tickets = tickets;
        this.ticketRepo = ticketRepo;
        this.projects = projects;
        this.notifications = notifications;
        this.audit = audit;
        this.tx = tx;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Request(String name, String connectorType, UUID projectId, String baseUrl, String authType,
                          Map<String, String> credentials, String query, Map<String, Object> fieldMapping,
                          Integer syncIntervalMinutes, Integer maxRetries, Boolean enabled) {}

    /** Credentials are never returned; only which keys are configured. */
    public record View(UUID id, String name, String connectorType, UUID projectId, String projectName, String baseUrl, String authType,
                       List<String> credentialKeys, String query, Map<String, Object> fieldMapping, int syncIntervalMinutes,
                       int maxRetries, boolean enabled, Instant lastSyncAt, String lastStatus, int consecutiveFailures) {}

    public record SyncResult(String status, int fetched, int created, int duplicates, int failed, String error) {}

    @Transactional(readOnly = true)
    public List<View> list() {
        return repo.findAll().stream().sorted(Comparator.comparing(Integration::getName)).map(this::view).toList();
    }

    @Transactional
    public View save(UUID id, Request r) {
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Name required");
        if (!connectors.containsKey(r.connectorType())) throw ApiException.badRequest("Unknown connector type");
        if (r.projectId() == null || !projects.existsById(r.projectId())) throw ApiException.badRequest("Project required");
        if (r.baseUrl() != null && !r.baseUrl().isBlank()) {
            String u = r.baseUrl().toLowerCase();
            if (!(u.startsWith("https://") || u.startsWith("http://") || u.startsWith("imaps://"))) throw ApiException.badRequest("Base URL must be http(s) or imaps");
        }
        Integration i = id == null ? new Integration() : repo.findById(id).orElseThrow(() -> ApiException.notFound("Integration"));
        if (id == null && repo.existsByNameIgnoreCase(r.name())) throw ApiException.conflict("Integration name exists");
        i.setName(r.name());
        i.setConnectorType(r.connectorType());
        i.setProjectId(r.projectId());
        i.setBaseUrl(r.baseUrl());
        i.setAuthType(r.authType() == null ? "NONE" : r.authType());
        if (r.credentials() != null && !r.credentials().isEmpty()) {
            try {
                i.setCredentialsEncrypted(cipher.encrypt(mapper.writeValueAsString(r.credentials())));
            } catch (Exception e) {
                throw ApiException.badRequest("Invalid credentials payload");
            }
        }
        i.setQuery(r.query());
        try {
            i.setFieldMapping(r.fieldMapping() == null ? null : mapper.writeValueAsString(r.fieldMapping()));
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid field mapping");
        }
        if (r.syncIntervalMinutes() != null) i.setSyncIntervalMinutes(Math.max(1, r.syncIntervalMinutes()));
        if (r.maxRetries() != null) i.setMaxRetries(Math.max(0, Math.min(10, r.maxRetries())));
        if (r.enabled() != null) i.setEnabled(r.enabled());
        i.setUpdatedAt(Instant.now(clock));
        repo.save(i);
        audit.log(id == null ? "INTEGRATION_CREATED" : "INTEGRATION_UPDATED", "Integration", i.getId(),
                i.getName() + " " + i.getConnectorType() + " enabled=" + i.isEnabled() + (r.credentials() != null ? " (credentials updated)" : ""));
        return view(i);
    }

    @Transactional
    public void delete(UUID id) {
        Integration i = repo.findById(id).orElseThrow(() -> ApiException.notFound("Integration"));
        repo.delete(i);
        audit.log("INTEGRATION_DELETED", "Integration", id, i.getName());
    }

    public String test(UUID id) {
        Integration i = repo.findById(id).orElseThrow(() -> ApiException.notFound("Integration"));
        try {
            return connectors.get(i.getConnectorType()).test(i, credentials(i));
        } catch (Exception e) {
            throw ApiException.badRequest("Connection failed: " + safeMessage(e));
        }
    }

    @Transactional(readOnly = true)
    public List<IntegrationSyncLog> logs(UUID id) {
        return logs.findTop50ByIntegrationIdOrderByStartedAtDesc(id);
    }

    /** Runs a sync with retries. Each external record is ingested in its own transaction; duplicates are skipped. */
    public SyncResult sync(UUID id) {
        Integration i = repo.findById(id).orElseThrow(() -> ApiException.notFound("Integration"));
        TicketConnector connector = connectors.get(i.getConnectorType());
        int attempt = 0;
        Exception last = null;
        while (attempt <= i.getMaxRetries()) {
            attempt++;
            IntegrationSyncLog l = new IntegrationSyncLog();
            l.setIntegrationId(id);
            l.setStartedAt(Instant.now(clock));
            l.setStatus("RUNNING");
            l.setAttempt(attempt);
            logs.save(l);
            try {
                List<Map<String, Object>> records = connector.fetch(i, credentials(i));
                Map<String, Object> mapping = mapping(i, connector);
                int created = 0, dup = 0, failed = 0;
                for (Map<String, Object> rec : records) {
                    String outcome = ingest(i, rec, mapping);
                    switch (outcome) {
                        case "CREATED" -> created++;
                        case "DUPLICATE" -> dup++;
                        default -> failed++;
                    }
                }
                l.setFetched(records.size());
                l.setCreated(created);
                l.setDuplicates(dup);
                l.setFailed(failed);
                l.setStatus(failed > 0 ? "PARTIAL" : "SUCCESS");
                l.setFinishedAt(Instant.now(clock));
                logs.save(l);
                finish(i.getId(), l.getStatus(), false);
                return new SyncResult(l.getStatus(), records.size(), created, dup, failed, null);
            } catch (Exception e) {
                last = e;
                l.setStatus("FAILED");
                l.setError(safeMessage(e));
                l.setFinishedAt(Instant.now(clock));
                logs.save(l);
                log.warn("Integration {} sync attempt {} failed: {}", i.getName(), attempt, safeMessage(e));
            }
        }
        finish(i.getId(), "FAILED", true);
        return new SyncResult("FAILED", 0, 0, 0, 0, safeMessage(last));
    }

    String ingest(Integration i, Map<String, Object> rec, Map<String, Object> mapping) {
        String externalId = FieldMapper.string(rec, (String) mapping.get("externalId"));
        if (externalId == null || externalId.isBlank()) return "FAILED";
        if (ticketRepo.existsByExternalSystemAndExternalId(i.getConnectorType(), externalId)) return "DUPLICATE";
        try {
            return tx.execute(s -> {
                String title = Optional.ofNullable(FieldMapper.string(rec, (String) mapping.get("title"))).orElse("(no subject) " + externalId);
                String priority = mapPriority(FieldMapper.string(rec, (String) mapping.get("priority")), mapping);
                String reporterEmail = FieldMapper.string(rec, (String) mapping.get("reporterEmail"));
                if (reporterEmail != null && !reporterEmail.matches("^[^@\\s]+@[^@\\s]+$")) reporterEmail = null;
                var req = new TicketDtos.CreateRequest(i.getProjectId(), title.length() > 300 ? title.substring(0, 300) : title,
                        FieldMapper.string(rec, (String) mapping.get("description")), priority, null, null, null, null,
                        FieldMapper.string(rec, (String) mapping.get("reporterName")), reporterEmail, null);
                String source = switch (i.getConnectorType()) {
                    case "JIRA" -> "JIRA";
                    case "SALESFORCE" -> "SALESFORCE";
                    case "EMAIL" -> "EMAIL";
                    default -> "API";
                };
                tickets.createInternal(req, source, i.getConnectorType(), externalId, null);
                return "CREATED";
            });
        } catch (ApiException e) {
            return e.getStatus().value() == 409 ? "DUPLICATE" : "FAILED";
        } catch (RuntimeException e) {
            return ticketRepo.existsByExternalSystemAndExternalId(i.getConnectorType(), externalId) ? "DUPLICATE" : "FAILED";
        }
    }

    @SuppressWarnings("unchecked")
    private static String mapPriority(String raw, Map<String, Object> mapping) {
        if (raw == null) return "MEDIUM";
        Object pm = mapping.get("priorityMap");
        if (pm instanceof Map<?, ?> m && m.get(raw) != null) return m.get(raw).toString();
        String up = raw.toUpperCase();
        return Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(up) ? up : "MEDIUM";
    }

    private void finish(UUID id, String status, boolean failed) {
        tx.executeWithoutResult(s -> repo.findById(id).ifPresent(i -> {
            i.setLastSyncAt(Instant.now(clock));
            i.setLastStatus(status);
            i.setConsecutiveFailures(failed ? i.getConsecutiveFailures() + 1 : 0);
            if (failed && i.getConsecutiveFailures() >= 3) {
                List<UUID> admins = jdbc.queryForList("select u.id from users u join roles r on r.id = u.role_id where r.code = 'ADMIN' and u.status = 'ACTIVE'", UUID.class);
                notifications.notifyAll(admins, "INTEGRATION_FAILURE", "Integration failing: " + i.getName(),
                        i.getConsecutiveFailures() + " consecutive failed synchronisations", "WARNING", "Integration", i.getId());
            }
        }));
    }

    /** Called by the scheduler: syncs enabled integrations whose interval elapsed. */
    public int syncDue() {
        int n = 0;
        Instant now = Instant.now(clock);
        for (Integration i : repo.findByEnabledTrue()) {
            if (i.getLastSyncAt() != null && i.getLastSyncAt().plus(Duration.ofMinutes(i.getSyncIntervalMinutes())).isAfter(now)) continue;
            sync(i.getId());
            n++;
        }
        return n;
    }

    private Map<String, String> credentials(Integration i) {
        if (i.getCredentialsEncrypted() == null) return Map.of();
        try {
            return mapper.readValue(cipher.decrypt(i.getCredentialsEncrypted()), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Stored credentials could not be read");
        }
    }

    private Map<String, Object> mapping(Integration i, TicketConnector c) throws Exception {
        Map<String, Object> m = new HashMap<>(c.defaultMapping());
        if (i.getFieldMapping() != null) m.putAll(mapper.readValue(i.getFieldMapping(), new TypeReference<Map<String, Object>>() {}));
        return m;
    }

    private View view(Integration i) {
        List<String> keys = List.of();
        try {
            keys = new ArrayList<>(credentials(i).keySet());
        } catch (RuntimeException ignored) {
            keys = List.of("(unreadable)");
        }
        Map<String, Object> fm = null;
        try {
            fm = i.getFieldMapping() == null ? null : mapper.readValue(i.getFieldMapping(), new TypeReference<>() {});
        } catch (Exception ignored) {
        }
        String projectName = projects.findById(i.getProjectId()).map(p -> p.getName()).orElse(null);
        return new View(i.getId(), i.getName(), i.getConnectorType(), i.getProjectId(), projectName, i.getBaseUrl(), i.getAuthType(), keys,
                i.getQuery(), fm, i.getSyncIntervalMinutes(), i.getMaxRetries(), i.isEnabled(), i.getLastSyncAt(), i.getLastStatus(),
                i.getConsecutiveFailures());
    }

    private static String safeMessage(Exception e) {
        if (e == null) return null;
        String m = e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "");
        return m.length() > 500 ? m.substring(0, 500) : m;
    }

    public Map<String, Map<String, Object>> defaultMappings() {
        Map<String, Map<String, Object>> m = new TreeMap<>();
        connectors.forEach((k, v) -> m.put(k, v.defaultMapping()));
        return m;
    }
}
