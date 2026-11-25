package com.suma.report;

import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Operational ticket reports: productivity, SLA compliance, pending, ageing, client / project / team, period summaries. */
@Service
public class OperationalReportService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService scope;
    private final Clock clock;

    public OperationalReportService(NamedParameterJdbcTemplate jdbc, AccessScopeService scope, Clock clock) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.clock = clock;
    }

    public record Report(String type, String title, LocalDate from, LocalDate to, List<String> columns, List<Map<String, Object>> rows) {}

    private MapSqlParameterSource params(AuthUser me, LocalDate from, LocalDate to, UUID projectId, UUID clientId, UUID teamId) {
        Set<UUID> projects = scope.visibleProjectIds(me);
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("now", Timestamp.from(Instant.now(clock)))
                .addValue("restrict", projects != null)
                .addValue("projects", projects == null || projects.isEmpty() ? List.of(new UUID(0, 0)) : projects)
                .addValue("project", projectId).addValue("client", clientId).addValue("team", teamId);
    }

    private static final String SCOPE = """
             (:restrict = false or t.project_id in (:projects)) and (cast(:project as uuid) is null or t.project_id = :project)
             and (cast(:client as uuid) is null or t.client_id = :client) and (cast(:team as uuid) is null or t.team_id = :team)
            """;

    @Transactional(readOnly = true)
    public Report run(AuthUser me, String type, LocalDate from0, LocalDate to0, UUID projectId, UUID clientId, UUID teamId) {
        LocalDate to = to0 == null ? LocalDate.now(clock) : to0;
        LocalDate from = from0 == null ? switch (type) {
            case "daily" -> to;
            case "weekly" -> to.minusDays(6);
            default -> to.minusDays(29);
        } : from0;
        if (from.isAfter(to)) throw new IllegalArgumentException("from must be before to");
        var p = params(me, from, to, projectId, clientId, teamId);
        return switch (type) {
            case "productivity" -> new Report(type, "Employee productivity", from, to,
                    List.of("employeeCode", "employee", "role", "assigned", "resolved", "open", "breached", "avgResolutionHours", "comments"),
                    jdbc.queryForList("""
                            select u.employee_code as "employeeCode", u.full_name as employee, r.code as role,
                              count(t.id) filter (where t.created_at >= :from and t.created_at < :to) as assigned,
                              count(t.id) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                              count(t.id) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                              count(t.id) filter (where (t.response_breached or t.resolution_breached) and t.created_at >= :from and t.created_at < :to) as breached,
                              round(coalesce(avg(extract(epoch from (t.resolved_at - t.created_at))/3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0)::numeric, 1) as "avgResolutionHours",
                              (select count(*) from ticket_comments c where c.author_id = u.id and c.created_at >= :from and c.created_at < :to) as comments
                            from users u join roles r on r.id = u.role_id left join tickets t on t.assignee_id = u.id and """ + SCOPE + """
                            where u.status = 'ACTIVE' and r.code in ('L1','L2','L3','TEAM_LEADER')
                              and (:restrict = false or u.id in (select user_id from project_members where project_id in (:projects)))
                            group by u.id, u.employee_code, u.full_name, r.code order by resolved desc, u.full_name
                            """, p));
            case "sla" -> new Report(type, "SLA compliance", from, to,
                    List.of("project", "priority", "tickets", "responseMet", "responseBreached", "resolutionMet", "resolutionBreached", "compliancePercent"),
                    jdbc.queryForList("""
                            select pr.name as project, t.priority, count(*) as tickets,
                              count(*) filter (where t.first_response_at is not null and not t.response_breached) as "responseMet",
                              count(*) filter (where t.response_breached) as "responseBreached",
                              count(*) filter (where t.resolved_at is not null and not t.resolution_breached) as "resolutionMet",
                              count(*) filter (where t.resolution_breached) as "resolutionBreached",
                              round(100.0 * count(*) filter (where not t.response_breached and not t.resolution_breached) / greatest(count(*), 1), 1) as "compliancePercent"
                            from tickets t join projects pr on pr.id = t.project_id
                            where t.resolution_due_at is not null and t.created_at >= :from and t.created_at < :to and """ + SCOPE + """
                            group by pr.name, t.priority order by pr.name, t.priority
                            """, p));
            case "pending" -> new Report(type, "Pending tickets", from, to,
                    List.of("ticketNumber", "title", "project", "priority", "status", "assignee", "ageHours", "resolutionDueAt", "breached"),
                    jdbc.queryForList("""
                            select t.ticket_number as "ticketNumber", t.title, pr.name as project, t.priority, t.status, u.full_name as assignee,
                              round(extract(epoch from (:now - t.created_at))/3600.0) as "ageHours", t.resolution_due_at as "resolutionDueAt",
                              (t.response_breached or t.resolution_breached) as breached
                            from tickets t join projects pr on pr.id = t.project_id left join users u on u.id = t.assignee_id
                            where t.status not in ('RESOLVED','CLOSED') and """ + SCOPE + """
                            order by t.created_at limit 1000
                            """, p));
            case "ageing" -> new Report(type, "Ticket ageing", from, to,
                    List.of("project", "lt24h", "d1to3", "d3to7", "d7to14", "gt14d", "total"),
                    jdbc.queryForList("""
                            select pr.name as project,
                              count(*) filter (where :now - t.created_at < interval '1 day') as lt24h,
                              count(*) filter (where :now - t.created_at >= interval '1 day' and :now - t.created_at < interval '3 days') as d1to3,
                              count(*) filter (where :now - t.created_at >= interval '3 days' and :now - t.created_at < interval '7 days') as d3to7,
                              count(*) filter (where :now - t.created_at >= interval '7 days' and :now - t.created_at < interval '14 days') as d7to14,
                              count(*) filter (where :now - t.created_at >= interval '14 days') as gt14d, count(*) as total
                            from tickets t join projects pr on pr.id = t.project_id
                            where t.status not in ('RESOLVED','CLOSED') and """ + SCOPE + """
                            group by pr.name order by total desc
                            """, p));
            case "client" -> new Report(type, "Client report", from, to,
                    List.of("client", "projects", "received", "resolved", "open", "breached", "avgResolutionHours"),
                    jdbc.queryForList("""
                            select c.name as client, count(distinct t.project_id) as projects,
                              count(t.id) filter (where t.created_at >= :from and t.created_at < :to) as received,
                              count(t.id) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                              count(t.id) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                              count(t.id) filter (where t.response_breached or t.resolution_breached) as breached,
                              round(coalesce(avg(extract(epoch from (t.resolved_at - t.created_at))/3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0)::numeric, 1) as "avgResolutionHours"
                            from tickets t join clients c on c.id = t.client_id where """ + SCOPE + """
                            group by c.name order by received desc
                            """, p));
            case "project" -> new Report(type, "Project report", from, to,
                    List.of("project", "client", "received", "resolved", "open", "breached", "critical", "avgResolutionHours"),
                    jdbc.queryForList("""
                            select pr.name as project, c.name as client,
                              count(t.id) filter (where t.created_at >= :from and t.created_at < :to) as received,
                              count(t.id) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                              count(t.id) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                              count(t.id) filter (where t.response_breached or t.resolution_breached) as breached,
                              count(t.id) filter (where t.priority = 'CRITICAL' and t.created_at >= :from and t.created_at < :to) as critical,
                              round(coalesce(avg(extract(epoch from (t.resolved_at - t.created_at))/3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0)::numeric, 1) as "avgResolutionHours"
                            from tickets t join projects pr on pr.id = t.project_id join clients c on c.id = pr.client_id where """ + SCOPE + """
                            group by pr.name, c.name order by received desc
                            """, p));
            case "team" -> new Report(type, "Team report", from, to,
                    List.of("team", "members", "received", "resolved", "open", "breached", "avgResolutionHours"),
                    jdbc.queryForList("""
                            select coalesce(tm.name, 'No team') as team, (select count(*) from team_members x where x.team_id = tm.id) as members,
                              count(t.id) filter (where t.created_at >= :from and t.created_at < :to) as received,
                              count(t.id) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                              count(t.id) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                              count(t.id) filter (where t.response_breached or t.resolution_breached) as breached,
                              round(coalesce(avg(extract(epoch from (t.resolved_at - t.created_at))/3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0)::numeric, 1) as "avgResolutionHours"
                            from tickets t left join teams tm on tm.id = t.team_id where """ + SCOPE + """
                            group by tm.id, tm.name order by received desc
                            """, p));
            case "daily", "weekly", "monthly" -> {
                String sq = "with days as (select generate_series(cast(:from as timestamp), cast(:to as timestamp) - interval '1 day', interval '1 day') as d) "
                        + "select to_char(d, 'YYYY-MM-DD') as date, "
                        + "(select count(*) from tickets t where t.created_at >= d and t.created_at < d + interval '1 day' and " + SCOPE + ") as received, "
                        + "(select count(*) from tickets t where t.resolved_at >= d and t.resolved_at < d + interval '1 day' and " + SCOPE + ") as resolved, "
                        + "(select count(*) from tickets t where t.closed_at >= d and t.closed_at < d + interval '1 day' and " + SCOPE + ") as closed, "
                        + "(select count(*) from ticket_history h join tickets t on t.id = h.ticket_id where h.new_value = 'REOPENED' and h.created_at >= d and h.created_at < d + interval '1 day' and " + SCOPE + ") as reopened, "
                        + "(select count(*) from tickets t where (t.response_breached or t.resolution_breached) and t.created_at >= d and t.created_at < d + interval '1 day' and " + SCOPE + ") as breached, "
                        + "(select count(*) from tickets t where t.priority = 'CRITICAL' and t.created_at >= d and t.created_at < d + interval '1 day' and " + SCOPE + ") as critical "
                        + "from days order by d";
                yield new Report(type, Character.toUpperCase(type.charAt(0)) + type.substring(1) + " summary", from, to,
                        List.of("date", "received", "resolved", "closed", "reopened", "breached", "critical"), jdbc.queryForList(sq, p));
            }
            default -> throw new IllegalArgumentException("Unknown report type " + type);
        };
    }
}
