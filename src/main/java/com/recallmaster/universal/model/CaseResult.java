package com.recallmaster.universal.model;

import java.time.Instant;
import java.util.List;

public record CaseResult(
        EvaluationCase caseInfo,
        String database,
        RetrievalMetrics retrievalMetrics,
        AiAnalysis aiAnalysis,
        List<SearchResult> retrieved,
        EvaluationStatus status,
        Instant finishedAt
) {
    public CaseResult {
        retrieved = retrieved == null ? List.of() : List.copyOf(retrieved);
        finishedAt = finishedAt == null ? Instant.now() : finishedAt;
    }
}
