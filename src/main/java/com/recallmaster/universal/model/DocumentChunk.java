package com.recallmaster.universal.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentChunk(
        String id,
        String text,
        float[] vector,
        Map<String, String> metadata
) {
    public DocumentChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        vector = vector == null ? new float[0] : vector;
    }

    public static DocumentChunk of(String id, String text) {
        return new DocumentChunk(id, text, null, new LinkedHashMap<>());
    }

    public static DocumentChunk of(String id, String text, float[] vector) {
        return new DocumentChunk(id, text, vector, new LinkedHashMap<>());
    }
}
