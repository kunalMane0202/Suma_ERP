package com.suma.project;

import com.suma.audit.AuditService;
import com.suma.client.ClientRepository;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.AccessScopeService;
import com.suma.security.CurrentUser;
import com.suma.user.User;
import com.suma.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TicketCategoryRepository categories;
    private final ClientRepository clients;
    private final UserRepository users;
    private final AccessScopeService scope;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, TicketCategoryRepository categories,
                          ClientRepository clients, UserRepository users, AccessScopeService scope, AuditService audit, JdbcTemplate jdbc) {
        this.projects = projects;
        this.members = members;
        this.categories = categories;
        this.clients = clients;
        this.users = users;
        this.scope = scope;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectDtos.ProjectResponse> list(String q, UUID clientId, String status, int page, int size) {
        Set<UUID> ids = scope.visibleProjectIds(CurrentUser.get());
        var p = projects.search(blank(q), clientId, blank(status), ids != null, ids == null || ids.isEmpty() ? Set.of(new UUID(0, 0)) : ids,
                PageRequest.of(page, Math.min(size, 200), Sort.by("name")));
        return PageResponse.of(p, pr -> toResponse(pr, false));
    }

    @Transactional(readOnly = true)
    public ProjectDtos.ProjectResponse get(UUID id) {
        return toResponse(load(id), true);
    }

    public Project load(UUID id) {
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        if (!scope.canViewProject(CurrentUser.get(), id)) throw ApiException.notFound("Project");
        return p;
    }

    @Transactional
    public ProjectDtos.ProjectResponse create(ProjectDtos.ProjectRequest r) {
        if (projects.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict("Project code already exists");
        Project p = new Project();
        apply(p, r);
        projects.save(p);
        syncDesignatedMembers(p);
        audit.log("PROJECT_CREATED", "Project", p.getId(), p.getCode());
        return toResponse(p, true);
    }

    @Transactional
    public ProjectDtos.ProjectResponse update(UUID id, ProjectDtos.ProjectRequest r) {
        Project p = load(id);
        if (!p.getCode().equalsIgnoreCase(r.code()) && projects.existsByCodeIgnoreCase(r.code()))
            throw ApiException.conflict("Project code already exists");
        String oldEmail = p.getProjectLeadEmail();
        UUID oldLead = p.getProjectLeadId();
        apply(p, r);
        if (!Objects.equals(oldEmail, p.getProjectLeadEmail()) || !Objects.equals(oldLead, p.getProjectLeadId())) {
            audit.log("PROJECT_LEAD_CHANGED", "Project", id, "lead=" + p.getProjectLeadId() + " email=" + p.getProjectLeadEmail()
                    + " verified=" + p.isProjectLeadEmailVerified());
        }
        syncDesignatedMembers(p);
        audit.log("PROJECT_UPDATED", "Project", id, p.getCode());
        return toResponse(p, true);
    }

    /** Explicit administrator verification of the Project Lead report recipient (reports are never sent to unverified addresses). */
    @Transactional
    public ProjectDtos.ProjectResponse verifyLeadEmail(UUID id, boolean verified) {
        Project p = load(id);
        if (p.getProjectLeadEmail() == null) throw ApiException.badRequest("Project has no Project Lead email configured");
        p.setProjectLeadEmailVerified(verified);
        audit.log(verified ? "PROJECT_LEAD_EMAIL_VERIFIED" : "PROJECT_LEAD_EMAIL_UNVERIFIED", "Project", id, p.getProjectLeadEmail());
        return toResponse(p, true);
    }

    @Transactional
    public void setStatus(UUID id, String status) {
        Project p = load(id);
        p.setStatus(status);
        audit.log("PROJECT_STATUS_CHANGED", "Project", id, status);
    }

    @Transactional
    public ProjectDtos.ProjectResponse upsertMember(UUID projectId, ProjectDtos.MemberRequest r) {
        Project p = load(projectId);
        if (!users.existsById(r.userId())) throw ApiException.badRequest("User not found");
        ProjectMember m = members.findById(new ProjectMember.Key(projectId, r.userId())).orElseGet(() -> {
            ProjectMember n = new ProjectMember();
            n.setProjectId(projectId);
            n.setUserId(r.userId());
            return n;
        });
        if (r.designation() != null) m.setDesignation(r.designation());
        if (r.allocationPercent() != null) m.setAllocationPercent(r.allocationPercent());
        if (r.primary() != null) m.setPrimary(r.primary());
        if (m.isPrimary()) {
            members.findByUserId(r.userId()).stream().filter(o -> !o.getProjectId().equals(projectId) && o.isPrimary())
                    .forEach(o -> o.setPrimary(false));
        }
        members.save(m);
        if ("PROJECT_LEAD".equals(m.getDesignation()) && p.getProjectLeadId() == null) {
            p.setProjectLeadId(r.userId());
        }
        audit.log("PROJECT_MEMBER_SET", "Project", projectId, r.userId() + " " + m.getDesignation());
        return toResponse(p, true);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        load(projectId);
        members.deleteById(new ProjectMember.Key(projectId, userId));
        audit.log("PROJECT_MEMBER_REMOVED", "Project", projectId, userId.toString());
    }

    @Transactional(readOnly = true)
    public List<ProjectDtos.CategoryResponse> categories(UUID projectId) {
        load(projectId);
        return categories.forProject(projectId).stream().map(ProjectService::toCategory).toList();
    }

    @Transactional
    public ProjectDtos.CategoryResponse addCategory(UUID projectId, ProjectDtos.CategoryRequest r) {
        load(projectId);
        if (r.parentId() != null) {
            TicketCategory parent = categories.findById(r.parentId()).orElseThrow(() -> ApiException.badRequest("Parent category not found"));
            if (parent.getParentId() != null) throw ApiException.badRequest("Only two category levels are supported");
        }
        TicketCategory c = new TicketCategory();
        c.setProjectId(projectId);
        c.setParentId(r.parentId());
        c.setName(r.name());
        c.setKeywords(r.keywords());
        c.setDefaultTeamId(r.defaultTeamId());
        categories.save(c);
        audit.log("CATEGORY_CREATED", "TicketCategory", c.getId(), r.name());
        return toCategory(c);
    }

    @Transactional
    public void deleteCategory(UUID projectId, UUID categoryId) {
        load(projectId);
        TicketCategory c = categories.findById(categoryId).orElseThrow(() -> ApiException.notFound("Category"));
        c.setActive(false);
        audit.log("CATEGORY_DEACTIVATED", "TicketCategory", categoryId, c.getName());
    }

    private static ProjectDtos.CategoryResponse toCategory(TicketCategory c) {
        return new ProjectDtos.CategoryResponse(c.getId(), c.getProjectId(), c.getParentId(), c.getName(), c.getKeywords(), c.getDefaultTeamId());
    }

    private void apply(Project p, ProjectDtos.ProjectRequest r) {
        if (!clients.existsById(r.clientId())) throw ApiException.badRequest("Client not found");
        for (UUID u : new UUID[]{r.managerId(), r.teamLeaderId(), r.projectLeadId()}) {
            if (u != null && !users.existsById(u)) throw ApiException.badRequest("Referenced user not found");
        }
        if (r.timezone() != null) {
            try { ZoneId.of(r.timezone()); } catch (Exception e) { throw ApiException.badRequest("Unknown timezone"); }
        }
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate()))
            throw ApiException.badRequest("End date must be after start date");
        p.setClientId(r.clientId());
        p.setCode(r.code().toUpperCase());
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setManagerId(r.managerId());
        p.setTeamLeaderId(r.teamLeaderId());
        String newEmail = r.projectLeadEmail() == null || r.projectLeadEmail().isBlank() ? null : r.projectLeadEmail().trim().toLowerCase();
        boolean changed = !Objects.equals(newEmail, p.getProjectLeadEmail()) || !Objects.equals(r.projectLeadId(), p.getProjectLeadId());
        p.setProjectLeadId(r.projectLeadId());
        p.setProjectLeadEmail(newEmail);
        if (changed) {
            // Auto-verified only if it equals the lead's own registered account email; otherwise requires explicit verification.
            boolean matchesAccount = newEmail != null && r.projectLeadId() != null &&
                    users.findById(r.projectLeadId()).map(User::getEmail).map(e -> e.equalsIgnoreCase(newEmail)).orElse(false);
            p.setProjectLeadEmailVerified(matchesAccount);
        }
        p.setCalendarId(r.calendarId());
        p.setSupportHours(r.supportHours());
        if (r.timezone() != null) p.setTimezone(r.timezone());
        if (r.status() != null) p.setStatus(r.status());
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
    }

    /** Keeps project_members designations consistent with the project's manager / team leader / lead fields. */
    private void syncDesignatedMembers(Project p) {
        Map<UUID, String> designated = new LinkedHashMap<>();
        if (p.getManagerId() != null) designated.put(p.getManagerId(), "MANAGER");
        if (p.getTeamLeaderId() != null) designated.put(p.getTeamLeaderId(), "TEAM_LEADER");
        if (p.getProjectLeadId() != null) designated.put(p.getProjectLeadId(), "PROJECT_LEAD");
        // Demote previous holders of designations that changed
        for (ProjectMember m : members.findByProjectId(p.getId())) {
            if (!"MEMBER".equals(m.getDesignation()) && !m.getDesignation().equals(designated.get(m.getUserId()))) {
                m.setDesignation("MEMBER");
            }
        }
        designated.forEach((userId, designation) -> {
            ProjectMember m = members.findById(new ProjectMember.Key(p.getId(), userId)).orElseGet(() -> {
                ProjectMember n = new ProjectMember();
                n.setProjectId(p.getId());
                n.setUserId(userId);
                n.setPrimary(members.findByUserId(userId).stream().noneMatch(ProjectMember::isPrimary));
                return n;
            });
            m.setDesignation(designation);
            members.save(m);
        });
    }

    private ProjectDtos.ProjectResponse toResponse(Project p, boolean withMembers) {
        Map<UUID, User> people = new HashMap<>();
        List<ProjectMember> ms = withMembers ? members.findByProjectId(p.getId()) : List.of();
        Set<UUID> ids = new HashSet<>();
        for (UUID u : new UUID[]{p.getManagerId(), p.getTeamLeaderId(), p.getProjectLeadId()}) if (u != null) ids.add(u);
        ms.forEach(m -> ids.add(m.getUserId()));
        users.findByIdIn(ids).forEach(u -> people.put(u.getId(), u));
        List<ProjectDtos.MemberResponse> memberDtos = ms.stream().filter(m -> people.containsKey(m.getUserId())).map(m -> {
            User u = people.get(m.getUserId());
            return new ProjectDtos.MemberResponse(u.getId(), u.getEmployeeCode(), u.getFullName(), u.roleCode(), m.getDesignation(),
                    m.getAllocationPercent(), m.isPrimary(), m.getAssignedAt());
        }).sorted(Comparator.comparing(ProjectDtos.MemberResponse::fullName)).toList();
        String clientName = clients.findById(p.getClientId()).map(c -> c.getName()).orElse(null);
        Long memberCount = jdbc.queryForObject("select count(*) from project_members where project_id = ?", Long.class, p.getId());
        var counts = jdbc.queryForMap("select count(*) as total, count(*) filter (where status not in ('RESOLVED','CLOSED')) as open from tickets where project_id = ?", p.getId());
        return new ProjectDtos.ProjectResponse(p.getId(), p.getClientId(), clientName, p.getCode(), p.getName(), p.getDescription(),
                p.getManagerId(), name(people, p.getManagerId()), p.getTeamLeaderId(), name(people, p.getTeamLeaderId()),
                p.getProjectLeadId(), name(people, p.getProjectLeadId()), p.getProjectLeadEmail(), p.isProjectLeadEmailVerified(),
                p.getCalendarId(), p.getSupportHours(), p.getTimezone(), p.getStatus(), p.getStartDate(), p.getEndDate(),
                memberCount == null ? 0 : memberCount, ((Number) counts.get("open")).longValue(), ((Number) counts.get("total")).longValue(),
                p.getCreatedAt(), withMembers ? memberDtos : null);
    }

    private static String name(Map<UUID, User> people, UUID id) {
        return id == null || !people.containsKey(id) ? null : people.get(id).getFullName();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
