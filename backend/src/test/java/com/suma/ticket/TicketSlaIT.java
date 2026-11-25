package com.suma.ticket;

import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketSlaIT extends IntegrationTest {
    @Autowired SlaMonitorService monitor;
    @Autowired TicketService tickets;

    @Test
    void lifecycleSlaPauseBreachAndTimeline() throws Exception {
        User admin = createUser("ADMIN");
        User eng = createUser("L1");
        UUID project = projectWithMember(eng, null, false);
        jdbc.update("insert into sla_policies (id, name, project_id, priority, response_minutes, resolution_minutes, business_hours_only, pause_on_pending) values (?,?,?,?,?,?,false,true)",
                UUID.randomUUID(), "P1 test", project, "HIGH", 30, 120);
        Session a = login(admin.getUsername());
        var created = json(mvc.perform(auth(post("/api/v1/tickets"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + project + "\",\"title\":\"Server down in DC1\",\"priority\":\"HIGH\"}")).andExpect(status().isCreated()).andReturn());
        String id = created.get("summary").get("id").asText();
        Instant createdAt = Instant.parse(created.get("summary").get("createdAt").asText());
        Instant due = Instant.parse(created.get("summary").get("sla").get("resolutionDueAt").asText());
        assertThat(Duration.between(createdAt, due)).isEqualTo(Duration.ofMinutes(120));

        mvc.perform(auth(post("/api/v1/tickets/" + id + "/assign"), a).contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + eng.getId() + "\"}"))
                .andExpect(jsonPath("$.summary.status").value("ASSIGNED"));
        // invalid transition rejected
        mvc.perform(auth(post("/api/v1/tickets/" + id + "/status"), a).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}")).andExpect(status().isBadRequest());
        // pending pauses SLA for 60 minutes
        mvc.perform(auth(post("/api/v1/tickets/" + id + "/status"), a).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\",\"note\":\"Waiting for customer\"}"))
                .andExpect(jsonPath("$.summary.sla.paused").value(true));
        clock.advance(Duration.ofMinutes(60));
        var resumed = json(mvc.perform(auth(post("/api/v1/tickets/" + id + "/status"), a).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}")).andReturn());
        assertThat(Instant.parse(resumed.get("summary").get("sla").get("resolutionDueAt").asText())).isEqualTo(due.plus(Duration.ofMinutes(60)));

        clock.advance(Duration.ofMinutes(200));
        monitor.detectBreaches();
        var detail = json(mvc.perform(auth(get("/api/v1/tickets/" + id), a)).andReturn());
        assertThat(detail.get("summary").get("sla").get("resolutionBreached").asBoolean()).isTrue();
        assertThat(detail.get("history").toString()).contains("SLA_BREACHED", "STATUS_CHANGED", "ASSIGNED", "CREATED");
        mvc.perform(auth(post("/api/v1/tickets/" + id + "/status"), a).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}")).andExpect(status().isBadRequest()); // note required
        mvc.perform(auth(post("/api/v1/tickets/" + id + "/status"), a).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"note\":\"Rebooted\"}"))
                .andExpect(jsonPath("$.summary.status").value("RESOLVED"));
    }

    @Test
    void externalTicketsAreDeduplicated() {
        User eng = createUser("L1");
        UUID project = projectWithMember(eng, null, false);
        var req = new TicketDtos.CreateRequest(project, "From Jira", null, "LOW", null, null, null, null, null, null, null);
        tickets.createInternal(req, "JIRA", "JIRA", "OPS-1", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tickets.createInternal(req, "JIRA", "JIRA", "OPS-1", null))
                .hasMessageContaining("Duplicate");
        assertThat(jdbc.queryForObject("select count(*) from tickets where external_id = 'OPS-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void engineersOnlySeeTheirProjectsTickets() throws Exception {
        User admin = createUser("ADMIN");
        User inside = createUser("L1");
        User outside = createUser("L1");
        UUID project = projectWithMember(inside, null, false);
        projectWithMember(outside, null, false);
        Session a = login(admin.getUsername());
        String id = json(mvc.perform(auth(post("/api/v1/tickets"), a).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + project + "\",\"title\":\"Isolation check\",\"priority\":\"LOW\"}")).andReturn()).get("summary").get("id").asText();
        mvc.perform(auth(get("/api/v1/tickets/" + id), login(inside.getUsername()))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/tickets/" + id), login(outside.getUsername()))).andExpect(status().isNotFound());
    }
}
