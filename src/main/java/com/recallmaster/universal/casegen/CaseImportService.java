package com.recallmaster.universal.casegen;

import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.LabelStatus;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
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

    public ImportedCaseSet importCases(String content, String filename) {
        String normalized = (filename != null ? filename.toLowerCase() : "");
        if (normalized.endsWith(".jsonl")) {
            return importJsonl(content);
        } else if (normalized.endsWith(".csv")) {
            return importCsv(content);
        } else {
            // Try JSON first, fallback to JSONL
            try {
                return importJson(content);
            } catch (IllegalArgumentException e) {
                return importJsonl(content);
            }
        }
    }

    public ImportedCaseSet importJsonl(String jsonl) {
        List<EvaluationCase> cases = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int lineNum = 0;
        try (BufferedReader reader = new BufferedReader(new StringReader(jsonl))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    EvaluationCase c = parseCaseWithAliases(line);
                    cases.add(c);
                } catch (Exception e) {
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read JSONL: " + e.getMessage(), e);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("JSONL parse errors: " + String.join("; ", errors));
        }
        return describe(cases);
    }

    public ImportedCaseSet importCsv(String csv) {
        List<EvaluationCase> cases = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV is empty");
            }
            String[] headers = parseCsvLine(headerLine);
            int lineNum = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    String[] values = parseCsvLine(line);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                        row.put(headers[i].trim(), values[i].trim());
                    }
                    cases.add(buildCaseFromRow(row, lineNum));
                } catch (Exception e) {
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read CSV: " + e.getMessage(), e);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("CSV parse errors: " + String.join("; ", errors));
        }
        return describe(cases);
    }

    private EvaluationCase parseCaseWithAliases(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            // Alias normalization
            if (!map.containsKey("question") && map.containsKey("query")) {
                map.put("question", map.remove("query"));
            }
            if (!map.containsKey("expectedIds") && map.containsKey("expected_ids")) {
                map.put("expectedIds", map.remove("expected_ids"));
            }
            return objectMapper.convertValue(map, EvaluationCase.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid case JSON: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private EvaluationCase buildCaseFromRow(Map<String, String> row, int lineNum) {
        String question = row.getOrDefault("question", row.getOrDefault("query", ""));
        if (question.isBlank()) {
            throw new IllegalArgumentException("Missing question at line " + lineNum);
        }
        String id = row.getOrDefault("id", null);
        List<String> intents = splitList(row.getOrDefault("intents", ""));
        List<String> expectedIds = splitList(row.getOrDefault("expectedIds",
                row.getOrDefault("expected_ids", "")));
        Map<String, String> filters = new LinkedHashMap<>();
        String filtersStr = row.getOrDefault("filters", "");
        if (!filtersStr.isBlank()) {
            try {
                filters = objectMapper.readValue(filtersStr, Map.class);
            } catch (Exception ignored) {}
        }
        LabelStatus labelStatus;
        try {
            labelStatus = LabelStatus.valueOf(row.getOrDefault("labelStatus", "MODEL_PROPOSED"));
        } catch (IllegalArgumentException e) {
            labelStatus = LabelStatus.MODEL_PROPOSED;
        }
        return new EvaluationCase(id, question, intents, expectedIds, filters, labelStatus);
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
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
