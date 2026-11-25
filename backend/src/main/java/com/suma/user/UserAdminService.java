package com.suma.user;

import com.suma.audit.AuditService;
import com.suma.common.ApiException;
import com.suma.common.PageResponse;
import com.suma.security.AccessScopeService;
import com.suma.security.AuthUser;
import com.suma.security.CurrentUser;
import com.suma.security.SessionValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserAdminService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final ShiftRepository shifts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AccessScopeService scope;
    private final SessionValidator sessionValidator;
    private final NamedParameterJdbcTemplate jdbc;

    public UserAdminService(UserRepository users, RoleRepository roles, PermissionRepository permissions, ShiftRepository shifts,
                            PasswordEncoder encoder, AuditService audit, AccessScopeService scope,
                            SessionValidator sessionValidator, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.shifts = shifts;
        this.encoder = encoder;
        this.audit = audit;
        this.scope = scope;
        this.sessionValidator = sessionValidator;
        this.jdbc = jdbc;
    }

    /** Non-admins only see users that share a visible project or team with them. */
    private Set<UUID> visibleUserIds(AuthUser me) {
        if (me.isAdmin()) return null;
        Set<UUID> projects = scope.visibleProjectIds(me);
        Set<UUID> ids = new HashSet<>();
        ids.add(me.id());
        if (!projects.isEmpty()) {
            ids.addAll(jdbc.queryForList("select user_id from project_members where project_id in (:p)",
                    new MapSqlParameterSource("p", projects), UUID.class));
        }
        ids.addAll(jdbc.queryForList("select tm.user_id from team_members tm join teams t on t.id = tm.team_id where t.team_leader_id = :u",
                new MapSqlParameterSource("u", me.id()), UUID.class));
        return ids;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.UserResponse> list(String q, String role, String status, int page, int size) {
        Set<UUID> ids = visibleUserIds(CurrentUser.get());
        var p = users.search(blank(q), blank(role), blank(status), ids != null, ids == null ? Set.of(new UUID(0, 0)) : ids,
                PageRequest.of(page, Math.min(size, 200), Sort.by("fullName")));
        return PageResponse.of(p, u -> toResponse(u, false));
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserSummary> directory() {
        Set<UUID> ids = visibleUserIds(CurrentUser.get());
        return users.findByStatus("ACTIVE").stream().filter(u -> ids == null || ids.contains(u.getId()))
                .sorted(Comparator.comparing(User::getFullName))
                .map(u -> new UserDtos.UserSummary(u.getId(), u.getEmployeeCode(), u.getFullName(), u.roleCode(), u.getEmail())).toList();
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(UUID id) {
        Set<UUID> ids = visibleUserIds(CurrentUser.get());
        if (ids != null && !ids.contains(id)) throw ApiException.notFound("User");
        return toResponse(users.findById(id).orElseThrow(() -> ApiException.notFound("User")), true);
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.UserRequest r) {
        if (r.password() == null) throw ApiException.badRequest("Initial password is required");
        if (users.existsByUsernameIgnoreCase(r.username())) throw ApiException.conflict("Username already exists");
        if (users.existsByEmailIgnoreCase(r.email())) throw ApiException.conflict("Email already exists");
        if (users.existsByEmployeeCodeIgnoreCase(r.employeeCode())) throw ApiException.conflict("Employee code already exists");
        User u = new User();
        apply(u, r);
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), u.getUsername() + " role=" + u.roleCode());
        return toResponse(u, true);
    }

    @Transactional
    public UserDtos.UserResponse update(UUID id, UserDtos.UserRequest r) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!u.getUsername().equalsIgnoreCase(r.username()) && users.existsByUsernameIgnoreCase(r.username())) throw ApiException.conflict("Username already exists");
        if (!u.getEmail().equalsIgnoreCase(r.email()) && users.existsByEmailIgnoreCase(r.email())) throw ApiException.conflict("Email already exists");
        if (!u.getEmployeeCode().equalsIgnoreCase(r.employeeCode()) && users.existsByEmployeeCodeIgnoreCase(r.employeeCode())) throw ApiException.conflict("Employee code already exists");
        String oldRole = u.roleCode();
        String oldStatus = u.getStatus();
        if (u.getId().equals(CurrentUser.id()) && (!r.role().equals(oldRole) || (r.status() != null && !"ACTIVE".equals(r.status()))))
            throw ApiException.badRequest("You cannot change your own role or deactivate yourself");
        apply(u, r);
        if (r.password() != null) u.setPasswordHash(encoder.encode(r.password()));
        if (!oldRole.equals(u.roleCode())) audit.log("USER_ROLE_CHANGED", "User", id, oldRole + " -> " + u.roleCode());
        if (!oldStatus.equals(u.getStatus())) audit.log("USER_STATUS_CHANGED", "User", id, oldStatus + " -> " + u.getStatus());
        audit.log("USER_UPDATED", "User", id, u.getUsername());
        sessionValidator.evictAll();
        return toResponse(u, true);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
        audit.log("USER_PASSWORD_RESET", "User", id, null);
    }

    @Transactional
    public void unlock(UUID id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
        if ("LOCKED".equals(u.getStatus())) u.setStatus("ACTIVE");
        audit.log("USER_UNLOCKED", "User", id, null);
    }

    private void apply(User u, UserDtos.UserRequest r) {
        u.setEmployeeCode(r.employeeCode().toUpperCase());
        u.setUsername(r.username().toLowerCase());
        u.setEmail(r.email().toLowerCase());
        u.setFullName(r.fullName().trim());
        u.setPhone(r.phone());
        u.setJobTitle(r.jobTitle());
        u.setRole(roles.findByCode(r.role()).orElseThrow(() -> ApiException.badRequest("Unknown role")));
        u.setShift(r.shiftId() == null ? null : shifts.findById(r.shiftId()).orElseThrow(() -> ApiException.badRequest("Shift not found")));
        if (r.timezone() != null && !r.timezone().isBlank()) {
            try { ZoneId.of(r.timezone()); } catch (Exception e) { throw ApiException.badRequest("Unknown timezone"); }
            u.setTimezone(r.timezone());
        }
        u.setMaxOpenTickets(r.maxOpenTickets());
        if (r.status() != null) u.setStatus(r.status());
        if (r.weeklyOffs() != null) {
            u.getWeeklyOffs().clear();
            r.weeklyOffs().forEach(d -> u.getWeeklyOffs().add(d.shortValue()));
        }
    }

    @Transactional(readOnly = true)
    public List<UserDtos.WorkloadRow> workload(UUID projectId) {
        AuthUser me = CurrentUser.get();
        Set<UUID> visible = visibleUserIds(me);
        var p = new MapSqlParameterSource("project", projectId);
        List<UserDtos.WorkloadRow> rows = jdbc.query("""
                select u.id, u.employee_code, u.full_name, r.code as role, u.max_open_tickets,
                  count(t.id) filter (where t.status not in ('RESOLVED','CLOSED')) as open,
                  count(t.id) filter (where t.status = 'IN_PROGRESS') as in_progress,
                  count(t.id) filter (where t.status = 'PENDING') as pending,
                  count(t.id) filter (where t.resolved_at > now() - interval '30 days') as resolved30,
                  count(t.id) filter (where t.status not in ('RESOLVED','CLOSED') and (t.response_breached or t.resolution_breached)) as breached
                from users u join roles r on r.id = u.role_id
                left join tickets t on t.assignee_id = u.id and (cast(:project as uuid) is null or t.project_id = :project)
                where u.status = 'ACTIVE' and r.code in ('L1','L2','L3','TEAM_LEADER')
                group by u.id, u.employee_code, u.full_name, r.code, u.max_open_tickets
                order by open desc, u.full_name
                """, p, (rs, i) -> new UserDtos.WorkloadRow(rs.getObject("id", UUID.class), rs.getString("employee_code"),
                rs.getString("full_name"), rs.getString("role"), rs.getLong("open"), rs.getLong("in_progress"), rs.getLong("pending"),
                rs.getLong("resolved30"), rs.getLong("breached"), (Integer) rs.getObject("max_open_tickets")));
        return rows.stream().filter(r -> visible == null || visible.contains(r.userId())).toList();
    }

    // ---------- roles & permissions ----------
    @Transactional(readOnly = true)
    public List<UserDtos.RoleResponse> roles() {
        return roles.findAll().stream().sorted(Comparator.comparing(r -> RoleCode.valueOf(r.getCode()).ordinal()))
                .map(r -> new UserDtos.RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                        r.getPermissions().stream().map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new)))).toList();
    }

    @Transactional(readOnly = true)
    public List<UserDtos.PermissionResponse> permissions() {
        return permissions.findAll().stream().sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                .map(p -> new UserDtos.PermissionResponse(p.getCode(), p.getModule(), p.getDescription())).toList();
    }

    @Transactional
    public UserDtos.RoleResponse setRolePermissions(String code, Set<String> codes) {
        Role role = roles.findByCode(code).orElseThrow(() -> ApiException.notFound("Role"));
        if ("ADMIN".equals(code) && !codes.containsAll(Set.of("ROLE_MANAGE", "SECURITY_MANAGE", "USER_WRITE")))
            throw ApiException.badRequest("ADMIN must keep ROLE_MANAGE, SECURITY_MANAGE and USER_WRITE to prevent lock-out");
        List<Permission> perms = permissions.findByCodeIn(codes);
        if (perms.size() != codes.size()) throw ApiException.badRequest("Unknown permission code");
        String before = role.getPermissions().stream().map(Permission::getCode).sorted().collect(Collectors.joining(","));
        role.getPermissions().clear();
        role.getPermissions().addAll(perms);
        audit.log("ROLE_PERMISSIONS_CHANGED", "Role", code, before + " -> " + String.join(",", new TreeSet<>(codes)));
        sessionValidator.evictAll();
        return roles().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }

    // ---------- shifts ----------
    @Transactional(readOnly = true)
    public List<UserDtos.ShiftResponse> shiftList() {
        return shifts.findAll().stream().sorted(Comparator.comparing(Shift::getStartTime)).map(UserAdminService::shift).toList();
    }

    @Transactional
    public UserDtos.ShiftResponse saveShift(UUID id, UserDtos.ShiftRequest r) {
        Shift s = id == null ? new Shift() : shifts.findById(id).orElseThrow(() -> ApiException.notFound("Shift"));
        if (id == null && shifts.existsByNameIgnoreCase(r.name())) throw ApiException.conflict("Shift already exists");
        if (r.timezone() != null) {
            try { ZoneId.of(r.timezone()); } catch (Exception e) { throw ApiException.badRequest("Unknown timezone"); }
            s.setTimezone(r.timezone());
        }
        s.setName(r.name());
        s.setStartTime(r.startTime());
        s.setEndTime(r.endTime());
        if (r.active() != null) s.setActive(r.active());
        shifts.save(s);
        audit.log(id == null ? "SHIFT_CREATED" : "SHIFT_UPDATED", "Shift", s.getId(), s.getName());
        return shift(s);
    }

    private static UserDtos.ShiftResponse shift(Shift s) {
        return new UserDtos.ShiftResponse(s.getId(), s.getName(), s.getStartTime(), s.getEndTime(), s.getTimezone(), s.isActive());
    }

    private UserDtos.UserResponse toResponse(User u, boolean detail) {
        List<UserDtos.ProjectAllocation> projects = List.of();
        List<String> teams = List.of();
        Long open = null;
        if (detail) {
            var p = new MapSqlParameterSource("u", u.getId());
            projects = jdbc.query("""
                    select p.id, p.code, p.name, pm.designation, pm.allocation_percent, pm.is_primary
                    from project_members pm join projects p on p.id = pm.project_id where pm.user_id = :u order by pm.is_primary desc, p.name
                    """, p, (rs, i) -> new UserDtos.ProjectAllocation(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getInt(5), rs.getBoolean(6)));
            teams = jdbc.queryForList("select t.name from teams t join team_members tm on tm.team_id = t.id where tm.user_id = :u order by t.name", p, String.class);
            open = jdbc.queryForObject("select count(*) from tickets where assignee_id = :u and status not in ('RESOLVED','CLOSED')", p, Long.class);
        }
        Shift s = u.getShift();
        return new UserDtos.UserResponse(u.getId(), u.getEmployeeCode(), u.getUsername(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getJobTitle(), u.roleCode(), s == null ? null : s.getId(), s == null ? null : s.getName(),
                u.getWeeklyOffs().stream().map(Short::intValue).collect(Collectors.toCollection(TreeSet::new)),
                u.getTimezone(), u.getMaxOpenTickets(), u.getStatus(), u.getLastLoginAt(), u.getLastLoginIp(), u.getCreatedAt(),
                projects, teams, open);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
