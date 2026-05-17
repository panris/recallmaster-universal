package com.recallmaster.universal.model;

import java.util.List;

public record RetrievalMetrics(
        List<String> hitIds,
        List<String> missIds,
        double recallRate,
        int topK
) {
    public RetrievalMetrics {
        hitIds = hitIds == null ? List.of() : List.copyOf(hitIds);
        missIds = missIds == null ? List.of() : List.copyOf(missIds);
        recallRate = Math.max(0, Math.min(1, recallRate));
    }
}
