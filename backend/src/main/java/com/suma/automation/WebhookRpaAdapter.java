package com.suma.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebhookRpaAdapter implements RpaAdapter {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper mapper;

    public WebhookRpaAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "WEBHOOK";
    }

    @Override
    public Result trigger(String endpoint, String job, Map<String, Object> payload) {
        try {
            URI uri;
            try {
                uri = com.suma.common.OutboundUrlGuard.check(endpoint);
            } catch (IllegalArgumentException e) {
                return new Result(false, 0, e.getMessage());
            }
            Map<String, Object> body = new HashMap<>(payload);
            body.put("job", job);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() >= 200 && res.statusCode() < 300;
            return new Result(ok, res.statusCode(), ok ? "Triggered" : "RPA endpoint returned " + res.statusCode());
        } catch (Exception e) {
            return new Result(false, 0, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
