package com.suma.auth;

import com.suma.user.Permission;
import com.suma.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuthDtos.MeResponse me(User u, UUID sessionId) {
        List<AuthDtos.ProjectRef> projects = jdbc.query("""
                select p.id, p.code, p.name,
                       case when p.project_lead_id = ? then 'PROJECT_LEAD' else coalesce(pm.designation, 'MEMBER') end as designation
                from projects p left join project_members pm on pm.project_id = p.id and pm.user_id = ?
                where p.status = 'ACTIVE' and (pm.user_id is not null or p.project_lead_id = ? or p.manager_id = ? or p.team_leader_id = ?)
                order by p.name
                """, (rs, i) -> new AuthDtos.ProjectRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                u.getId(), u.getId(), u.getId(), u.getId(), u.getId());
        boolean lead = projects.stream().anyMatch(p -> "PROJECT_LEAD".equals(p.designation()));
        Integer teamsLed = jdbc.queryForObject("select count(*) from teams where team_leader_id = ?", Integer.class, u.getId());
        return new AuthDtos.MeResponse(u.getId(), u.getEmployeeCode(), u.getUsername(), u.getFullName(), u.getEmail(),
                u.roleCode(), u.getRole().getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet()),
                u.getThemePreference(), u.getTimezone(), sessionId, projects, lead, teamsLed != null && teamsLed > 0);
    }
}
