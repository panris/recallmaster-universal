package com.recallmaster.universal.model;

import java.util.List;

public record JudgeVerdict(
        String judgeName,
        int score,
        List<String> coveredIntents,
        List<String> missingIntents,
        double noiseRatio,
        String summary,
        String suggestion
) {
    public JudgeVerdict {
        score = Math.max(0, Math.min(100, score));
        coveredIntents = coveredIntents == null ? List.of() : List.copyOf(coveredIntents);
        missingIntents = missingIntents == null ? List.of() : List.copyOf(missingIntents);
        noiseRatio = Math.max(0, Math.min(1, noiseRatio));
        summary = summary == null ? "" : summary;
        suggestion = suggestion == null ? "" : suggestion;
    }
}
