package com.recallmaster.universal.model;

import java.util.Map;

public record SearchResult(
        String id,
        String text,
        double score,
        Map<String, String> metadata
) {
    public SearchResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
