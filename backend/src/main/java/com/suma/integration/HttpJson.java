package com.suma.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Small HTTP helper shared by REST-based connectors. Credentials are applied here and never logged. */
final class HttpJson {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpJson() {}

    static Map<String, Object> get(String url, Integration i, Map<String, String> creds, ObjectMapper mapper) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(com.suma.common.OutboundUrlGuard.check(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        switch (i.getAuthType()) {
            case "BASIC" -> b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (creds.getOrDefault("username", "") + ":" + creds.getOrDefault("password", "")).getBytes(StandardCharsets.UTF_8)));
            case "BEARER", "OAUTH2" -> b.header("Authorization", "Bearer " + creds.getOrDefault("token", ""));
            case "API_KEY" -> b.header(creds.getOrDefault("headerName", "X-API-Key"), creds.getOrDefault("apiKey", ""));
            default -> { }
        }
        HttpResponse<String> res = CLIENT.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + " from " + URI.create(url).getHost());
        }
        String body = res.body().trim();
        if (body.startsWith("[")) return Map.of("items", mapper.readValue(body, new TypeReference<java.util.List<Object>>() {}));
        return mapper.readValue(body, new TypeReference<>() {});
    }
}
