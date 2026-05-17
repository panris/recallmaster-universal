package com.recallmaster.universal.model;

import java.util.Map;

public record SearchRequest(
        String query,
        float[] queryVector,
        int topK,
        Map<String, String> filters
) {
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}
