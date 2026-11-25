package com.suma.team;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.project.ProjectRepository;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeamService {
    private final TeamRepository teams;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final AccessScopeService scope;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public TeamService(TeamRepository teams, UserRepository users, ProjectRepository projects, AccessScopeService scope,
                       AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.teams = teams;
        this.users = users;
        this.projects = projects;
        this.scope = scope;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TeamDtos.TeamResponse> list(UUID projectId) {
        AuthUser me = CurrentUser.get();
        Set<UUID> visible = scope.visibleProjectIds(me);
        return teams.findAll().stream()
                .filter(t -> projectId == null || projectId.equals(t.getProjectId()))
                .filter(t -> visible == null || me.id().equals(t.getTeamLeaderId()) || t.getMemberIds().contains(me.id())
                        || (t.getProjectId() != null && visible.contains(t.getProjectId())))
                .sorted(Comparator.comparing(Team::getName))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TeamDtos.TeamResponse get(UUID id) {
        return toResponse(teams.findById(id).orElseThrow(() -> ApiException.notFound("Team")));
    }

    @Transactional
    public TeamDtos.TeamResponse create(TeamDtos.TeamRequest r) {
        if (teams.existsByNameIgnoreCase(r.name())) throw ApiException.conflict("Team name already exists");
        Team t = new Team();
        apply(t, r);
        teams.save(t);
        audit.log("TEAM_CREATED", "Team", t.getId(), t.getName());
        return toResponse(t);
    }

    @Transactional
    public TeamDtos.TeamResponse update(UUID id, TeamDtos.TeamRequest r) {
        Team t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        if (!t.getName().equalsIgnoreCase(r.name()) && teams.existsByNameIgnoreCase(r.name())) throw ApiException.conflict("Team name already exists");
        apply(t, r);
        audit.log("TEAM_UPDATED", "Team", id, t.getName() + " members=" + t.getMemberIds().size());
        return toResponse(t);
    }

    @Transactional
    public void delete(UUID id) {
        Team t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        teams.delete(t);
        audit.log("TEAM_DELETED", "Team", id, t.getName());
    }

    private void apply(Team t, TeamDtos.TeamRequest r) {
        if (r.projectId() != null && !projects.existsById(r.projectId())) throw ApiException.badRequest("Project not found");
        if (r.teamLeaderId() != null && !users.existsById(r.teamLeaderId())) throw ApiException.badRequest("Team leader not found");
        t.setName(r.name().trim());
        t.setDescription(r.description());
        t.setProjectId(r.projectId());
        t.setTeamLeaderId(r.teamLeaderId());
        if (r.active() != null) t.setActive(r.active());
        if (r.memberIds() != null) {
            if (users.findByIdIn(r.memberIds()).size() != r.memberIds().size()) throw ApiException.badRequest("One or more members not found");
            t.getMemberIds().clear();
            t.getMemberIds().addAll(r.memberIds());
        }
    }

    private TeamDtos.TeamResponse toResponse(Team t) {
        Set<UUID> ids = new HashSet<>(t.getMemberIds());
        if (t.getTeamLeaderId() != null) ids.add(t.getTeamLeaderId());
        Map<UUID, User> people = new HashMap<>();
        users.findByIdIn(ids).forEach(u -> people.put(u.getId(), u));
        Map<UUID, Long> open = new HashMap<>();
        if (!t.getMemberIds().isEmpty()) {
            jdbc.query("select assignee_id, count(*) from tickets where assignee_id in (:ids) and status not in ('RESOLVED','CLOSED') group by assignee_id",
                    new MapSqlParameterSource("ids", t.getMemberIds()), rs -> {
                        open.put(rs.getObject(1, UUID.class), rs.getLong(2));
                    });
        }
        List<TeamDtos.MemberDto> members = t.getMemberIds().stream().filter(people::containsKey).map(people::get)
                .sorted(Comparator.comparing(User::getFullName))
                .map(u -> new TeamDtos.MemberDto(u.getId(), u.getEmployeeCode(), u.getFullName(), u.roleCode(), open.getOrDefault(u.getId(), 0L))).toList();
        long resolved = t.getMemberIds().isEmpty() ? 0 : Optional.ofNullable(jdbc.queryForObject(
                "select count(*) from tickets where assignee_id in (:ids) and resolved_at > now() - interval '30 days'",
                new MapSqlParameterSource("ids", t.getMemberIds()), Long.class)).orElse(0L);
        String projectName = t.getProjectId() == null ? null : projects.findById(t.getProjectId()).map(p -> p.getName()).orElse(null);
        User leader = t.getTeamLeaderId() == null ? null : people.get(t.getTeamLeaderId());
        return new TeamDtos.TeamResponse(t.getId(), t.getName(), t.getDescription(), t.getProjectId(), projectName,
                t.getTeamLeaderId(), leader == null ? null : leader.getFullName(), t.isActive(), members,
                open.values().stream().mapToLong(Long::longValue).sum(), resolved);
    }
}
