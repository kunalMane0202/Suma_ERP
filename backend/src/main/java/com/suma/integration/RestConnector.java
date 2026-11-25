package com.suma.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Generic REST connector: GET {baseUrl}{query}; records at mapping.itemsPath (default "items", or a top-level array). */
@Component
public class RestConnector implements TicketConnector {
    private final ObjectMapper mapper;

    public RestConnector(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() { return "REST"; }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetch(Integration i, Map<String, String> creds) throws Exception {
        String url = JiraConnector.trim(i.getBaseUrl()) + (i.getQuery() == null ? "" : i.getQuery());
        Map<String, Object> res = HttpJson.get(url, i, creds, mapper);
        String itemsPath = "items";
        if (i.getFieldMapping() != null) {
            Map<String, Object> m = mapper.readValue(i.getFieldMapping(), Map.class);
            if (m.get("itemsPath") != null) itemsPath = m.get("itemsPath").toString();
        }
        Object items = FieldMapper.extract(res, itemsPath);
        return items instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @Override
    public String test(Integration i, Map<String, String> creds) throws Exception {
        return "Fetched " + fetch(i, creds).size() + " record(s)";
    }

    @Override
    public Map<String, Object> defaultMapping() {
        return Map.of("itemsPath", "items", "externalId", "id", "title", "title", "description", "description", "priority", "priority");
    }
}
