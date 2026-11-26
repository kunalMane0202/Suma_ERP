package com.suma.integration;

import com.sun.net.httpserver.HttpServer;
import com.suma.support.IntegrationTest;
import com.suma.user.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Jira, Salesforce and generic REST connectors against a local stub server: auth headers, mapping, de-duplication, retries. */
class ConnectorIT extends IntegrationTest {
    static HttpServer server;
    static String base;
    static final Map<String, String> authSeen = new ConcurrentHashMap<>();
    static final String RUN = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void start() throws Exception {
        System.setProperty("suma.outbound.allowLoopback", "true");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        reply("/jira/rest/api/2/search", 200, """
                {"issues":[{"key":"OPS-%1$s","fields":{"summary":"VPN down for Pune","description":"Since 9am","priority":{"name":"Highest"},
                  "reporter":{"displayName":"Asha Rao","emailAddress":"asha@example.com"}}}]}""".formatted(RUN));
        reply("/sf/services/data/v59.0/query", 200, """
                {"records":[{"CaseNumber":"C-%1$s","Subject":"Invoice portal error","Description":"500 on submit","Priority":"High",
                  "SuppliedName":"Ravi","SuppliedEmail":"ravi@example.com"}]}""".formatted(RUN));
        reply("/rest/api/tickets", 200, """
                {"items":[{"id":"R-%1$s-1","title":"Printer offline","priority":"LOW"},{"id":"R-%1$s-2","title":"  ","priority":"LOW"},{"title":"No id","priority":"LOW"}]}""".formatted(RUN));
        reply("/broken", 500, "{\"error\":\"boom\"}");
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    static void reply(String path, int code, String body) {
        server.createContext(path, ex -> {
            authSeen.put(path, String.valueOf(ex.getRequestHeaders().getFirst("Authorization")) + "|" + String.valueOf(ex.getRequestHeaders().getFirst("X-Api-Key")));
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
    }

    @AfterAll
    static void stop() {
        System.clearProperty("suma.outbound.allowLoopback");
        server.stop(0);
    }

    private String create(Session s, UUID project, String type, String url, String authType, String creds, String query, int retries) throws Exception {
        String body = "{\"name\":\"" + type + " " + UUID.randomUUID().toString().substring(0, 8) + "\",\"connectorType\":\"" + type + "\",\"projectId\":\"" + project + "\",\"baseUrl\":\"" + url
                + "\",\"authType\":\"" + authType + "\",\"credentials\":" + creds + (query == null ? "" : ",\"query\":\"" + query + "\"")
                + ",\"maxRetries\":" + retries + ",\"enabled\":false}";
        return json(mvc.perform(auth(post("/api/v1/integrations"), s).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private com.fasterxml.jackson.databind.JsonNode sync(Session s, String id) throws Exception {
        return json(mvc.perform(auth(post("/api/v1/integrations/" + id + "/sync"), s)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void jiraIssuesBecomeTicketsWithMappedPriorityAndAreNotDuplicated() throws Exception {
        User admin = createUser("ADMIN");
        UUID project = projectWithMember(createUser("L1"), null, false);
        Session s = login(admin.getUsername());
        String id = create(s, project, "JIRA", base + "/jira", "BASIC", "{\"username\":\"svc\",\"password\":\"s3cret\"}", null, 0);
        var first = sync(s, id);
        assertThat(first.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(first.get("created").asInt()).isEqualTo(1);
        assertThat(authSeen.get("/jira/rest/api/2/search")).startsWith("Basic " + java.util.Base64.getEncoder().encodeToString("svc:s3cret".getBytes()));
        Map<String, Object> t = jdbc.queryForMap("select title, priority, source, reporter_email from tickets where external_system = 'JIRA' and external_id = ?", "OPS-" + RUN);
        assertThat(t.get("title")).isEqualTo("VPN down for Pune");
        assertThat(t.get("priority")).isEqualTo("CRITICAL");
        assertThat(t.get("source")).isEqualTo("JIRA");
        assertThat(t.get("reporter_email")).isEqualTo("asha@example.com");
        var second = sync(s, id);
        assertThat(second.get("created").asInt()).isZero();
        assertThat(second.get("duplicates").asInt()).isEqualTo(1);
        // the stored credentials are encrypted and never returned
        String stored = jdbc.queryForObject("select credentials_encrypted from integrations where id = ?::uuid", String.class, id);
        assertThat(stored).doesNotContain("s3cret");
        assertThat(mvc.perform(auth(get("/api/v1/integrations"), s)).andReturn().getResponse().getContentAsString()).doesNotContain("s3cret");
    }

    @Test
    void salesforceCasesUseBearerToken() throws Exception {
        User admin = createUser("ADMIN");
        UUID project = projectWithMember(createUser("L1"), null, false);
        Session s = login(admin.getUsername());
        String id = create(s, project, "SALESFORCE", base + "/sf", "BEARER", "{\"token\":\"tok-123\"}", "SELECT Id FROM Case", 0);
        assertThat(sync(s, id).get("created").asInt()).isEqualTo(1);
        assertThat(authSeen.get("/sf/services/data/v59.0/query")).startsWith("Bearer tok-123");
        assertThat(jdbc.queryForObject("select priority from tickets where external_system = 'SALESFORCE' and external_id = ?", String.class, "C-" + RUN)).isEqualTo("HIGH");
    }

    @Test
    void restConnectorUsesApiKeyAndCountsBadRecordsAsPartial() throws Exception {
        User admin = createUser("ADMIN");
        UUID project = projectWithMember(createUser("L1"), null, false);
        Session s = login(admin.getUsername());
        String id = create(s, project, "REST", base + "/rest", "API_KEY", "{\"headerName\":\"X-Api-Key\",\"apiKey\":\"k-9\"}", "/api/tickets", 0);
        var r = sync(s, id);
        assertThat(r.get("created").asInt()).isEqualTo(2);
        assertThat(r.get("failed").asInt()).isEqualTo(1); // the record without an external id
        // a blank title falls back to a readable placeholder instead of an empty ticket title
        assertThat(jdbc.queryForObject("select title from tickets where external_system = 'REST' and external_id = ?", String.class, "R-" + RUN + "-2"))
                .isEqualTo("(no subject) R-" + RUN + "-2");
        assertThat(r.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(authSeen.get("/rest/api/tickets")).endsWith("|k-9");
    }

    @Test
    void failingEndpointIsRetriedLoggedAndCounted() throws Exception {
        User admin = createUser("ADMIN");
        UUID project = projectWithMember(createUser("L1"), null, false);
        Session s = login(admin.getUsername());
        String id = create(s, project, "REST", base, "NONE", "{}", "/broken", 2);
        var r = sync(s, id);
        assertThat(r.get("status").asText()).isEqualTo("FAILED");
        assertThat(r.get("error").asText()).contains("HTTP 500");
        assertThat(jdbc.queryForObject("select count(*) from integration_sync_logs where integration_id = ?::uuid and status = 'FAILED'", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select consecutive_failures from integrations where id = ?::uuid", Integer.class, id)).isEqualTo(1);
    }
}
