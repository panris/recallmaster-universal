package com.recallmaster.universal.casegen;

import java.util.List;

public record CaseGenerationRequest(
        List<String> sourcePaths,
        int maxCases,
        boolean requireHumanVerification
) {
    public CaseGenerationRequest {
        sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        maxCases = maxCases < 1 ? 10 : maxCases;
    }
}
