package com.recallmaster.universal.casegen;

import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.LabelStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CaseImportService {

    private static final TypeReference<List<EvaluationCase>> CASE_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public CaseImportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ImportedCaseSet importJson(String json) {
        try {
            List<EvaluationCase> cases = objectMapper.readValue(json, CASE_LIST);
            return describe(cases);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid case JSON: " + ex.getMessage(), ex);
        }
    }

    public String exportJson(List<EvaluationCase> cases) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cases);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to export cases as JSON", ex);
        }
    }

    private ImportedCaseSet describe(List<EvaluationCase> cases) {
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            labelCounts.merge(evaluationCase.labelStatus().name(), 1, Integer::sum);
            if (evaluationCase.expectedIds().isEmpty()) {
                warnings.add("Case " + evaluationCase.id() + " has no expectedIds, hard Recall@K will be 0.");
            }
            if (evaluationCase.labelStatus() == LabelStatus.MODEL_PROPOSED
                    || evaluationCase.labelStatus() == LabelStatus.NEEDS_REVIEW) {
                warnings.add("Case " + evaluationCase.id() + " requires review before acceptance-grade reporting.");
            }
        }
        return new ImportedCaseSet(cases.size(), cases, labelCounts, warnings.stream().limit(50).toList());
    }
}
