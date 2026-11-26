package com.suma.security;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Delegated USER_WRITE / ROLE_MANAGE must not become an admin takeover; password resets end old sessions. */
class DelegatedAdminIT extends IntegrationTest {

    private String userJson(String role) {
        String n = UUID.randomUUID().toString().substring(0, 8);
        return "{\"employeeCode\":\"D" + n + "\",\"username\":\"d" + n + "\",\"email\":\"d" + n + "@example.com\",\"fullName\":\"Delegated " + n
                + "\",\"role\":\"" + role + "\",\"password\":\"Strong-pass1\",\"status\":\"ACTIVE\",\"weeklyOffs\":[7]}";
    }

    @Test
    void managerWithDelegatedPermissionsCannotCreateOrTakeOverAdmins() throws Exception {
        String grant = "insert into role_permissions (role_id, permission_id) select r.id, p.id from roles r, permissions p "
                + "where r.code = 'MANAGER' and p.code in ('USER_WRITE','ROLE_MANAGE') on conflict do nothing";
        jdbc.update(grant);
        try {
            User admin = createUser("ADMIN");
            User manager = createUser("MANAGER");
            sessionValidator.evictAll();
            Session m = login(manager.getUsername());
            mvc.perform(auth(post("/api/v1/users"), m).contentType(MediaType.APPLICATION_JSON).content(userJson("ADMIN"))).andExpect(status().isForbidden());
            mvc.perform(auth(post("/api/v1/users/" + admin.getId() + "/reset-password"), m).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"Hijacked-pass1\"}")).andExpect(status().isForbidden());
            mvc.perform(auth(put("/api/v1/roles/ADMIN/permissions"), m).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"permissions\":[\"ROLE_MANAGE\",\"SECURITY_MANAGE\",\"USER_WRITE\"]}")).andExpect(status().isForbidden());
            // cannot hand out a permission the manager does not hold
            mvc.perform(auth(put("/api/v1/roles/L1/permissions"), m).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"permissions\":[\"DASHBOARD_VIEW\",\"SECURITY_MANAGE\"]}")).andExpect(status().isForbidden());
            // cannot widen their own role
            mvc.perform(auth(put("/api/v1/roles/MANAGER/permissions"), m).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"permissions\":[\"USER_WRITE\",\"ROLE_MANAGE\",\"CLIENT_WRITE\"]}")).andExpect(status().isForbidden());
        } finally {
            jdbc.update("delete from role_permissions where role_id = (select id from roles where code = 'MANAGER') "
                    + "and permission_id in (select id from permissions where code in ('USER_WRITE','ROLE_MANAGE'))");
            sessionValidator.evictAll();
        }
    }

    @Test
    void passwordResetAndChangeSignOutOtherSessions() throws Exception {
        User admin = createUser("ADMIN");
        User emp = createUser("L1");
        Session old = login(emp.getUsername());
        Session a = login(admin.getUsername());
        mvc.perform(auth(post("/api/v1/users/" + emp.getId() + "/reset-password"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"Reset-pass12\"}")).andExpect(status().isNoContent());
        sessionValidator.evictAll();
        mvc.perform(auth(get("/api/v1/auth/me"), old)).andExpect(status().isUnauthorized());

        Session one = login(emp.getUsername(), "Reset-pass12", "127.0.0.1");
        Session two = login(emp.getUsername(), "Reset-pass12", "127.0.0.1");
        mvc.perform(auth(post("/api/v1/auth/change-password"), two).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Reset-pass12\",\"newPassword\":\"Changed-pass12\"}")).andExpect(status().isNoContent());
        sessionValidator.evictAll();
        mvc.perform(auth(get("/api/v1/auth/me"), one)).andExpect(status().isUnauthorized()); // other device signed out
        mvc.perform(auth(get("/api/v1/auth/me"), two)).andExpect(status().isOk());           // the session that changed it stays
    }
}
