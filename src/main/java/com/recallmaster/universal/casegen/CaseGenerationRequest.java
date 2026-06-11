package com.recallmaster.universal.casegen;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CaseGenerationRequest(
        @NotEmpty(message = "sourcePaths must not be empty") @Size(max = 50, message = "too many source paths, max 50") List<String> sourcePaths,
        @Min(value = 1, message = "maxCases must be at least 1") @Max(value = 1000, message = "maxCases must be at most 1000") int maxCases,
        boolean requireHumanVerification
) {
    public CaseGenerationRequest {
        sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
        maxCases = maxCases < 1 ? 10 : maxCases;
    }
}
