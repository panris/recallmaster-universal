package com.recallmaster.universal.report;

import java.util.List;

public record RunComparison(
        String baselineRunId,
        String candidateRunId,
        int commonCases,
        int regressions,
        int improvements,
        int unchanged,
        double averageRecallDelta,
        List<String> onlyInBaseline,
        List<String> onlyInCandidate,
        List<CaseComparison> cases
) {
    public RunComparison {
        onlyInBaseline = onlyInBaseline == null ? List.of() : List.copyOf(onlyInBaseline);
        onlyInCandidate = onlyInCandidate == null ? List.of() : List.copyOf(onlyInCandidate);
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
