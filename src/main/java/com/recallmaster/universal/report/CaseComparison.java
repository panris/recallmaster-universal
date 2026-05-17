package com.recallmaster.universal.report;

import java.util.List;

public record CaseComparison(
        String caseId,
        String question,
        String baselineStatus,
        String candidateStatus,
        double baselineRecall,
        double candidateRecall,
        double recallDelta,
        List<String> newlyMissedIds,
        List<String> recoveredIds,
        String baselineSummary,
        String candidateSummary
) {
    public CaseComparison {
        newlyMissedIds = newlyMissedIds == null ? List.of() : List.copyOf(newlyMissedIds);
        recoveredIds = recoveredIds == null ? List.of() : List.copyOf(recoveredIds);
        baselineSummary = baselineSummary == null ? "" : baselineSummary;
        candidateSummary = candidateSummary == null ? "" : candidateSummary;
    }
}
