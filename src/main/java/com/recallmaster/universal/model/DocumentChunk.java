package com.recallmaster.universal.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentChunk(
        String id,
        String text,
        Map<String, String> metadata
) {
    public DocumentChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DocumentChunk of(String id, String text) {
        return new DocumentChunk(id, text, new LinkedHashMap<>());
    }
}
