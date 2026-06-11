package com.recallmaster.universal.task;

import com.recallmaster.universal.model.EvaluationCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EvaluationRunRequest(
        @Size(max = 100, message = "database name too long") String database,
        @Min(value = 1, message = "topK must be at least 1") @Max(value = 100, message = "topK must be at most 100") int topK,
        @NotEmpty(message = "cases must not be empty") @Size(max = 500, message = "too many cases, max 500") List<EvaluationCase> cases
) {
    public EvaluationRunRequest {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
