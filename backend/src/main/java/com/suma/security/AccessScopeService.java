package com.suma.security;

import com.suma.common.ApiException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Data-level isolation. Admins see everything; everyone else sees only the clients/projects/users they are linked to.
 * A {@code null} return value means "unrestricted".
 */
@Service
public class AccessScopeService {
    private final NamedParameterJdbcTemplate jdbc;

    public AccessScopeService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<UUID> visibleProjectIds(AuthUser u) {
        if (u.isAdmin()) return null;
        var p = new MapSqlParameterSource("u", u.id());
        List<UUID> ids = jdbc.queryForList("""
                select project_id from project_members where user_id = :u
                union select id from projects where manager_id = :u or team_leader_id = :u or project_lead_id = :u
                union select project_id from teams where team_leader_id = :u and project_id is not null
                union select p.id from projects p join clients c on c.id = p.client_id where c.account_manager_id = :u
                """, p, UUID.class);
        return new HashSet<>(ids);
    }

    public boolean canViewProject(AuthUser u, UUID projectId) {
        Set<UUID> ids = visibleProjectIds(u);
        return ids == null || ids.contains(projectId);
    }

    public void requireProject(AuthUser u, UUID projectId) {
        if (!canViewProject(u, projectId)) throw ApiException.forbidden("You do not have access to this project");
    }

    public Set<UUID> visibleClientIds(AuthUser u) {
        if (u.isAdmin()) return null;
        var p = new MapSqlParameterSource("u", u.id());
        Set<UUID> projects = visibleProjectIds(u);
        p.addValue("projects", projects.isEmpty() ? Set.of(new UUID(0, 0)) : projects);
        return new HashSet<>(jdbc.queryForList("""
                select client_id from projects where id in (:projects)
                union select id from clients where account_manager_id = :u
                """, p, UUID.class));
    }

    /** Projects for which the user is the designated Project Lead. */
    public Set<UUID> projectsLedBy(UUID userId) {
        return new HashSet<>(jdbc.queryForList("""
                select id from projects where project_lead_id = :u
                union select project_id from project_members where user_id = :u and designation = 'PROJECT_LEAD'
                """, new MapSqlParameterSource("u", userId), UUID.class));
    }

    /**
     * Users whose attendance the caller may view. Organisation viewers see everyone; team/project supervisors see their
     * team and project members; Project Leads see members of projects they lead; everyone sees themself.
     */
    public Set<UUID> attendanceVisibleUserIds(AuthUser u) {
        if (u.isAdmin() || u.has("ATTENDANCE_ORG_VIEW")) return null;
        Set<UUID> result = new HashSet<>();
        result.add(u.id());
        var p = new MapSqlParameterSource("u", u.id());
        if (u.has("ATTENDANCE_TEAM_VIEW")) {
            result.addAll(jdbc.queryForList("""
                    select tm.user_id from team_members tm join teams t on t.id = tm.team_id where t.team_leader_id = :u
                    union select pm.user_id from project_members pm join projects pr on pr.id = pm.project_id
                      where pr.manager_id = :u or pr.team_leader_id = :u
                    union select pm.user_id from project_members pm join projects pr on pr.id = pm.project_id
                      join clients c on c.id = pr.client_id where c.account_manager_id = :u
                    """, p, UUID.class));
        }
        Set<UUID> led = projectsLedBy(u.id());
        if (!led.isEmpty()) {
            result.addAll(jdbc.queryForList("select user_id from project_members where project_id in (:p)",
                    new MapSqlParameterSource("p", led), UUID.class));
        }
        return result;
    }

    public boolean canViewAttendanceOf(AuthUser u, UUID userId) {
        Set<UUID> ids = attendanceVisibleUserIds(u);
        return ids == null || ids.contains(userId);
    }

    /** Projects whose attendance reports the caller may view. null = all. */
    public Set<UUID> attendanceVisibleProjectIds(AuthUser u) {
        if (u.isAdmin() || u.has("ATTENDANCE_ORG_VIEW")) return null;
        Set<UUID> result = new HashSet<>(projectsLedBy(u.id()));
        if (u.has("ATTENDANCE_TEAM_VIEW") || u.has("ATTENDANCE_REPORT_VIEW")) {
            Set<UUID> visible = visibleProjectIds(u);
            if (visible != null) result.addAll(visible);
        }
        return result;
    }
}
