package com.recallmaster.universal.connector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpJsonClient {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_MS = 500;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    public HttpJsonClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> postJson(String url, Object body, String apiKey) {
        return postJson(url, body, apiKey, 0);
    }

    private Map<String, Object> postJson(String url, Object body, String apiKey, int attempt) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            applyAuth(builder, apiKey);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 && attempt < MAX_RETRIES) {
                sleep(RETRY_BASE_MS * (1L << attempt));
                return postJson(url, body, apiKey, attempt + 1);
            }
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (IOException ex) {
            if (attempt < MAX_RETRIES) {
                sleep(RETRY_BASE_MS * (1L << attempt));
                return postJson(url, body, apiKey, attempt + 1);
            }
            throw new IllegalStateException("Failed to call " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + url, ex);
        }
    }

    public void postNdjson(String url, String ndjson, String apiKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(ndjson));
            applyAuth(builder, apiKey);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url + ": " + response.body());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling " + url, ex);
        }
    }

    private void applyAuth(HttpRequest.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            if (apiKey.startsWith("ApiKey ") || apiKey.startsWith("Bearer ")) {
                builder.header("Authorization", apiKey);
            } else {
                builder.header("Authorization", "Bearer " + apiKey);
            }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
