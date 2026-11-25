package com.suma.dashboard;

import com.suma.attendance.AttendanceDtos;
import com.suma.attendance.AttendanceReportService;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Operational dashboard metrics computed from live database records, restricted to the caller's visible projects. */
@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService scope;
    private final AttendanceReportService attendance;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, AccessScopeService scope, AttendanceReportService attendance, Clock clock) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.attendance = attendance;
        this.clock = clock;
    }

    public record Filter(LocalDate from, LocalDate to, UUID clientId, UUID projectId, UUID teamId, UUID assigneeId, String status) {}

    public record Kpis(long activeClients, long totalProjects, long ticketsReceived, long ticketsResolved, long pending, long overdue,
                       long slaBreaches, double slaCompliancePercent, double avgResolutionHours, long criticalOpen) {}

    public record Summary(Filter filter, Kpis kpis, List<Map<String, Object>> trend, List<Map<String, Object>> priorityDistribution,
                          List<Map<String, Object>> statusDistribution, List<Map<String, Object>> teamPerformance,
                          List<Map<String, Object>> topAssignees, AttendanceDtos.DashboardView workforce) {}

    @Transactional(readOnly = true)
    public Summary summary(AuthUser me, Filter f0) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = f0.to() == null ? today : f0.to();
        LocalDate from = f0.from() == null ? to.minusDays(29) : f0.from();
        Filter f = new Filter(from, to, f0.clientId(), f0.projectId(), f0.teamId(), f0.assigneeId(), f0.status());
        Set<UUID> projects = scope.visibleProjectIds(me);
        boolean engineer = Set.of("L1", "L2", "L3").contains(me.role());
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .addValue("now", Timestamp.from(Instant.now(clock)))
                .addValue("restrict", projects != null)
                .addValue("projects", projects == null || projects.isEmpty() ? List.of(new UUID(0, 0)) : projects)
                .addValue("client", f.clientId()).addValue("project", f.projectId()).addValue("team", f.teamId())
                .addValue("assignee", engineer ? me.id() : f.assigneeId()).addValue("status", f.status());
        String where = """
                (:restrict = false or t.project_id in (:projects))
                and (cast(:client as uuid) is null or t.client_id = :client)
                and (cast(:project as uuid) is null or t.project_id = :project)
                and (cast(:team as uuid) is null or t.team_id = :team)
                and (cast(:assignee as uuid) is null or t.assignee_id = :assignee)
                and (cast(:status as varchar) is null or t.status = :status)
                """;
        Map<String, Object> k = jdbc.queryForMap("""
                select
                  count(*) filter (where t.created_at >= :from and t.created_at < :to) as received,
                  count(*) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                  count(*) filter (where t.status not in ('RESOLVED','CLOSED')) as pending,
                  count(*) filter (where t.status not in ('RESOLVED','CLOSED') and t.resolution_due_at < :now) as overdue,
                  count(*) filter (where (t.response_breached or t.resolution_breached) and t.created_at >= :from and t.created_at < :to) as breaches,
                  count(*) filter (where t.resolution_due_at is not null and t.resolved_at >= :from and t.resolved_at < :to) as sla_resolved,
                  count(*) filter (where t.resolution_due_at is not null and t.resolved_at >= :from and t.resolved_at < :to and not t.resolution_breached and not t.response_breached) as sla_met,
                  coalesce(avg(extract(epoch from (t.resolved_at - t.created_at)) / 3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0) as avg_hours,
                  count(*) filter (where t.status not in ('RESOLVED','CLOSED') and t.priority = 'CRITICAL') as critical_open
                from tickets t where """ + where, p);
        Long activeClients = jdbc.queryForObject("""
                select count(*) from clients c where c.status = 'ACTIVE' and (:restrict = false or c.id in (select client_id from projects where id in (:projects)))
                and (cast(:client as uuid) is null or c.id = :client)
                """, p, Long.class);
        Long totalProjects = jdbc.queryForObject("""
                select count(*) from projects pr where (:restrict = false or pr.id in (:projects))
                and (cast(:client as uuid) is null or pr.client_id = :client) and (cast(:project as uuid) is null or pr.id = :project)
                """, p, Long.class);
        long slaResolved = num(k.get("sla_resolved"));
        Kpis kpis = new Kpis(activeClients == null ? 0 : activeClients, totalProjects == null ? 0 : totalProjects, num(k.get("received")),
                num(k.get("resolved")), num(k.get("pending")), num(k.get("overdue")), num(k.get("breaches")),
                slaResolved == 0 ? 100.0 : Math.round(num(k.get("sla_met")) * 1000.0 / slaResolved) / 10.0,
                Math.round(((Number) k.get("avg_hours")).doubleValue() * 10) / 10.0, num(k.get("critical_open")));
        List<Map<String, Object>> trend = jdbc.queryForList("""
                with days as (select generate_series(cast(:from as timestamp), cast(:to as timestamp) - interval '1 day', interval '1 day') as d)
                select to_char(d, 'YYYY-MM-DD') as date,
                  (select count(*) from tickets t where t.created_at >= d and t.created_at < d + interval '1 day' and """ + where + """
                  ) as received,
                  (select count(*) from tickets t where t.resolved_at >= d and t.resolved_at < d + interval '1 day' and """ + where + """
                  ) as resolved,
                  (select count(*) from tickets t where (t.response_breached or t.resolution_breached) and t.created_at >= d and t.created_at < d + interval '1 day' and """ + where + """
                  ) as breached
                from days order by d
                """, p);
        List<Map<String, Object>> priority = jdbc.queryForList("""
                select c.code as priority, c.label, c.color, count(t.id) as count
                from ticket_priority_config c left join tickets t on t.priority = c.code and t.created_at >= :from and t.created_at < :to and """ + where + """
                group by c.code, c.label, c.color, c.sort_order order by c.sort_order
                """, p);
        List<Map<String, Object>> status = jdbc.queryForList("""
                select c.code as status, c.label, c.color, count(t.id) as count
                from ticket_status_config c left join tickets t on t.status = c.code and t.created_at >= :from and t.created_at < :to and """ + where + """
                group by c.code, c.label, c.color, c.sort_order order by c.sort_order
                """, p);
        List<Map<String, Object>> teams = jdbc.queryForList("""
                select coalesce(tm.name, 'Unassigned team') as team,
                  count(*) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                  count(*) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved,
                  count(*) filter (where t.response_breached or t.resolution_breached) as breached,
                  round(coalesce(avg(extract(epoch from (t.resolved_at - t.created_at)) / 3600.0) filter (where t.resolved_at >= :from and t.resolved_at < :to), 0)::numeric, 1) as avg_resolution_hours
                from tickets t left join teams tm on tm.id = t.team_id
                where (t.created_at >= :from or t.status not in ('RESOLVED','CLOSED')) and """ + where + """
                group by tm.name order by open desc limit 12
                """, p);
        List<Map<String, Object>> assignees = jdbc.queryForList("""
                select u.full_name as name, count(*) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                  count(*) filter (where t.resolved_at >= :from and t.resolved_at < :to) as resolved
                from tickets t join users u on u.id = t.assignee_id where """ + where + """
                group by u.full_name order by resolved desc, open desc limit 10
                """, p);
        AttendanceDtos.DashboardView workforce = null;
        try {
            workforce = attendance.dashboard(me, null, new AttendanceReportService.Filter(f.projectId(), f.teamId(), engineer ? me.id() : null));
        } catch (RuntimeException ignored) {
            // workforce widget is optional for users without attendance visibility
        }
        return new Summary(f, kpis, trend, priority, status, teams, assignees, workforce);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
