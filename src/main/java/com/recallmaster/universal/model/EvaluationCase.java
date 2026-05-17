package com.recallmaster.universal.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvaluationCase(
        String id,
        String question,
        List<String> intents,
        List<String> expectedIds,
        Map<String, String> filters,
        LabelStatus labelStatus
) {
    public EvaluationCase {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        intents = intents == null ? List.of() : List.copyOf(intents);
        expectedIds = expectedIds == null ? List.of() : List.copyOf(expectedIds);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        labelStatus = labelStatus == null ? LabelStatus.MODEL_PROPOSED : labelStatus;
    }
}
