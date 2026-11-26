package com.suma.ticket;

import com.suma.notification.NotificationService;
import com.suma.project.Project;
import com.suma.project.ProjectRepository;
import com.suma.settings.SettingsService;
import com.suma.sla.EscalationRule;
import com.suma.sla.SlaService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Breach detection, percentage-based escalation, pending reminders and auto-closure of resolved tickets. */
@Service
public class SlaMonitorService {
    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final SlaService sla;
    private final ProjectRepository projects;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SlaMonitorService(TicketRepository tickets, TicketService ticketService, SlaService sla, ProjectRepository projects,
                             NotificationService notifications, SettingsService settings, ApplicationEventPublisher events,
                             TransactionTemplate tx, JdbcTemplate jdbc, Clock clock) {
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.sla = sla;
        this.projects = projects;
        this.notifications = notifications;
        this.settings = settings;
        this.events = events;
        this.tx = tx;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public int detectBreaches() {
        Instant now = Instant.now(clock);
        int n = 0;
        for (Ticket t : tickets.responseBreaches(now)) {
            tx.executeWithoutResult(s -> tickets.findById(t.getId()).ifPresent(x -> {
                if (x.isResponseBreached() || x.getFirstResponseAt() != null) return;
                x.setResponseBreached(true);
                ticketService.record(x, null, "SLA_BREACHED", "response", null, x.getResponseDueAt().toString(), "First response SLA breached");
                alert(x, "response");
                escalateFor(x, "RESPONSE_BREACH", 100);
                events.publishEvent(new TicketEvent("SLA_BREACHED", x.getId(), null, "RESPONSE"));
            }));
            n++;
        }
        for (Ticket t : tickets.resolutionBreaches(now)) {
            tx.executeWithoutResult(s -> tickets.findById(t.getId()).ifPresent(x -> {
                if (x.isResolutionBreached()) return;
                x.setResolutionBreached(true);
                ticketService.record(x, null, "SLA_BREACHED", "resolution", null, x.getResolutionDueAt().toString(), "Resolution SLA breached");
                alert(x, "resolution");
                escalateFor(x, "RESOLUTION_BREACH", 100);
                events.publishEvent(new TicketEvent("SLA_BREACHED", x.getId(), null, "RESOLUTION"));
            }));
            n++;
        }
        // percentage-based escalations
        for (Ticket t : tickets.openWithSla()) {
            long total = Duration.between(t.getCreatedAt(), t.getResolutionDueAt()).toSeconds();
            if (total <= 0) continue;
            int pct = (int) Math.min(100, Duration.between(t.getCreatedAt(), now).toSeconds() * 100 / total);
            List<EscalationRule> rules = sla.escalationRules(t.getProjectId(), t.getPriority());
            boolean applies = rules.stream().anyMatch(r -> "RESOLUTION_PERCENT".equals(r.getTriggerType()) && r.getThresholdPercent() != null
                    && pct >= r.getThresholdPercent() && r.getEscalationLevel() > t.getEscalationLevel());
            if (applies) {
                tx.executeWithoutResult(s -> tickets.findById(t.getId()).ifPresent(x -> escalateFor(x, "RESOLUTION_PERCENT", pct)));
                n++;
            }
        }
        return n;
    }

    private void escalateFor(Ticket t, String trigger, int pct) {
        for (EscalationRule r : sla.escalationRules(t.getProjectId(), t.getPriority())) {
            if (!trigger.equals(r.getTriggerType())) continue;
            if ("RESOLUTION_PERCENT".equals(trigger) && (r.getThresholdPercent() == null || pct < r.getThresholdPercent())) continue;
            if (r.getEscalationLevel() <= t.getEscalationLevel()) continue;
            ticketService.escalateInternal(t, null, "Automatic escalation: " + trigger + (r.getThresholdPercent() != null ? " " + r.getThresholdPercent() + "%" : ""), r.getEscalationLevel());
            if (r.getNotifyUserId() != null) {
                notifications.notifyWithRules(r.getNotifyUserId(), "TICKET_ESCALATED", t.getProjectId(), "Ticket " + t.getTicketNumber() + " escalated (L" + r.getEscalationLevel() + ")",
                        t.getTitle(), "WARNING", "Ticket", t.getId());
            }
            if (r.getNotifyRole() != null) {
                List<UUID> ids = jdbc.queryForList("select u.id from users u join roles r on r.id = u.role_id join project_members pm on pm.user_id = u.id "
                        + "where r.code = ? and pm.project_id = ? and u.status = 'ACTIVE'", UUID.class, r.getNotifyRole(), t.getProjectId());
                notifications.notifyAllWithRules(ids, "TICKET_ESCALATED", t.getProjectId(), "Ticket " + t.getTicketNumber() + " escalated (L" + r.getEscalationLevel() + ")",
                        t.getTitle(), "WARNING", "Ticket", t.getId());
            }
        }
    }

    private void alert(Ticket t, String type) {
        Project p = projects.findById(t.getProjectId()).orElseThrow();
        Set<UUID> notify = new HashSet<>();
        if (t.getAssigneeId() != null) notify.add(t.getAssigneeId());
        if (p.getTeamLeaderId() != null) notify.add(p.getTeamLeaderId());
        if (p.getManagerId() != null) notify.add(p.getManagerId());
        for (UUID u : notify) {
            notifications.notifyWithRules(u, "SLA_BREACH", t.getProjectId(), "SLA breach: " + t.getTicketNumber(),
                    t.getTitle() + " breached its " + type + " SLA", "CRITICAL", "Ticket", t.getId());
        }
    }

    public int pendingReminders() {
        int hours = settings.getInt("ticket.pending_reminder_hours", 24);
        if (hours <= 0) return 0;
        Instant before = Instant.now(clock).minus(Duration.ofHours(hours));
        int n = 0;
        for (Ticket t : tickets.pendingSince(before)) {
            if (t.getAssigneeId() == null) continue;
            Integer recent = jdbc.queryForObject("select count(*) from notifications where entity_id = ? and type = 'TICKET_PENDING_REMINDER' and created_at > ?",
                    Integer.class, t.getId().toString(), java.sql.Timestamp.from(before));
            if (recent != null && recent > 0) continue;
            tx.executeWithoutResult(s -> {
                notifications.notify(t.getAssigneeId(), "TICKET_PENDING_REMINDER", "Pending ticket " + t.getTicketNumber(),
                        "Ticket has been pending for more than " + hours + " hours: " + t.getTitle(), "INFO", "Ticket", t.getId());
                events.publishEvent(new TicketEvent("TICKET_PENDING_REMINDER", t.getId(), null, null));
            });
            n++;
        }
        return n;
    }

    public int autoClose() {
        int days = settings.getInt("ticket.auto_close_resolved_days", 7);
        if (days <= 0) return 0;
        int n = 0;
        for (Ticket t : tickets.resolvedBefore(Instant.now(clock).minus(Duration.ofDays(days)))) {
            tx.executeWithoutResult(s -> tickets.findById(t.getId()).filter(x -> "RESOLVED".equals(x.getStatus()))
                    .ifPresent(x -> ticketService.applyStatus(x, "CLOSED", null, "Automatically closed after " + days + " days in RESOLVED")));
            n++;
        }
        return n;
    }
}
