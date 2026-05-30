package com.recallmaster.universal.embedding;

import com.recallmaster.universal.connector.HttpJsonClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAiCompatibleEmbeddingModel implements EmbeddingModel {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final int dimensions;
    private final HttpJsonClient http;

    public OpenAiCompatibleEmbeddingModel(String name, String baseUrl, String apiKey, int dimensions, ObjectMapper objectMapper) {
        this.name = name;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.dimensions = dimensions;
        this.http = new HttpJsonClient(objectMapper);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", text);
        body.put("model", name);
        if (dimensions > 0) {
            body.put("dimensions", dimensions);
        }
        Map<String, Object> response = http.postJson(baseUrl + "/embeddings", body, apiKey);
        Object rawData = response.get("data");
        if (!(rawData instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("OpenAI embedding response missing data array");
        }
        if (!(list.getFirst() instanceof Map<?, ?> first)) {
            throw new IllegalStateException("OpenAI embedding response data[0] is not an object");
        }
        Object embedding = first.get("embedding");
        if (!(embedding instanceof List<?> vector)) {
            throw new IllegalStateException("OpenAI embedding response missing embedding array");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = ((Number) vector.get(i)).floatValue();
        }
        return result;
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
