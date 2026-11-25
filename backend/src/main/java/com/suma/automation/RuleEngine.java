package com.suma.automation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.notification.NotificationService;
import com.suma.project.TicketCategory;
import com.suma.project.TicketCategoryRepository;
import com.suma.ticket.Ticket;
import com.suma.ticket.TicketEvent;
import com.suma.ticket.TicketRepository;
import com.suma.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Configurable TRIGGER → CONDITIONS → ACTIONS engine for tickets. Runs synchronously in the transaction that raised the
 * event, so rule actions are atomic with the change. Actions do not re-trigger rules (re-entrancy guard).
 *
 * Conditions: [{"field":"priority","op":"eq|neq|in|contains|empty|notEmpty","value":...}]
 * Actions: ASSIGN_USER{userId}, ASSIGN_LEAST_LOADED{teamId?}, SET_PRIORITY{value}, SET_CATEGORY{categoryId},
 *          AUTO_CATEGORIZE, NOTIFY_USER{userId,message}, NOTIFY_ROLE{role,message}, ESCALATE{level,reason},
 *          ADD_COMMENT{body,internal}, REQUEST_APPROVAL{approverId,reason}, SET_STATUS{status,note}, RPA_TRIGGER{endpoint,job}
 */
@Service
public class RuleEngine {
    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);
    public static final Set<String> TRIGGERS = Set.of("TICKET_CREATED", "TICKET_UPDATED", "TICKET_STATUS_CHANGED", "TICKET_ASSIGNED",
            "SLA_BREACHED", "TICKET_PENDING_REMINDER");
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);

    private final AutomationRuleRepository rules;
    private final AutomationExecutionRepository executions;
    private final ApprovalRequestRepository approvals;
    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final TicketCategoryRepository categories;
    private final NotificationService notifications;
    private final List<RpaAdapter> rpa;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public RuleEngine(AutomationRuleRepository rules, AutomationExecutionRepository executions, ApprovalRequestRepository approvals,
                      TicketRepository tickets, TicketService ticketService, TicketCategoryRepository categories,
                      NotificationService notifications, List<RpaAdapter> rpa, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.rules = rules;
        this.executions = executions;
        this.approvals = approvals;
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.categories = categories;
        this.notifications = notifications;
        this.rpa = rpa;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public record Condition(String field, String op, Object value) {}

    @EventListener
    public void onTicketEvent(TicketEvent e) {
        if (RUNNING.get()) return;
        RUNNING.set(true);
        try {
            Ticket t = tickets.findById(e.ticketId()).orElse(null);
            if (t == null) return;
            for (AutomationRule r : rules.applicable(e.type(), t.getProjectId())) {
                AutomationExecution ex = new AutomationExecution();
                ex.setRuleId(r.getId());
                ex.setTriggerEvent(e.type());
                ex.setEntityType("Ticket");
                ex.setEntityId(t.getId().toString());
                try {
                    if (!matches(r, t)) continue;
                    List<String> done = execute(r, t);
                    ex.setStatus("MATCHED");
                    ex.setActionsRun(String.join("; ", done));
                    executions.save(ex);
                    if (r.isStopProcessing()) break;
                } catch (RuntimeException err) {
                    ex.setStatus("FAILED");
                    ex.setError(err.getMessage());
                    executions.save(ex);
                    log.warn("Automation rule {} failed: {}", r.getName(), err.getMessage());
                }
            }
        } finally {
            RUNNING.set(false);
        }
    }

    public boolean matches(AutomationRule r, Ticket t) {
        List<Condition> conds = parse(r.getConditions(), new TypeReference<>() {});
        for (Condition c : conds) {
            Object actual = field(t, c.field());
            if (!evaluate(actual, c.op(), c.value())) return false;
        }
        return true;
    }

    static boolean evaluate(Object actual, String op, Object expected) {
        String a = actual == null ? null : actual.toString();
        return switch (op == null ? "eq" : op) {
            case "eq" -> Objects.equals(a, expected == null ? null : expected.toString());
            case "neq" -> !Objects.equals(a, expected == null ? null : expected.toString());
            case "in" -> expected instanceof Collection<?> col && a != null && col.stream().map(String::valueOf).anyMatch(a::equals);
            case "contains" -> a != null && expected != null && a.toLowerCase().contains(expected.toString().toLowerCase());
            case "empty" -> a == null || a.isBlank();
            case "notEmpty" -> a != null && !a.isBlank();
            case "gte" -> a != null && expected != null && Double.parseDouble(a) >= Double.parseDouble(expected.toString());
            default -> throw new IllegalArgumentException("Unknown operator " + op);
        };
    }

    private static Object field(Ticket t, String f) {
        return switch (f) {
            case "priority" -> t.getPriority();
            case "status" -> t.getStatus();
            case "projectId" -> t.getProjectId();
            case "clientId" -> t.getClientId();
            case "categoryId" -> t.getCategoryId();
            case "source" -> t.getSource();
            case "title" -> t.getTitle();
            case "description" -> t.getDescription();
            case "text" -> (t.getTitle() + " " + (t.getDescription() == null ? "" : t.getDescription()));
            case "assigneeId" -> t.getAssigneeId();
            case "teamId" -> t.getTeamId();
            case "escalationLevel" -> t.getEscalationLevel();
            default -> throw new IllegalArgumentException("Unknown field " + f);
        };
    }

    private List<String> execute(AutomationRule r, Ticket t) {
        List<Map<String, Object>> actions = parse(r.getActions(), new TypeReference<>() {});
        List<String> done = new ArrayList<>();
        for (Map<String, Object> a : actions) {
            String type = String.valueOf(a.get("type"));
            switch (type) {
                case "ASSIGN_USER" -> {
                    ticketService.assignInternal(t, UUID.fromString(str(a, "userId")), null, null, "Automation: " + r.getName());
                    done.add("assigned");
                }
                case "ASSIGN_LEAST_LOADED" -> {
                    UUID team = a.get("teamId") != null ? UUID.fromString(str(a, "teamId")) : t.getTeamId();
                    UUID u = leastLoaded(t.getProjectId(), team);
                    if (u != null) {
                        ticketService.assignInternal(t, u, team, null, "Automation (least loaded): " + r.getName());
                        done.add("assigned least-loaded " + u);
                    } else done.add("no eligible assignee");
                }
                case "SET_PRIORITY" -> {
                    String p = str(a, "value");
                    if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(p)) throw new IllegalArgumentException("Invalid priority");
                    ticketService.record(t, null, "UPDATED", "priority", t.getPriority(), p, "Automation: " + r.getName());
                    t.setPriority(p);
                    done.add("priority=" + p);
                }
                case "SET_CATEGORY" -> {
                    t.setCategoryId(UUID.fromString(str(a, "categoryId")));
                    ticketService.record(t, null, "UPDATED", "category", null, str(a, "categoryId"), "Automation: " + r.getName());
                    done.add("category set");
                }
                case "AUTO_CATEGORIZE" -> {
                    TicketCategory c = categorize(t);
                    if (c != null) {
                        if (c.getParentId() != null) {
                            t.setCategoryId(c.getParentId());
                            t.setSubcategoryId(c.getId());
                        } else t.setCategoryId(c.getId());
                        if (c.getDefaultTeamId() != null && t.getTeamId() == null) t.setTeamId(c.getDefaultTeamId());
                        ticketService.record(t, null, "UPDATED", "category", null, c.getName(), "Auto-categorized by keywords");
                        done.add("categorized " + c.getName());
                    } else done.add("no category matched");
                }
                case "NOTIFY_USER" -> {
                    notifications.notify(UUID.fromString(str(a, "userId")), "AUTOMATION", "Ticket " + t.getTicketNumber(),
                            Objects.toString(a.get("message"), t.getTitle()), "INFO", "Ticket", t.getId());
                    done.add("notified user");
                }
                case "NOTIFY_ROLE" -> {
                    List<UUID> ids = jdbc.queryForList("select u.id from users u join roles r on r.id = u.role_id join project_members pm on pm.user_id = u.id "
                            + "where r.code = ? and pm.project_id = ? and u.status = 'ACTIVE'", UUID.class, str(a, "role"), t.getProjectId());
                    notifications.notifyAll(ids, "AUTOMATION", "Ticket " + t.getTicketNumber(), Objects.toString(a.get("message"), t.getTitle()), "INFO", "Ticket", t.getId());
                    done.add("notified " + ids.size() + " " + str(a, "role"));
                }
                case "ESCALATE" -> {
                    int level = a.get("level") == null ? t.getEscalationLevel() + 1 : Integer.parseInt(str(a, "level"));
                    ticketService.escalateInternal(t, null, Objects.toString(a.get("reason"), "Automation: " + r.getName()), level);
                    done.add("escalated L" + level);
                }
                case "ADD_COMMENT" -> {
                    ticketService.record(t, null, "COMMENTED", null, null, null, "Automation note");
                    jdbc.update("insert into ticket_comments (id, ticket_id, author_id, body, internal, created_at) values (?, ?, null, ?, ?, ?)",
                            UUID.randomUUID(), t.getId(), str(a, "body"), Boolean.parseBoolean(Objects.toString(a.get("internal"), "true")),
                            java.sql.Timestamp.from(Instant.now()));
                    done.add("comment added");
                }
                case "REQUEST_APPROVAL" -> {
                    ApprovalRequest ar = new ApprovalRequest();
                    ar.setTicketId(t.getId());
                    ar.setApproverId(UUID.fromString(str(a, "approverId")));
                    ar.setReason(Objects.toString(a.get("reason"), r.getName()));
                    approvals.save(ar);
                    notifications.notify(ar.getApproverId(), "APPROVAL_REQUESTED", "Approval requested: " + t.getTicketNumber(), ar.getReason(), "WARNING", "Ticket", t.getId());
                    ticketService.record(t, null, "APPROVAL_REQUESTED", null, null, null, ar.getReason());
                    done.add("approval requested");
                }
                case "SET_STATUS" -> {
                    ticketService.applyStatus(t, str(a, "status"), null, Objects.toString(a.get("note"), "Automation: " + r.getName()));
                    done.add("status=" + str(a, "status"));
                }
                case "RPA_TRIGGER" -> {
                    RpaAdapter adapter = rpa.stream().filter(x -> x.name().equals(Objects.toString(a.get("adapter"), "WEBHOOK"))).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown RPA adapter"));
                    var res = adapter.trigger(str(a, "endpoint"), Objects.toString(a.get("job"), "suma-ticket"),
                            Map.of("ticketId", t.getId().toString(), "ticketNumber", t.getTicketNumber(), "priority", t.getPriority(), "status", t.getStatus()));
                    ticketService.record(t, null, "RPA_TRIGGERED", null, null, String.valueOf(res.statusCode()), res.message());
                    done.add("rpa " + (res.success() ? "ok" : "failed: " + res.message()));
                }
                default -> throw new IllegalArgumentException("Unknown action " + type);
            }
        }
        return done;
    }

    /** Keyword-based categorisation: category keywords (comma separated) matched against title + description. */
    TicketCategory categorize(Ticket t) {
        String text = (t.getTitle() + " " + Objects.toString(t.getDescription(), "")).toLowerCase();
        TicketCategory best = null;
        int bestHits = 0;
        for (TicketCategory c : categories.forProject(t.getProjectId())) {
            if (c.getKeywords() == null) continue;
            int hits = 0;
            for (String k : c.getKeywords().split(",")) {
                String kw = k.trim().toLowerCase();
                if (!kw.isEmpty() && text.contains(kw)) hits++;
            }
            if (hits > bestHits || (hits == bestHits && hits > 0 && c.getParentId() != null && best != null && best.getParentId() == null)) {
                best = c;
                bestHits = hits;
            }
        }
        return best;
    }

    private UUID leastLoaded(UUID projectId, UUID teamId) {
        List<UUID> ids = jdbc.queryForList("""
                select u.id from users u join project_members pm on pm.user_id = u.id and pm.project_id = ?
                join roles r on r.id = u.role_id
                left join tickets t on t.assignee_id = u.id and t.status not in ('RESOLVED','CLOSED')
                where u.status = 'ACTIVE' and r.code in ('L1','L2','L3')
                  and (cast(? as uuid) is null or exists (select 1 from team_members tm where tm.team_id = ? and tm.user_id = u.id))
                group by u.id, u.max_open_tickets
                having u.max_open_tickets is null or count(t.id) < u.max_open_tickets
                order by count(t.id), random() limit 1
                """, UUID.class, projectId, teamId, teamId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private <T> T parse(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json == null || json.isBlank() ? "[]" : json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid rule JSON: " + e.getMessage());
        }
    }

    private static String str(Map<String, Object> a, String key) {
        Object v = a.get(key);
        if (v == null) throw new IllegalArgumentException("Action parameter '" + key + "' is required");
        return v.toString();
    }
}
