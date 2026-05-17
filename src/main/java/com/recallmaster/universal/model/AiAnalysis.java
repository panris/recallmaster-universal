package com.recallmaster.universal.model;

import java.util.List;

public record AiAnalysis(
        int score,
        double intentCoverage,
        double noiseRatio,
        boolean needsHumanReview,
        boolean judgeDisagreement,
        String summary,
        String suggestion,
        List<JudgeVerdict> judgeVerdicts
) {
    public AiAnalysis {
        score = Math.max(0, Math.min(100, score));
        intentCoverage = Math.max(0, Math.min(1, intentCoverage));
        noiseRatio = Math.max(0, Math.min(1, noiseRatio));
        summary = summary == null ? "" : summary;
        suggestion = suggestion == null ? "" : suggestion;
        judgeVerdicts = judgeVerdicts == null ? List.of() : List.copyOf(judgeVerdicts);
    }
}
