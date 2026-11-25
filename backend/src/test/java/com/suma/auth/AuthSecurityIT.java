package com.suma.auth;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthSecurityIT extends IntegrationTest {

    @Test
    void successfulLoginRecordsIpSessionAndAttendance() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername(), PASSWORD, "198.51.100.23");
        assertThat(s.refresh()).isNotNull();
        assertThat(s.refresh().isHttpOnly()).isTrue();
        Map<String, Object> row = jdbc.queryForMap("select success, ip_address, ip_version, session_id from login_activity_logs where user_id = ? order by created_at desc limit 1", u.getId());
        assertThat(row.get("success")).isEqualTo(true);
        assertThat(row.get("ip_address")).isEqualTo("198.51.100.23");
        assertThat(((Number) row.get("ip_version")).intValue()).isEqualTo(4);
        Integer att = jdbc.queryForObject("select count(*) from attendance_sessions where user_id = ? and status = 'ACTIVE' and login_ip = '198.51.100.23'", Integer.class, u.getId());
        assertThat(att).isEqualTo(1);
        mvc.perform(auth(get("/api/v1/auth/me"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("L1"));
    }

    @Test
    void failedAndUnknownUserAttemptsAreLoggedWithIpWithoutPasswords() throws Exception {
        User u = createUser("L2");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"WrongSecret#1\"}").with(r -> { r.setRemoteAddr("203.0.113.77"); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost-user-xyz\",\"password\":\"Whatever#123\"}").with(r -> { r.setRemoteAddr("2001:db8::99"); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
        assertThat(jdbc.queryForObject("select failure_category from login_activity_logs where username_attempted = ? order by created_at desc limit 1", String.class, u.getUsername()))
                .isEqualTo("INVALID_CREDENTIALS");
        Map<String, Object> unknown = jdbc.queryForMap("select failure_category, ip_address, ip_version from login_activity_logs where username_attempted = 'ghost-user-xyz'");
        assertThat(unknown.get("failure_category")).isEqualTo("UNKNOWN_USER");
        assertThat(unknown.get("ip_address")).isEqualTo("2001:db8::99");
        assertThat(((Number) unknown.get("ip_version")).intValue()).isEqualTo(6);
        // no secret ever reaches the security tables
        Integer leaks = jdbc.queryForObject("""
                select (select count(*) from login_activity_logs where coalesce(username_attempted,'') like '%Secret%' or coalesce(user_agent,'') like '%Secret%')
                     + (select count(*) from ip_activity_logs where details like '%WrongSecret%')""", Integer.class);
        assertThat(leaks).isZero();
    }

    @Test
    void repeatedFailuresLockAccountTemporarilyAndFlagSuspicious() throws Exception {
        User u = createUser("L1");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"nope\"}").with(r -> { r.setRemoteAddr("198.51.100.77"); return r; }));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isLocked());
        assertThat(jdbc.queryForObject("select count(*) from login_activity_logs where username_attempted = ? and suspicious", Integer.class, u.getUsername())).isPositive();
    }

    @Test
    void unauthenticatedAndUnauthorizedAccessIsCategorised() throws Exception {
        mvc.perform(get("/api/v1/clients").with(r -> { r.setRemoteAddr("198.51.100.200"); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MISSING_AUTH"));
        mvc.perform(get("/api/v1/clients").header("Authorization", "Bearer not.a.jwt").with(r -> { r.setRemoteAddr("198.51.100.201"); return r; }))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        assertThat(jdbc.queryForObject("select category from anonymous_access_logs where ip_address = '198.51.100.200'", String.class)).isEqualTo("MISSING_AUTH");
        assertThat(jdbc.queryForObject("select category from anonymous_access_logs where ip_address = '198.51.100.201'", String.class)).isEqualTo("INVALID_TOKEN");

        User l1 = createUser("L1");
        Session s = login(l1.getUsername());
        mvc.perform(auth(get("/api/v1/security/dashboard"), s)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(jdbc.queryForObject("select count(*) from anonymous_access_logs where category = 'ACCESS_DENIED' and user_id = ?", Integer.class, l1.getId())).isEqualTo(1);
    }

    @Test
    void forwardedForOnlyTrustedFromConfiguredProxies() throws Exception {
        User u = createUser("L1");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "6.6.6.6")
                .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}").with(r -> { r.setRemoteAddr("198.51.100.50"); return r; }))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "203.0.113.10")
                .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}").with(r -> { r.setRemoteAddr("10.1.2.3"); return r; }))
                .andExpect(status().isOk());
        var ips = jdbc.queryForList("select ip_address from login_activity_logs where user_id = ? order by created_at", String.class, u.getId());
        assertThat(ips).containsExactlyInAnyOrder("198.51.100.50", "203.0.113.10");
    }

    @Test
    void refreshRotatesAndReuseRevokesSession() throws Exception {
        User u = createUser("L3");
        Session s = login(u.getUsername());
        var r1 = mvc.perform(post("/api/v1/auth/refresh").cookie(s.refresh())).andExpect(status().isOk()).andReturn();
        Cookie rotated = r1.getResponse().getCookie("suma_rt");
        assertThat(rotated.getValue()).isNotEqualTo(s.refresh().getValue());
        // replaying the old token = theft signal: session revoked
        mvc.perform(post("/api/v1/auth/refresh").cookie(s.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
        sessionValidator.evictAll();
        mvc.perform(auth(get("/api/v1/auth/me"), s)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REVOKED_SESSION"));
    }

    @Test
    void adminCanTerminateSessionAndItStopsWorkingImmediately() throws Exception {
        User admin = createUser("ADMIN");
        User emp = createUser("L1");
        Session a = login(admin.getUsername());
        Session e = login(emp.getUsername());
        mvc.perform(auth(get("/api/v1/attendance/me"), e)).andExpect(status().isOk());
        String sid = e.me().get("sessionId").asText();
        mvc.perform(auth(post("/api/v1/security/sessions/" + sid + "/terminate"), a).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/attendance/me"), e)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select logout_source from attendance_sessions where auth_session_id = ?::uuid", String.class, sid)).isEqualTo("ADMIN_TERMINATED");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'SESSION_TERMINATED' and entity_id = ?", Integer.class, sid)).isEqualTo(1);
    }

    @Test
    void ipDenyRuleBlocksLoginAndRequests() throws Exception {
        User admin = createUser("ADMIN");
        User emp = createUser("L1");
        Session a = login(admin.getUsername());
        String ruleId = json(mvc.perform(auth(post("/api/v1/security/ip-rules"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cidr\":\"192.0.2.0/24\",\"ruleType\":\"DENY\",\"description\":\"test\"}")).andExpect(status().isCreated()).andReturn()).get("id").asText();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + emp.getUsername() + "\",\"password\":\"" + PASSWORD + "\"}").with(r -> { r.setRemoteAddr("192.0.2.44"); return r; }))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from anonymous_access_logs where category = 'BLOCKED_IP' and ip_address = '192.0.2.44'", Integer.class)).isPositive();
        mvc.perform(auth(delete("/api/v1/security/ip-rules/" + ruleId), a)).andExpect(status().isNoContent());
        // the deny rule no longer applies once removed
        login(emp.getUsername(), PASSWORD, "192.0.2.45");
    }

    @Test
    void rolePermissionsEnforcedOnBackend() throws Exception {
        Session l1 = login(createUser("L1").getUsername());
        Session tl = login(createUser("TEAM_LEADER").getUsername());
        Session mgr = login(createUser("MANAGER").getUsername());
        mvc.perform(auth(post("/api/v1/clients"), mgr).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"X1\",\"name\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/users"), l1)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/users"), tl)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/audit"), tl)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/audit"), mgr)).andExpect(status().isOk());
        mvc.perform(auth(put("/api/v1/attendance/settings"), tl).contentType(MediaType.APPLICATION_JSON).content("""
                {"idleThresholdMinutes":20,"heartbeatTimeoutMinutes":30,"missingLogoutAfterHours":16,"reportingTimezone":"UTC","notifyTeamLeaderOnIdle":false,
                 "monthlyReportEnabled":true,"monthlyReportHour":6,"emailMaxAttempts":5,"emailRetryBackoffMinutes":15,"retentionDays":730}""")).andExpect(status().isForbidden());
    }
}
