package com.recallmaster.universal.llm;

import com.recallmaster.universal.connector.HttpJsonClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LlmClient {

    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final HttpJsonClient http;

    public LlmClient(String model, String baseUrl, String apiKey, ObjectMapper objectMapper) {
        this.model = model;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.http = new HttpJsonClient(objectMapper);
    }

    @SuppressWarnings("unchecked")
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        Map<String, Object> response = http.postJson(baseUrl + "/chat/completions", body, apiKey);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            throw new IllegalStateException("LLM returned no choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        return String.valueOf(message.get("content"));
    }

    @SuppressWarnings("unchecked")
    public String chatJson(String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        Map<String, Object> response = http.postJson(baseUrl + "/chat/completions", body, apiKey);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getOrDefault("choices", List.of());
        if (choices.isEmpty()) {
            throw new IllegalStateException("LLM returned no choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        return String.valueOf(message.get("content"));
    }

    private String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.openai.com/v1";
        }
        String normalized = url.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1") || normalized.endsWith("/v2")) {
            return normalized;
        }
        return normalized + "/v1";
    }
}
