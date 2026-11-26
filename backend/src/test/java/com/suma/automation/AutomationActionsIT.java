package com.suma.automation;

import com.sun.net.httpserver.HttpServer;
import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each of the 12 automation actions, run by a TICKET_CREATED rule scoped to a fresh project. */
class AutomationActionsIT extends IntegrationTest {

    private record Fixture(User admin, User eng, UUID project, Session session) {}

    private Fixture fixture() throws Exception {
        User admin = createUser("ADMIN");
        User eng = createUser("L1");
        UUID project = projectWithMember(eng, null, false);
        return new Fixture(admin, eng, project, login(admin.getUsername()));
    }

    private void rule(UUID project, String actionsJson) {
        jdbc.update("insert into automation_rules (id, name, trigger_event, conditions, actions, project_id) values (?,?,?,?,?,?)",
                UUID.randomUUID(), "IT rule " + UUID.randomUUID(), "TICKET_CREATED", "[]", actionsJson, project);
    }

    private UUID ticket(Fixture f, String title) throws Exception {
        var r = mvc.perform(auth(post("/api/v1/tickets"), f.session()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\"" + f.project() + "\",\"title\":\"" + title + "\",\"priority\":\"LOW\"}")).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json(r).get("summary").get("id").asText());
    }

    private Map<String, Object> row(UUID ticket) {
        return jdbc.queryForMap("select assignee_id, priority, category_id, subcategory_id, escalation_level, status from tickets where id = ?", ticket);
    }

    private String lastExecution(UUID ticket) {
        return jdbc.queryForObject("select status || ':' || coalesce(actions_run, error, '') from automation_executions where entity_id = ? order by created_at desc limit 1",
                String.class, ticket.toString());
    }

    @Test
    void assignUser() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"ASSIGN_USER\",\"userId\":\"" + f.eng().getId() + "\"}]");
        UUID t = ticket(f, "Assign me");
        assertThat(row(t).get("assignee_id")).isEqualTo(f.eng().getId());
    }

    @Test
    void assignLeastLoadedPicksTheEngineerWithFewestOpenTickets() throws Exception {
        Fixture f = fixture();
        User busy = f.eng();
        User free = createUser("L2");
        jdbc.update("insert into project_members (project_id, user_id, designation) values (?, ?, 'MEMBER')", f.project(), free.getId());
        // give the first engineer two open tickets before the rule exists
        for (int i = 0; i < 2; i++) {
            UUID t = ticket(f, "Existing " + i);
            jdbc.update("update tickets set assignee_id = ?, status = 'ASSIGNED' where id = ?", busy.getId(), t);
        }
        rule(f.project(), "[{\"type\":\"ASSIGN_LEAST_LOADED\"}]");
        UUID t = ticket(f, "Route me");
        assertThat(row(t).get("assignee_id")).isEqualTo(free.getId());
    }

    @Test
    void setPriority() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"SET_PRIORITY\",\"value\":\"CRITICAL\"}]");
        assertThat(row(ticket(f, "Raise me")).get("priority")).isEqualTo("CRITICAL");
    }

    @Test
    void setCategoryAndAutoCategorize() throws Exception {
        Fixture f = fixture();
        UUID fixed = UUID.randomUUID();
        UUID network = UUID.randomUUID();
        jdbc.update("insert into ticket_categories (id, project_id, name) values (?,?,?)", fixed, f.project(), "Fixed");
        jdbc.update("insert into ticket_categories (id, project_id, name, keywords) values (?,?,?,?)", network, f.project(), "Network", "zqvpnx, tunnelzq");
        rule(f.project(), "[{\"type\":\"AUTO_CATEGORIZE\"}]");
        assertThat(row(ticket(f, "The zqvpnx keeps dropping")).get("category_id")).isEqualTo(network);
        assertThat(row(ticket(f, "Nothing matches here")).get("category_id")).isNull();

        Fixture g = fixture();
        UUID chosen = UUID.randomUUID();
        jdbc.update("insert into ticket_categories (id, project_id, name) values (?,?,?)", chosen, g.project(), "Chosen");
        rule(g.project(), "[{\"type\":\"SET_CATEGORY\",\"categoryId\":\"" + chosen + "\"}]");
        assertThat(row(ticket(g, "Set my category")).get("category_id")).isEqualTo(chosen);
    }

    @Test
    void notifyUserAndRole() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"NOTIFY_USER\",\"userId\":\"" + f.admin().getId() + "\",\"message\":\"Look at this\"},{\"type\":\"NOTIFY_ROLE\",\"role\":\"L1\",\"message\":\"For L1\"}]");
        UUID t = ticket(f, "Tell people");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'AUTOMATION' and entity_id = ? and message = 'Look at this'",
                Integer.class, f.admin().getId(), t.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'AUTOMATION' and entity_id = ? and message = 'For L1'",
                Integer.class, f.eng().getId(), t.toString())).isEqualTo(1);
    }

    @Test
    void escalate() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"ESCALATE\",\"level\":\"2\",\"reason\":\"VIP client\"}]");
        UUID t = ticket(f, "Escalate me");
        assertThat(((Number) row(t).get("escalation_level")).intValue()).isEqualTo(2);
    }

    @Test
    void addComment() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"ADD_COMMENT\",\"body\":\"Checked by automation\",\"internal\":\"true\"}]");
        UUID t = ticket(f, "Comment on me");
        assertThat(jdbc.queryForObject("select count(*) from ticket_comments where ticket_id = ? and body = 'Checked by automation' and internal", Integer.class, t)).isEqualTo(1);
    }

    @Test
    void requestApprovalCreatesPendingRequestAndNotifiesApprover() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"REQUEST_APPROVAL\",\"approverId\":\"" + f.admin().getId() + "\",\"reason\":\"Needs sign-off\"}]");
        UUID t = ticket(f, "Approve me");
        assertThat(jdbc.queryForObject("select status from approval_requests where ticket_id = ?", String.class, t)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'APPROVAL_REQUESTED' and entity_id = ?",
                Integer.class, f.admin().getId(), t.toString())).isEqualTo(1);
    }

    @Test
    void setStatus() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"SET_STATUS\",\"status\":\"IN_PROGRESS\",\"note\":\"Auto start\"}]");
        assertThat(row(ticket(f, "Start me")).get("status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    void rpaTriggerPostsToTheEndpointAndRefusesMetadataAddresses() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", ex -> {
            received.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(202, -1);
            ex.close();
        });
        server.start();
        System.setProperty("suma.outbound.allowLoopback", "true");
        try {
            Fixture f = fixture();
            rule(f.project(), "[{\"type\":\"RPA_TRIGGER\",\"endpoint\":\"http://127.0.0.1:" + server.getAddress().getPort() + "/hook\",\"job\":\"reset-password\"}]");
            UUID t = ticket(f, "Robot please");
            assertThat(received.get()).contains("\"job\":\"reset-password\"").contains(t.toString());
            assertThat(lastExecution(t)).isEqualTo("MATCHED:rpa ok");
            assertThat(jdbc.queryForObject("select count(*) from ticket_history where ticket_id = ? and action = 'RPA_TRIGGERED'", Integer.class, t)).isEqualTo(1);

            Fixture g = fixture();
            rule(g.project(), "[{\"type\":\"RPA_TRIGGER\",\"endpoint\":\"http://169.254.169.254/latest/meta-data/\"}]");
            UUID blocked = ticket(g, "Steal metadata");
            assertThat(lastExecution(blocked)).startsWith("MATCHED:rpa failed").contains("not allowed");
        } finally {
            System.clearProperty("suma.outbound.allowLoopback");
            server.stop(0);
        }
    }

    @Test
    void failingActionIsRecordedAndDoesNotBlockTheTicket() throws Exception {
        Fixture f = fixture();
        rule(f.project(), "[{\"type\":\"SET_PRIORITY\",\"value\":\"URGENT\"}]");
        UUID t = ticket(f, "Bad rule");
        assertThat(row(t).get("priority")).isEqualTo("LOW");
        assertThat(lastExecution(t)).startsWith("FAILED:Invalid priority");
    }
}
