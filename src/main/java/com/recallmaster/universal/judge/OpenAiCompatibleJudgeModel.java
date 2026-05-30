package com.recallmaster.universal.judge;

import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.JudgeVerdict;
import com.recallmaster.universal.model.SearchResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAiCompatibleJudgeModel implements JudgeModel {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String name;
    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public OpenAiCompatibleJudgeModel(String name, String model, String baseUrl, String apiKey, ObjectMapper objectMapper) {
        this.name = name;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JudgeVerdict judge(EvaluationCase evaluationCase, List<SearchResult> retrieved) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Judge " + name + " requires an OpenAI-compatible baseUrl");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(evaluationCase, retrieved))
                ));
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Judge " + name + " returned HTTP " + response.statusCode() + ": " + response.body());
            }
            Map<String, Object> parsed = objectMapper.readValue(response.body(), MAP_TYPE);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.getOrDefault("choices", List.of());
            if (choices.isEmpty()) {
                throw new IllegalStateException("Judge " + name + " returned no choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            Map<String, Object> verdict = objectMapper.readValue(String.valueOf(message.get("content")), MAP_TYPE);
            return new JudgeVerdict(
                    name,
                    number(verdict.get("score"), 0).intValue(),
                    stringList(verdict.get("covered_intents")),
                    stringList(verdict.get("missing_intents")),
                    number(verdict.get("noise_ratio"), 0).doubleValue(),
                    String.valueOf(verdict.getOrDefault("summary", "")),
                    String.valueOf(verdict.getOrDefault("suggestion", "")));
        } catch (JacksonException ex) {
            throw new IllegalStateException("Judge " + name + " returned invalid JSON", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call judge " + name, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling judge " + name, ex);
        }
    }

    private String systemPrompt() {
        return """
                You are a strict RAG retrieval evaluator. Judge whether retrieved chunks cover every user intent.
                Return only a JSON object with: score (0-100), covered_intents (array), missing_intents (array),
                noise_ratio (0-1), summary (Chinese), suggestion (Chinese).
                Be specific about the missing intent. Do not judge answer fluency; judge retrieval coverage only.
                """;
    }

    private String userPrompt(EvaluationCase evaluationCase, List<SearchResult> retrieved) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("question", evaluationCase.question());
            payload.put("intents", evaluationCase.intents());
            payload.put("expected_ids", evaluationCase.expectedIds());
            payload.put("retrieved", retrieved.stream()
                    .map(result -> Map.of(
                            "id", result.id(),
                            "score", result.score(),
                            "text", result.text(),
                            "metadata", result.metadata()))
                    .toList());
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize user prompt", e);
        }
    }

    private Number number(Object value, Number defaultValue) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
