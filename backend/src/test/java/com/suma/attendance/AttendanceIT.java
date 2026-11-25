package com.suma.attendance;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttendanceIT extends IntegrationTest {
    @Autowired AttendanceMaintenanceService maintenance;

    @Test
    void breakLifecycleWithDurationAndDuplicatePrevention() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername());
        mvc.perform(auth(post("/api/v1/attendance/breaks/start"), s).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"MEAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(auth(post("/api/v1/attendance/breaks/start"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(auth(get("/api/v1/attendance/me"), s)).andExpect(jsonPath("$.currentState").value("ON_BREAK"));
        clock.advance(Duration.ofMinutes(17));
        mvc.perform(auth(post("/api/v1/attendance/breaks/end"), s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSeconds").value(17 * 60)).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(auth(post("/api/v1/attendance/breaks/end"), s)).andExpect(status().isConflict());
        mvc.perform(auth(get("/api/v1/attendance/me"), s)).andExpect(jsonPath("$.breakCount").value(1)).andExpect(jsonPath("$.breakSeconds").value(17 * 60))
                .andExpect(jsonPath("$.currentState").value("WORKING"));
        assertThat(jdbc.queryForList("select event_type from attendance_events where user_id = ? order by occurred_at, created_at", String.class, u.getId()))
                .containsExactly("LOGIN", "BREAK_START", "BREAK_END");
    }

    @Test
    void idleAfterThresholdIsRecordedSeparatelyAndConfirmed() throws Exception {
        User u = createUser("L2");
        Session s = login(u.getUsername());
        mvc.perform(auth(get("/api/v1/attendance/idle/status"), s)).andExpect(jsonPath("$.thresholdMinutes").value(20)).andExpect(jsonPath("$.systemWideMonitoring").value(false));
        Instant lastActivity = clock.instant();
        clock.advance(Duration.ofMinutes(21));
        var r = mvc.perform(auth(post("/api/v1/attendance/idle/events"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idleStartedAt\":\"" + lastActivity + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNCONFIRMED")).andReturn();
        String idleId = json(r).get("id").asText();
        // idempotent: a second report returns the same open event
        mvc.perform(auth(post("/api/v1/attendance/idle/events"), s).contentType(MediaType.APPLICATION_JSON).content("{\"idleStartedAt\":\"" + lastActivity + "\"}"))
                .andExpect(jsonPath("$.id").value(idleId));
        mvc.perform(auth(get("/api/v1/attendance/me"), s)).andExpect(jsonPath("$.currentState").value("IDLE")).andExpect(jsonPath("$.breakCount").value(0));
        mvc.perform(auth(post("/api/v1/attendance/idle/confirm"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idleEventId\":\"" + idleId + "\",\"response\":\"WORKING\"}")).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.response").value("WORKING"))
                .andExpect(jsonPath("$.durationSeconds").value(21 * 60));
        mvc.perform(auth(get("/api/v1/attendance/me"), s)).andExpect(jsonPath("$.currentState").value("WORKING")).andExpect(jsonPath("$.breakCount").value(0))
                .andExpect(jsonPath("$.idleSeconds").value(21 * 60));
    }

    @Test
    void idleConfirmedAsBreakStartsALinkedBreak() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername());
        clock.advance(Duration.ofMinutes(25));
        var r = mvc.perform(auth(post("/api/v1/attendance/idle/events"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idleStartedAt\":\"" + clock.instant().minus(Duration.ofMinutes(20)) + "\"}")).andReturn();
        String idleId = json(r).get("id").asText();
        mvc.perform(auth(post("/api/v1/attendance/idle/confirm"), s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idleEventId\":\"" + idleId + "\",\"response\":\"ON_BREAK\"}")).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/attendance/breaks/current"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.startSource").value("IDLE_CONFIRMATION"));
        // another user cannot answer this prompt
        Session other = login(createUser("L1").getUsername());
        mvc.perform(auth(post("/api/v1/attendance/idle/confirm"), other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idleEventId\":\"" + idleId + "\",\"response\":\"AWAY\"}")).andExpect(status().isForbidden());
    }

    @Test
    void logoutClosesSessionAndOpenBreak() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername());
        mvc.perform(auth(post("/api/v1/attendance/breaks/start"), s).contentType(MediaType.APPLICATION_JSON).content("{}"));
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post("/api/v1/auth/logout").cookie(s.refresh())).andExpect(status().isNoContent());
        var row = jdbc.queryForMap("select status, logout_source, duration_seconds from attendance_sessions where user_id = ?", u.getId());
        assertThat(row.get("status")).isEqualTo("LOGGED_OUT");
        assertThat(row.get("logout_source")).isEqualTo("USER_LOGOUT");
        assertThat(((Number) row.get("duration_seconds")).longValue()).isEqualTo(300);
        assertThat(jdbc.queryForObject("select status from break_records where user_id = ?", String.class, u.getId())).isEqualTo("AUTO_CLOSED");
    }

    @Test
    void concurrentSessionsAreSeparateAndReportedWithoutDoubleCounting() throws Exception {
        User u = createUser("L3");
        Session a = login(u.getUsername());
        clock.advance(Duration.ofMinutes(30));
        Session b = login(u.getUsername());
        clock.advance(Duration.ofMinutes(30));
        assertThat(jdbc.queryForObject("select count(*) from attendance_sessions where user_id = ? and status = 'ACTIVE'", Integer.class, u.getId())).isEqualTo(2);
        mvc.perform(auth(get("/api/v1/attendance/me"), b)).andExpect(jsonPath("$.sessionSeconds").value(3600));
        assertThat(a.token()).isNotEqualTo(b.token());
    }

    @Test
    void missingHeartbeatReconciledAsMissingLogoutAtLastSeenTime() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername());
        mvc.perform(auth(post("/api/v1/attendance/heartbeat"), s).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        Instant lastSeen = clock.instant();
        clock.advance(Duration.ofMinutes(45)); // browser closed: no more heartbeats (timeout 30 min)
        maintenance.reconcileSessions();
        var row = jdbc.queryForMap("select status, logout_source, logout_at from attendance_sessions where user_id = ?", u.getId());
        assertThat(row.get("status")).isEqualTo("MISSING_LOGOUT");
        assertThat(row.get("logout_source")).isEqualTo("HEARTBEAT_TIMEOUT");
        assertThat(((java.sql.Timestamp) row.get("logout_at")).toInstant()).isEqualTo(lastSeen.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'MISSING_LOGOUT'", Integer.class, u.getId())).isEqualTo(1);
        sessionValidator.evictAll();
        mvc.perform(auth(get("/api/v1/attendance/me"), s)).andExpect(status().isUnauthorized());
    }

    @Test
    void serverSideIdleDetectionFromStaleActivityWithLiveHeartbeat() throws Exception {
        User u = createUser("L1");
        Session s = login(u.getUsername());
        Instant activity = clock.instant();
        clock.advance(Duration.ofMinutes(22));
        mvc.perform(auth(post("/api/v1/attendance/heartbeat"), s).contentType(MediaType.APPLICATION_JSON).content("{\"lastActivityAt\":\"" + activity + "\"}"));
        maintenance.detectIdleFromHeartbeats();
        assertThat(jdbc.queryForObject("select source from idle_events where user_id = ?", String.class, u.getId())).isEqualTo("SERVER_HEARTBEAT");
    }

    @Test
    void breakRemindersAndExceededFollowUpsAreSentOnce() throws Exception {
        jdbc.update("update attendance_settings set max_break_minutes = 30, break_reminder_interval_minutes = 10 where project_id is null");
        try {
            User u = createUser("L1");
            Session s = login(u.getUsername());
            mvc.perform(auth(post("/api/v1/attendance/breaks/start"), s).contentType(MediaType.APPLICATION_JSON).content("{}"));
            clock.advance(Duration.ofMinutes(12));
            maintenance.processBreakReminders();
            maintenance.processBreakReminders();
            clock.advance(Duration.ofMinutes(20)); // 32 minutes
            maintenance.processBreakReminders();
            var types = jdbc.queryForList("select reminder_type || ':' || sequence_no from break_reminders where user_id = ? order by sent_at, reminder_type", String.class, u.getId());
            assertThat(types).containsExactlyInAnyOrder("REMINDER:1", "REMINDER:3", "EXCEEDED:1");
            assertThat(jdbc.queryForObject("select exceeded from break_records where user_id = ?", Boolean.class, u.getId())).isTrue();
        } finally {
            jdbc.update("update attendance_settings set max_break_minutes = null, break_reminder_interval_minutes = null where project_id is null");
        }
    }

    @Test
    void dailyAndMonthlyReportsAndExports() throws Exception {
        User admin = createUser("ADMIN");
        User emp = createUser("L1");
        Session e = login(emp.getUsername());
        mvc.perform(auth(post("/api/v1/attendance/breaks/start"), e).contentType(MediaType.APPLICATION_JSON).content("{}"));
        clock.advance(Duration.ofMinutes(8));
        mvc.perform(auth(post("/api/v1/attendance/breaks/end"), e));
        Session a = login(admin.getUsername());
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        var r = mvc.perform(auth(get("/api/v1/attendance/reports/daily"), a).param("date", today.toString()).param("userId", emp.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        var row = json(r).get("rows").get(0);
        assertThat(row.get("employee").get("employeeCode").asText()).isEqualTo(emp.getEmployeeCode());
        assertThat(row.get("day").get("breakCount").asInt()).isEqualTo(1);
        assertThat(row.get("day").get("breakSeconds").asLong()).isEqualTo(480);
        mvc.perform(auth(get("/api/v1/attendance/reports/monthly"), a).param("year", String.valueOf(today.getYear())).param("month", String.valueOf(today.getMonthValue())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employees").isArray());
        for (String f : new String[]{"csv", "xlsx", "pdf"}) {
            var x = mvc.perform(auth(get("/api/v1/attendance/reports/export"), a).param("type", "daily").param("format", f).param("date", today.toString()))
                    .andExpect(status().isOk()).andReturn();
            assertThat(x.getResponse().getContentAsByteArray().length).isGreaterThan(100);
        }
        // employees only see their own attendance
        var self = mvc.perform(auth(get("/api/v1/attendance/reports/daily"), e).param("date", today.toString())).andReturn();
        assertThat(json(self).get("rows")).hasSize(1);
    }

    @Test
    void correctionsPreserveOriginalAndAudit() throws Exception {
        User admin = createUser("ADMIN");
        User emp = createUser("L1");
        Session e = login(emp.getUsername());
        mvc.perform(post("/api/v1/auth/logout").cookie(e.refresh()));
        String sessionId = jdbc.queryForObject("select id::text from attendance_sessions where user_id = ?", String.class, emp.getId());
        String original = jdbc.queryForObject("select logout_at::text from attendance_sessions where id = ?::uuid", String.class, sessionId);
        Session a = login(admin.getUsername());
        Instant corrected = clock.instant().plus(Duration.ofHours(2));
        mvc.perform(auth(post("/api/v1/attendance/corrections"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"SESSION\",\"targetId\":\"" + sessionId + "\",\"field\":\"logoutAt\",\"value\":\"" + corrected + "\",\"reason\":\"Forgot to sign out properly\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.originalValue").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ATTENDANCE_CORRECTED' and entity_id = ?", Integer.class, sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from attendance_events where event_type = 'CORRECTION' and user_id = ?", Integer.class, emp.getId())).isEqualTo(1);
        assertThat(original).isNotNull();
    }
}
