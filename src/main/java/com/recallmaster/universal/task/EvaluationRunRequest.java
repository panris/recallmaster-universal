package com.recallmaster.universal.task;

import com.recallmaster.universal.model.EvaluationCase;
import java.util.List;

public record EvaluationRunRequest(
        String database,
        int topK,
        List<EvaluationCase> cases
) {
    public EvaluationRunRequest {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
