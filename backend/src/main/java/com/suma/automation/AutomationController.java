package com.suma.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.CurrentUser;
import com.suma.ticket.TicketRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/automation")
@Tag(name = "Workflow & Automation")
public class AutomationController {
    private final AutomationRuleRepository rules;
    private final AutomationExecutionRepository executions;
    private final ApprovalRequestRepository approvals;
    private final TicketRepository tickets;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public AutomationController(AutomationRuleRepository rules, AutomationExecutionRepository executions, ApprovalRequestRepository approvals,
                                TicketRepository tickets, AuditService audit, ObjectMapper mapper) {
        this.rules = rules;
        this.executions = executions;
        this.approvals = approvals;
        this.tickets = tickets;
        this.audit = audit;
        this.mapper = mapper;
    }

    public record RuleRequest(String name, String description, String triggerEvent, Object conditions, Object actions, UUID projectId,
                              Integer priority, Boolean stopProcessing, Boolean active) {}

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public List<AutomationRule> list() {
        return rules.findAll(org.springframework.data.domain.Sort.by("priority", "name"));
    }

    @GetMapping("/triggers")
    public Map<String, Object> triggers() {
        return Map.of("triggers", new TreeSet<>(RuleEngine.TRIGGERS),
                "fields", List.of("priority", "status", "projectId", "clientId", "categoryId", "source", "title", "description", "text", "assigneeId", "teamId", "escalationLevel"),
                "operators", List.of("eq", "neq", "in", "contains", "empty", "notEmpty", "gte"),
                "actions", List.of("ASSIGN_USER", "ASSIGN_LEAST_LOADED", "SET_PRIORITY", "SET_CATEGORY", "AUTO_CATEGORIZE", "NOTIFY_USER",
                        "NOTIFY_ROLE", "ESCALATE", "ADD_COMMENT", "REQUEST_APPROVAL", "SET_STATUS", "RPA_TRIGGER"),
                "scheduledAttendanceAutomations", List.of("Break reminders", "Break limit follow-ups", "Idle detection notifications",
                        "Attendance exception notifications", "Missing logout notifications", "Daily summary generation",
                        "Monthly report generation & delivery", "Failed email retries"));
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    @Transactional
    public AutomationRule create(@RequestBody RuleRequest r) {
        return save(new AutomationRule(), r, true);
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    @Transactional
    public AutomationRule update(@PathVariable UUID id, @RequestBody RuleRequest r) {
        return save(rules.findById(id).orElseThrow(() -> ApiException.notFound("Rule")), r, false);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    @Transactional
    public void delete(@PathVariable UUID id) {
        rules.deleteById(id);
        audit.log("AUTOMATION_RULE_DELETED", "AutomationRule", id, null);
    }

    private AutomationRule save(AutomationRule a, RuleRequest r, boolean create) {
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Name required");
        if (!RuleEngine.TRIGGERS.contains(r.triggerEvent())) throw ApiException.badRequest("Unknown trigger " + r.triggerEvent());
        try {
            String conds = mapper.writeValueAsString(r.conditions() == null ? List.of() : r.conditions());
            String acts = mapper.writeValueAsString(r.actions() == null ? List.of() : r.actions());
            List<?> condList = mapper.readValue(conds, List.class);
            List<?> actList = mapper.readValue(acts, List.class);
            for (Object c : condList) {
                Map<?, ?> m = (Map<?, ?>) c;
                RuleEngine.evaluate("x", String.valueOf(m.get("op")), m.get("value") == null ? "x" : m.get("value")); // validates operator
            }
            if (actList.isEmpty()) throw ApiException.badRequest("At least one action is required");
            a.setConditions(conds);
            a.setActions(acts);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid rule definition: " + e.getMessage());
        }
        a.setName(r.name());
        a.setDescription(r.description());
        a.setTriggerEvent(r.triggerEvent());
        a.setProjectId(r.projectId());
        if (r.priority() != null) a.setPriority(r.priority());
        if (r.stopProcessing() != null) a.setStopProcessing(r.stopProcessing());
        if (r.active() != null) a.setActive(r.active());
        if (create) a.setCreatedBy(CurrentUser.id());
        a.setUpdatedAt(Instant.now());
        rules.save(a);
        audit.log(create ? "AUTOMATION_RULE_CREATED" : "AUTOMATION_RULE_UPDATED", "AutomationRule", a.getId(), a.getName());
        return a;
    }

    @GetMapping("/executions")
    @PreAuthorize("hasAuthority('AUTOMATION_MANAGE')")
    public PageResponse<AutomationExecution> executions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(executions.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200))));
    }

    // ---------- approvals ----------
    @GetMapping("/approvals")
    public List<ApprovalRequest> myApprovals(@RequestParam(defaultValue = "PENDING") String status) {
        var me = CurrentUser.get();
        if (me.isAdmin() && "ALL".equals(status)) return approvals.findTop100ByOrderByCreatedAtDesc();
        return approvals.findByApproverIdAndStatusOrderByCreatedAtDesc(me.id(), status);
    }

    @PostMapping("/approvals/{id}/decide")
    @Transactional
    public ApprovalRequest decide(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        var me = CurrentUser.get();
        ApprovalRequest a = approvals.findById(id).orElseThrow(() -> ApiException.notFound("Approval"));
        if (!me.id().equals(a.getApproverId()) && !me.isAdmin()) throw ApiException.forbidden("Not the approver");
        if (!"PENDING".equals(a.getStatus())) throw ApiException.conflict("Already decided");
        String decision = body.get("decision");
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) throw ApiException.badRequest("decision must be APPROVED or REJECTED");
        a.setStatus(decision);
        a.setDecisionNote(body.get("note"));
        a.setDecidedAt(Instant.now());
        audit.log("APPROVAL_" + decision, "Ticket", a.getTicketId(), a.getDecisionNote());
        tickets.findById(a.getTicketId()).ifPresent(t -> t.setUpdatedAt(Instant.now()));
        return a;
    }
}
