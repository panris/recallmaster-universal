package com.recallmaster.universal.report;

import java.util.List;
import java.util.Map;

public record RunSummary(
        String runId,
        String database,
        String status,
        int total,
        int completed,
        double averageRecall,
        double averageIntentCoverage,
        double averageNoiseRatio,
        int fullRecall,
        int partialRecall,
        int missed,
        int needsHumanReview,
        Map<String, Integer> missingIntentCounts,
        List<String> suggestions
) {
    public RunSummary {
        missingIntentCounts = missingIntentCounts == null ? Map.of() : Map.copyOf(missingIntentCounts);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
