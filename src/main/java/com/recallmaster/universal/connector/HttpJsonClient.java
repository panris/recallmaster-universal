package com.recallmaster.universal.connector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class HttpJsonClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    HttpJsonClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Object> postJson(String url, Object body, String apiKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                if (apiKey.startsWith("ApiKey ") || apiKey.startsWith("Bearer ")) {
                    builder.header("Authorization", apiKey);
                } else {
                    builder.header("Authorization", "Bearer " + apiKey);
                }
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + url, ex);
        }
    }
}
