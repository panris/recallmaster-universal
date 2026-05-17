package com.recallmaster.universal.casegen;

import com.recallmaster.universal.model.EvaluationCase;
import java.util.List;

public record GeneratedCaseSet(
        List<EvaluationCase> cases,
        String note
) {
    public GeneratedCaseSet {
        cases = cases == null ? List.of() : List.copyOf(cases);
        note = note == null ? "" : note;
    }
}
