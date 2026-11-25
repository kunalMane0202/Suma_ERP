package com.suma.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class SalesforceConnector implements TicketConnector {
    private final ObjectMapper mapper;

    public SalesforceConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "SALESFORCE"; }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetch(Integration i, Map<String, String> creds) throws Exception {
        String soql = i.getQuery() == null || i.getQuery().isBlank()
                ? "SELECT Id, CaseNumber, Subject, Description, Priority, SuppliedName, SuppliedEmail FROM Case WHERE LastModifiedDate = LAST_N_DAYS:1"
                : i.getQuery();
        String url = JiraConnector.trim(i.getBaseUrl()) + "/services/data/v59.0/query?q=" + URLEncoder.encode(soql, StandardCharsets.UTF_8);
        return (List<Map<String, Object>>) HttpJson.get(url, i, creds, mapper).getOrDefault("records", List.of());
    }

    @Override
    public String test(Integration i, Map<String, String> creds) throws Exception {
        HttpJson.get(JiraConnector.trim(i.getBaseUrl()) + "/services/data/", i, creds, mapper);
        return "Connected to Salesforce REST API";
    }

    @Override
    public Map<String, Object> defaultMapping() {
        return Map.of("externalId", "CaseNumber", "title", "Subject", "description", "Description", "priority", "Priority",
                "reporterName", "SuppliedName", "reporterEmail", "SuppliedEmail",
                "priorityMap", Map.of("High", "HIGH", "Medium", "MEDIUM", "Low", "LOW", "Critical", "CRITICAL"));
    }
}
