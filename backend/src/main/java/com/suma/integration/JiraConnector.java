package com.suma.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class JiraConnector implements TicketConnector {
    private final ObjectMapper mapper;

    public JiraConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "JIRA"; }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetch(Integration i, Map<String, String> creds) throws Exception {
        String jql = i.getQuery() == null || i.getQuery().isBlank() ? "updated >= -1d ORDER BY created DESC" : i.getQuery();
        String url = trim(i.getBaseUrl()) + "/rest/api/2/search?maxResults=50&jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8);
        Map<String, Object> res = HttpJson.get(url, i, creds, mapper);
        return (List<Map<String, Object>>) res.getOrDefault("issues", List.of());
    }

    @Override
    public String test(Integration i, Map<String, String> creds) throws Exception {
        Map<String, Object> me = HttpJson.get(trim(i.getBaseUrl()) + "/rest/api/2/myself", i, creds, mapper);
        return "Connected to Jira as " + me.getOrDefault("displayName", "unknown user");
    }

    @Override
    public Map<String, Object> defaultMapping() {
        return Map.of("externalId", "key", "title", "fields.summary", "description", "fields.description",
                "priority", "fields.priority.name", "reporterName", "fields.reporter.displayName", "reporterEmail", "fields.reporter.emailAddress",
                "priorityMap", Map.of("Highest", "CRITICAL", "High", "HIGH", "Medium", "MEDIUM", "Low", "LOW", "Lowest", "LOW"));
    }

    static String trim(String s) {
        if (s == null) throw new IllegalArgumentException("Base URL is required");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
