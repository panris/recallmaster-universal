package com.recallmaster.universal.casegen;

import com.recallmaster.universal.model.EvaluationCase;
import java.util.List;
import java.util.Map;

public record ImportedCaseSet(
        int count,
        List<EvaluationCase> cases,
        Map<String, Integer> labelStatusCounts,
        List<String> warnings
) {
    public ImportedCaseSet {
        cases = cases == null ? List.of() : List.copyOf(cases);
        labelStatusCounts = labelStatusCounts == null ? Map.of() : Map.copyOf(labelStatusCounts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
