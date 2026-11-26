package com.suma.ticket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.audit.AuditService;
import com.suma.client.ClientRepository;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.notification.NotificationService;
import com.suma.project.Project;
import com.suma.project.ProjectRepository;
import com.suma.project.TicketCategoryRepository;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.sla.BusinessTimeCalculator;
import com.suma.sla.SlaPolicy;
import com.suma.sla.SlaService;
import com.suma.team.TeamRepository;
import com.suma.user.User;
import com.suma.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Ticket lifecycle NEW → ASSIGNED → IN_PROGRESS → PENDING → RESOLVED → CLOSED (→ REOPENED), with SLA tracking and full timeline. */
@Service
public class TicketService {
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "NEW", Set.of("ASSIGNED", "IN_PROGRESS", "PENDING", "RESOLVED", "CLOSED"),
            "ASSIGNED", Set.of("IN_PROGRESS", "PENDING", "RESOLVED", "NEW"),
            "IN_PROGRESS", Set.of("PENDING", "RESOLVED", "ASSIGNED"),
            "PENDING", Set.of("IN_PROGRESS", "RESOLVED", "ASSIGNED"),
            "RESOLVED", Set.of("CLOSED", "REOPENED"),
            "CLOSED", Set.of("REOPENED"),
            "REOPENED", Set.of("ASSIGNED", "IN_PROGRESS", "PENDING", "RESOLVED"));

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final TicketAttachmentRepository attachments;
    private final TicketHistoryRepository history;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final TicketCategoryRepository categories;
    private final TeamRepository teams;
    private final UserRepository users;
    private final SlaService sla;
    private final AccessScopeService scope;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final com.suma.ticket.field.CustomFieldService customFields;

    public TicketService(TicketRepository tickets, TicketCommentRepository comments, TicketAttachmentRepository attachments,
                         TicketHistoryRepository history, ProjectRepository projects, ClientRepository clients,
                         TicketCategoryRepository categories, TeamRepository teams, UserRepository users, SlaService sla,
                         AccessScopeService scope, NotificationService notifications, AuditService audit,
                         ApplicationEventPublisher events, ObjectMapper mapper, JdbcTemplate jdbc, Clock clock,
                         com.suma.ticket.field.CustomFieldService customFields) {
        this.customFields = customFields;
        this.tickets = tickets;
        this.comments = comments;
        this.attachments = attachments;
        this.history = history;
        this.projects = projects;
        this.clients = clients;
        this.categories = categories;
        this.teams = teams;
        this.users = users;
        this.sla = sla;
        this.scope = scope;
        this.notifications = notifications;
        this.audit = audit;
        this.events = events;
        this.mapper = mapper;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record Filter(String q, UUID projectId, UUID clientId, List<String> statuses, List<String> priorities, UUID assigneeId,
                         UUID teamId, Boolean overdue, Boolean breached, Boolean mine, Boolean unassigned, Instant from, Instant to) {}

    // ------------------------------------------------------------------ queries

    private Specification<Ticket> scopeSpec(AuthUser me) {
        Set<UUID> projectIds = scope.visibleProjectIds(me);
        boolean engineer = Set.of("L1", "L2", "L3").contains(me.role());
        return (root, q, cb) -> {
            if (projectIds == null) return cb.conjunction();
            List<Predicate> ps = new ArrayList<>();
            ps.add(projectIds.isEmpty() ? cb.disjunction() : root.get("projectId").in(projectIds));
            if (engineer) {
                ps.add(cb.or(cb.equal(root.get("assigneeId"), me.id()), cb.isNull(root.get("assigneeId")), cb.equal(root.get("createdBy"), me.id())));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketDtos.TicketSummary> list(AuthUser me, Filter f, int page, int size, String sort) {
        Instant now = Instant.now(clock);
        Specification<Ticket> spec = scopeSpec(me).and((root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("ticketNumber")), like),
                        cb.like(cb.lower(root.get("externalId")), like)));
            }
            if (f.projectId() != null) ps.add(cb.equal(root.get("projectId"), f.projectId()));
            if (f.clientId() != null) ps.add(cb.equal(root.get("clientId"), f.clientId()));
            if (f.statuses() != null && !f.statuses().isEmpty()) ps.add(root.get("status").in(f.statuses()));
            if (f.priorities() != null && !f.priorities().isEmpty()) ps.add(root.get("priority").in(f.priorities()));
            if (f.assigneeId() != null) ps.add(cb.equal(root.get("assigneeId"), f.assigneeId()));
            if (f.teamId() != null) ps.add(cb.equal(root.get("teamId"), f.teamId()));
            if (Boolean.TRUE.equals(f.mine())) ps.add(cb.equal(root.get("assigneeId"), me.id()));
            if (Boolean.TRUE.equals(f.unassigned())) ps.add(cb.isNull(root.get("assigneeId")));
            if (Boolean.TRUE.equals(f.overdue())) {
                ps.add(root.get("status").in("RESOLVED", "CLOSED").not());
                ps.add(cb.lessThan(root.get("resolutionDueAt"), now));
            }
            if (Boolean.TRUE.equals(f.breached())) ps.add(cb.or(cb.isTrue(root.get("responseBreached")), cb.isTrue(root.get("resolutionBreached"))));
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            if (f.to() != null) ps.add(cb.lessThan(root.get("createdAt"), f.to()));
            return cb.and(ps.toArray(Predicate[]::new));
        });
        Sort s = switch (sort == null ? "" : sort) {
            case "priority" -> Sort.by(Sort.Order.asc("priority"), Sort.Order.desc("createdAt"));
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "due" -> Sort.by(Sort.Order.asc("resolutionDueAt").nullsLast());
            case "updated" -> Sort.by(Sort.Direction.DESC, "updatedAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        Page<Ticket> p = tickets.findAll(spec, PageRequest.of(page, Math.min(size, 200), s));
        Lookup l = lookup(p.getContent());
        return PageResponse.of(p, t -> summary(t, l, now));
    }

    @Transactional(readOnly = true)
    public TicketDtos.TicketDetail get(AuthUser me, UUID id) {
        Ticket t = load(me, id);
        return detail(t);
    }

    Ticket load(AuthUser me, UUID id) {
        Ticket t = tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket"));
        if (tickets.count(scopeSpec(me).and((r, q, cb) -> cb.equal(r.get("id"), id))) == 0) throw ApiException.notFound("Ticket");
        return t;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public TicketDtos.TicketDetail create(AuthUser me, TicketDtos.CreateRequest r) {
        if (me != null) scope.requireProject(me, r.projectId());
        Ticket t = createInternal(r, "MANUAL", null, null, me == null ? null : me.id());
        return detail(t);
    }

    /** Creates a ticket from any source. External tickets are de-duplicated on (externalSystem, externalId). */
    @Transactional
    public Ticket createInternal(TicketDtos.CreateRequest r, String source, String externalSystem, String externalId, UUID actorId) {
        if (externalId != null && tickets.existsByExternalSystemAndExternalId(externalSystem, externalId))
            throw ApiException.conflict("Duplicate external ticket " + externalSystem + ":" + externalId);
        Project p = projects.findById(r.projectId()).orElseThrow(() -> ApiException.badRequest("Project not found"));
        if (!"ACTIVE".equals(p.getStatus())) throw ApiException.badRequest("Project is not active");
        validateCategory(r.projectId(), r.categoryId(), r.subcategoryId());
        Instant now = Instant.now(clock);
        Ticket t = new Ticket();
        t.setTicketNumber(nextNumber(p));
        t.setProjectId(p.getId());
        t.setClientId(p.getClientId());
        t.setTitle(r.title().trim());
        t.setDescription(r.description());
        t.setPriority(r.priority() == null ? "MEDIUM" : r.priority());
        t.setCategoryId(r.categoryId());
        t.setSubcategoryId(r.subcategoryId());
        t.setTeamId(r.teamId());
        t.setReporterName(r.reporterName());
        t.setReporterEmail(r.reporterEmail());
        t.setSource(source);
        t.setExternalSystem(externalSystem);
        t.setExternalId(externalId);
        t.setCreatedBy(actorId);
        // required custom fields are enforced for people creating tickets; integrations are never blocked by them
        t.setCustomFields(writeFields(customFields.clean(p.getId(), r.customFields(), "MANUAL".equals(source))));
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        applySla(t, p, now);
        tickets.saveAndFlush(t);
        record(t, actorId, "CREATED", null, null, null, "Source " + source + (externalId != null ? " (" + externalSystem + ":" + externalId + ")" : ""));
        if (r.assigneeId() != null) assignInternal(t, r.assigneeId(), r.teamId(), actorId, null);
        events.publishEvent(new TicketEvent("TICKET_CREATED", t.getId(), actorId, null));
        if ("CRITICAL".equals(t.getPriority())) {
            Set<UUID> notify = new HashSet<>();
            if (p.getManagerId() != null) notify.add(p.getManagerId());
            if (p.getTeamLeaderId() != null) notify.add(p.getTeamLeaderId());
            notifications.notifyAllWithRules(notify, "CRITICAL_INCIDENT", t.getProjectId(), "Critical ticket " + t.getTicketNumber(), t.getTitle(), "CRITICAL", "Ticket", t.getId());
        }
        return t;
    }

    @Transactional
    public TicketDtos.TicketDetail update(AuthUser me, UUID id, TicketDtos.UpdateRequest r) {
        Ticket t = load(me, id);
        Instant now = Instant.now(clock);
        if (r.title() != null && !r.title().equals(t.getTitle())) { record(t, me.id(), "UPDATED", "title", t.getTitle(), r.title(), null); t.setTitle(r.title().trim()); }
        if (r.description() != null && !r.description().equals(t.getDescription())) { record(t, me.id(), "UPDATED", "description", null, null, "Description edited"); t.setDescription(r.description()); }
        if (r.priority() != null && !r.priority().equals(t.getPriority())) {
            record(t, me.id(), "UPDATED", "priority", t.getPriority(), r.priority(), null);
            t.setPriority(r.priority());
            applySla(t, projects.findById(t.getProjectId()).orElseThrow(), t.getCreatedAt());
        }
        if (r.categoryId() != null || r.subcategoryId() != null) {
            validateCategory(t.getProjectId(), r.categoryId(), r.subcategoryId());
            if (!Objects.equals(r.categoryId(), t.getCategoryId())) record(t, me.id(), "UPDATED", "category", name(t.getCategoryId()), name(r.categoryId()), null);
            t.setCategoryId(r.categoryId());
            t.setSubcategoryId(r.subcategoryId());
        }
        if (r.teamId() != null && !r.teamId().equals(t.getTeamId())) { record(t, me.id(), "UPDATED", "team", null, r.teamId().toString(), null); t.setTeamId(r.teamId()); }
        if (r.customFields() != null) {
            String before = t.getCustomFields();
            t.setCustomFields(writeFields(customFields.clean(t.getProjectId(), r.customFields(), true)));
            if (!java.util.Objects.equals(before, t.getCustomFields())) record(t, me.id(), "UPDATED", "customFields", null, null, "Custom fields edited");
        }
        t.setUpdatedAt(now);
        events.publishEvent(new TicketEvent("TICKET_UPDATED", t.getId(), me.id(), null));
        return detail(t);
    }

    @Transactional
    public TicketDtos.TicketDetail assign(AuthUser me, UUID id, TicketDtos.AssignRequest r) {
        Ticket t = load(me, id);
        if (!t.isOpen()) throw ApiException.badRequest("Resolved or closed tickets cannot be reassigned");
        assignInternal(t, r.assigneeId(), r.teamId(), me.id(), r.note());
        return detail(t);
    }

    public void assignInternal(Ticket t, UUID assigneeId, UUID teamId, UUID actorId, String note) {
        if (assigneeId != null) {
            User u = users.findById(assigneeId).orElseThrow(() -> ApiException.badRequest("Assignee not found"));
            if (!u.isActive()) throw ApiException.badRequest("Assignee is not active");
            Integer member = jdbc.queryForObject("select count(*) from project_members where project_id = ? and user_id = ?", Integer.class, t.getProjectId(), assigneeId);
            if (member == null || member == 0) throw ApiException.badRequest("Assignee is not allocated to this project");
        }
        UUID old = t.getAssigneeId();
        t.setAssigneeId(assigneeId);
        if (teamId != null) t.setTeamId(teamId);
        if (assigneeId != null && ("NEW".equals(t.getStatus()) || "REOPENED".equals(t.getStatus()))) {
            record(t, actorId, "STATUS_CHANGED", "status", t.getStatus(), "ASSIGNED", null);
            t.setStatus("ASSIGNED");
        }
        t.setUpdatedAt(Instant.now(clock));
        record(t, actorId, old == null ? "ASSIGNED" : "REASSIGNED", "assignee", userName(old), userName(assigneeId), note);
        if (assigneeId != null && !assigneeId.equals(actorId)) {
            notifications.notifyWithRules(assigneeId, "TICKET_ASSIGNED", t.getProjectId(), "Ticket " + t.getTicketNumber() + " assigned to you",
                    t.getTitle() + " (" + t.getPriority() + ")", "CRITICAL".equals(t.getPriority()) ? "CRITICAL" : "INFO", "Ticket", t.getId());
        }
        events.publishEvent(new TicketEvent("TICKET_ASSIGNED", t.getId(), actorId, String.valueOf(assigneeId)));
    }

    @Transactional
    public TicketDtos.TicketDetail changeStatus(AuthUser me, UUID id, TicketDtos.StatusRequest r) {
        Ticket t = load(me, id);
        String to = r.status();
        if (to.equals(t.getStatus())) return detail(t);
        if (!TRANSITIONS.getOrDefault(t.getStatus(), Set.of()).contains(to))
            throw ApiException.badRequest("Transition " + t.getStatus() + " → " + to + " is not allowed");
        if (("RESOLVED".equals(to) || "REOPENED".equals(to) || "PENDING".equals(to)) && (r.note() == null || r.note().isBlank()))
            throw ApiException.badRequest("A note is required when moving a ticket to " + to);
        if ("IN_PROGRESS".equals(to) && t.getAssigneeId() == null) t.setAssigneeId(me.id());
        applyStatus(t, to, me.id(), r.note());
        return detail(t);
    }

    public void applyStatus(Ticket t, String to, UUID actorId, String note) {
        Instant now = Instant.now(clock);
        String from = t.getStatus();
        Project p = projects.findById(t.getProjectId()).orElseThrow();
        SlaPolicy policy = t.getSlaPolicyId() == null ? null : sla.policyFor(t.getProjectId(), t.getPriority()).orElse(null);
        // pause / resume SLA clock around PENDING
        if ("PENDING".equals(to) && policy != null && policy.isPauseOnPending() && t.getSlaPausedAt() == null) {
            t.setSlaPausedAt(now);
        } else if ("PENDING".equals(from) && t.getSlaPausedAt() != null) {
            BusinessTimeCalculator calc = sla.calculator(policy != null ? policy : new SlaPolicy(), p.getCalendarId());
            long paused = calc.businessSecondsBetween(t.getSlaPausedAt(), now);
            t.setSlaPausedSeconds(t.getSlaPausedSeconds() + paused);
            if (t.getResolutionDueAt() != null) t.setResolutionDueAt(calc.addBusinessSeconds(t.getResolutionDueAt(), paused));
            if (t.getFirstResponseAt() == null && t.getResponseDueAt() != null) t.setResponseDueAt(calc.addBusinessSeconds(t.getResponseDueAt(), paused));
            t.setSlaPausedAt(null);
        }
        if (t.getFirstResponseAt() == null && Set.of("IN_PROGRESS", "PENDING", "RESOLVED").contains(to)) markFirstResponse(t, now);
        switch (to) {
            case "RESOLVED" -> {
                t.setResolvedAt(now);
                if (t.getResolutionDueAt() != null && now.isAfter(t.getResolutionDueAt())) t.setResolutionBreached(true);
            }
            case "CLOSED" -> t.setClosedAt(now);
            case "REOPENED" -> {
                t.setReopenedCount(t.getReopenedCount() + 1);
                t.setResolvedAt(null);
                t.setClosedAt(null);
            }
            default -> { }
        }
        t.setStatus(to);
        t.setUpdatedAt(now);
        record(t, actorId, "STATUS_CHANGED", "status", from, to, note);
        if (note != null && !note.isBlank()) addCommentInternal(t, actorId, "[" + to + "] " + note, false);
        if (t.getAssigneeId() != null && !t.getAssigneeId().equals(actorId) && Set.of("REOPENED", "CLOSED").contains(to)) {
            notifications.notify(t.getAssigneeId(), "TICKET_" + to, "Ticket " + t.getTicketNumber() + " " + to.toLowerCase(), t.getTitle(), "INFO", "Ticket", t.getId());
        }
        events.publishEvent(new TicketEvent("TICKET_STATUS_CHANGED", t.getId(), actorId, from + "->" + to));
    }

    @Transactional
    public TicketDtos.TicketDetail escalate(AuthUser me, UUID id, String reason) {
        Ticket t = load(me, id);
        escalateInternal(t, me.id(), reason, t.getEscalationLevel() + 1);
        return detail(t);
    }

    public void escalateInternal(Ticket t, UUID actorId, String reason, int level) {
        if (level <= t.getEscalationLevel() || level > 5) return;
        int old = t.getEscalationLevel();
        t.setEscalationLevel(level);
        t.setUpdatedAt(Instant.now(clock));
        record(t, actorId, "ESCALATED", "escalationLevel", String.valueOf(old), String.valueOf(level), reason);
        Project p = projects.findById(t.getProjectId()).orElseThrow();
        Set<UUID> notify = new HashSet<>();
        if (p.getTeamLeaderId() != null) notify.add(p.getTeamLeaderId());
        if (level >= 2 && p.getManagerId() != null) notify.add(p.getManagerId());
        if (t.getAssigneeId() != null) notify.add(t.getAssigneeId());
        notifications.notifyAllWithRules(notify, "TICKET_ESCALATED", t.getProjectId(), "Ticket " + t.getTicketNumber() + " escalated to L" + level,
                reason, level >= 2 ? "CRITICAL" : "WARNING", "Ticket", t.getId());
    }

    @Transactional
    public TicketDtos.CommentDto comment(AuthUser me, UUID id, TicketDtos.CommentRequest r) {
        Ticket t = load(me, id);
        TicketComment c = addCommentInternal(t, me.id(), r.body(), r.internal());
        if (t.getFirstResponseAt() == null && !r.internal() && me.id().equals(t.getAssigneeId())) markFirstResponse(t, c.getCreatedAt());
        t.setUpdatedAt(Instant.now(clock));
        return new TicketDtos.CommentDto(c.getId(), c.getAuthorId(), userName(c.getAuthorId()), c.getBody(), c.isInternal(), c.getCreatedAt());
    }

    TicketComment addCommentInternal(Ticket t, UUID actorId, String body, boolean internal) {
        TicketComment c = new TicketComment();
        c.setTicketId(t.getId());
        c.setAuthorId(actorId);
        c.setBody(body);
        c.setInternal(internal);
        c.setCreatedAt(Instant.now(clock));
        comments.save(c);
        record(t, actorId, "COMMENTED", null, null, null, internal ? "Internal note added" : "Comment added");
        return c;
    }

    private void markFirstResponse(Ticket t, Instant at) {
        t.setFirstResponseAt(at);
        if (t.getResponseDueAt() != null && at.isAfter(t.getResponseDueAt())) t.setResponseBreached(true);
        record(t, null, "FIRST_RESPONSE", null, null, at.toString(), null);
    }

    // ------------------------------------------------------------------ SLA

    private void applySla(Ticket t, Project p, Instant from) {
        Optional<SlaPolicy> policy = sla.policyFor(p.getId(), t.getPriority());
        if (policy.isEmpty()) {
            t.setSlaPolicyId(null);
            t.setResponseDueAt(null);
            t.setResolutionDueAt(null);
            return;
        }
        SlaPolicy sp = policy.get();
        BusinessTimeCalculator calc = sla.calculator(sp, p.getCalendarId());
        t.setSlaPolicyId(sp.getId());
        t.setResponseDueAt(calc.addBusinessSeconds(from, sp.getResponseMinutes() * 60L + t.getSlaPausedSeconds()));
        t.setResolutionDueAt(calc.addBusinessSeconds(from, sp.getResolutionMinutes() * 60L + t.getSlaPausedSeconds()));
    }

    TicketDtos.SlaInfo slaInfo(Ticket t, Instant now) {
        if (t.getResolutionDueAt() == null) return new TicketDtos.SlaInfo(null, null, t.getFirstResponseAt(), false, false, false, "NO_SLA", null, null);
        boolean paused = t.getSlaPausedAt() != null;
        String state;
        Long remaining = null;
        if (!t.isOpen()) state = t.isResolutionBreached() ? "BREACHED" : "MET";
        else if (paused) state = "PAUSED";
        else {
            remaining = Duration.between(now, t.getResolutionDueAt()).toSeconds();
            long total = Duration.between(t.getCreatedAt(), t.getResolutionDueAt()).toSeconds();
            state = remaining < 0 || t.isResolutionBreached() || t.isResponseBreached() ? "BREACHED"
                    : total > 0 && remaining < total * 0.25 ? "AT_RISK" : "ON_TRACK";
        }
        String policyName = t.getSlaPolicyId() == null ? null : jdbc.queryForList("select name from sla_policies where id = ?", String.class, t.getSlaPolicyId())
                .stream().findFirst().orElse(null);
        return new TicketDtos.SlaInfo(t.getResponseDueAt(), t.getResolutionDueAt(), t.getFirstResponseAt(), t.isResponseBreached(),
                t.isResolutionBreached(), paused, state, remaining, policyName);
    }

    // ------------------------------------------------------------------ helpers

    private String nextNumber(Project p) {
        Long n = jdbc.queryForObject("select nextval('ticket_number_seq')", Long.class);
        return p.getCode() + "-" + n;
    }

    private void validateCategory(UUID projectId, UUID categoryId, UUID subId) {
        if (categoryId != null) {
            var c = categories.findById(categoryId).orElseThrow(() -> ApiException.badRequest("Category not found"));
            if (c.getProjectId() != null && !c.getProjectId().equals(projectId)) throw ApiException.badRequest("Category belongs to another project");
        }
        if (subId != null) {
            var s = categories.findById(subId).orElseThrow(() -> ApiException.badRequest("Subcategory not found"));
            if (categoryId == null || !categoryId.equals(s.getParentId())) throw ApiException.badRequest("Subcategory does not belong to the category");
        }
    }

    public void record(Ticket t, UUID actorId, String action, String field, String oldV, String newV, String note) {
        TicketHistory h = new TicketHistory();
        h.setTicketId(t.getId());
        h.setActorId(actorId);
        h.setActorName(actorId == null ? "System" : userName(actorId));
        h.setAction(action);
        h.setField(field);
        h.setOldValue(trim(oldV));
        h.setNewValue(trim(newV));
        h.setNote(note);
        h.setCreatedAt(Instant.now(clock));
        history.save(h);
    }

    private static String trim(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
    }

    private String userName(UUID id) {
        return id == null ? null : users.findById(id).map(User::getFullName).orElse(id.toString());
    }

    private String name(UUID categoryId) {
        return categoryId == null ? null : categories.findById(categoryId).map(c -> c.getName()).orElse(null);
    }

    private String writeFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid custom fields");
        }
    }

    private Map<String, Object> readFields(String json) {
        if (json == null) return Map.of();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private record Lookup(Map<UUID, String> projects, Map<UUID, String> clients, Map<UUID, String> users, Map<UUID, String> teams,
                          Map<UUID, String> categories) {}

    private Lookup lookup(Collection<Ticket> list) {
        Map<UUID, String> p = new HashMap<>(), c = new HashMap<>(), u = new HashMap<>(), tm = new HashMap<>(), cat = new HashMap<>();
        Set<UUID> pIds = new HashSet<>(), cIds = new HashSet<>(), uIds = new HashSet<>(), tIds = new HashSet<>(), catIds = new HashSet<>();
        for (Ticket t : list) {
            pIds.add(t.getProjectId());
            cIds.add(t.getClientId());
            if (t.getAssigneeId() != null) uIds.add(t.getAssigneeId());
            if (t.getTeamId() != null) tIds.add(t.getTeamId());
            if (t.getCategoryId() != null) catIds.add(t.getCategoryId());
        }
        projects.findAllById(pIds).forEach(x -> p.put(x.getId(), x.getName()));
        clients.findAllById(cIds).forEach(x -> c.put(x.getId(), x.getName()));
        users.findAllById(uIds).forEach(x -> u.put(x.getId(), x.getFullName()));
        teams.findAllById(tIds).forEach(x -> tm.put(x.getId(), x.getName()));
        categories.findAllById(catIds).forEach(x -> cat.put(x.getId(), x.getName()));
        return new Lookup(p, c, u, tm, cat);
    }

    TicketDtos.TicketSummary summary(Ticket t, Lookup l, Instant now) {
        Instant end = t.isOpen() ? now : (t.getResolvedAt() != null ? t.getResolvedAt() : t.getUpdatedAt());
        return new TicketDtos.TicketSummary(t.getId(), t.getTicketNumber(), t.getTitle(), t.getStatus(), t.getPriority(), t.getProjectId(),
                l.projects().get(t.getProjectId()), t.getClientId(), l.clients().get(t.getClientId()), t.getAssigneeId(),
                l.users().get(t.getAssigneeId()), t.getTeamId(), l.teams().get(t.getTeamId()), l.categories().get(t.getCategoryId()),
                t.getSource(), t.getEscalationLevel(), t.getCreatedAt(), t.getUpdatedAt(), Duration.between(t.getCreatedAt(), end).toHours(),
                slaInfo(t, now));
    }

    TicketDtos.TicketDetail detail(Ticket t) {
        Instant now = Instant.now(clock);
        Lookup l = lookup(List.of(t));
        Map<UUID, String> names = new HashMap<>();
        List<TicketComment> cs = comments.findByTicketIdOrderByCreatedAtAsc(t.getId());
        List<TicketAttachment> as = attachments.findByTicketIdOrderByCreatedAtAsc(t.getId());
        Set<UUID> ids = new HashSet<>();
        cs.forEach(c -> { if (c.getAuthorId() != null) ids.add(c.getAuthorId()); });
        as.forEach(a -> { if (a.getUploadedBy() != null) ids.add(a.getUploadedBy()); });
        users.findAllById(ids).forEach(u -> names.put(u.getId(), u.getFullName()));
        return new TicketDtos.TicketDetail(summary(t, l, now), t.getDescription(), t.getCategoryId(), t.getSubcategoryId(), t.getReporterName(),
                t.getReporterEmail(), t.getExternalSystem(), t.getExternalId(), t.getReopenedCount(), t.getResolvedAt(), t.getClosedAt(),
                readFields(t.getCustomFields()),
                cs.stream().map(c -> new TicketDtos.CommentDto(c.getId(), c.getAuthorId(), names.get(c.getAuthorId()), c.getBody(), c.isInternal(), c.getCreatedAt())).toList(),
                as.stream().map(a -> new TicketDtos.AttachmentDto(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), names.get(a.getUploadedBy()), a.getCreatedAt())).toList(),
                history.findByTicketIdOrderByCreatedAtAsc(t.getId()).stream().map(h -> new TicketDtos.HistoryDto(h.getId(), h.getAction(), h.getField(),
                        h.getOldValue(), h.getNewValue(), h.getNote(), h.getActorName(), h.getCreatedAt())).toList(),
                new ArrayList<>(new TreeSet<>(TRANSITIONS.getOrDefault(t.getStatus(), Set.of()))));
    }

    void touch(Ticket t) {
        t.setUpdatedAt(Instant.now(clock));
    }

    void audit(String action, UUID ticketId, String details) {
        audit.log(action, "Ticket", ticketId, details);
    }
}
