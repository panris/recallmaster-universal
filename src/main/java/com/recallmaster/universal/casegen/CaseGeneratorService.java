package com.recallmaster.universal.casegen;

import com.recallmaster.universal.document.DocumentLoader;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.llm.LlmClient;
import com.recallmaster.universal.model.DocumentChunk;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.LabelStatus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CaseGeneratorService {

    private final DocumentLoader documentLoader;
    private final EmbeddingModel embeddingModel;
    private final LlmClient llmClient;

    public CaseGeneratorService(DocumentLoader documentLoader, EmbeddingModel embeddingModel, LlmClient llmClient) {
        this.documentLoader = documentLoader;
        this.embeddingModel = embeddingModel;
        this.llmClient = llmClient;
    }

    public GeneratedCaseSet generate(CaseGenerationRequest request) {
        List<DocumentChunk> corpus = new ArrayList<>();
        for (String sourcePath : request.sourcePaths()) {
            corpus.addAll(documentLoader.load(Path.of(sourcePath)));
        }
        if (corpus.isEmpty()) {
            return new GeneratedCaseSet(List.of(), "没有可生成用例的源文档。");
        }
        List<EvaluationCase> cases = new ArrayList<>();
        int limit = Math.min(request.maxCases(), Math.max(1, corpus.size()));
        for (int i = 0; i < limit; i++) {
            DocumentChunk pivot = corpus.get(i % corpus.size());
            List<String> intents = inferIntents(pivot.text());
            String question = composeQuestion(pivot.text(), intents);
            Set<String> expected = new LinkedHashSet<>();
            expected.add(pivot.id());
            for (DocumentChunk candidate : nearestNeighbours(corpus, pivot, 2)) {
                expected.add(candidate.id());
            }
            cases.add(new EvaluationCase(
                    null,
                    question,
                    intents,
                    List.copyOf(expected),
                    pivot.metadata(),
                    request.requireHumanVerification() ? LabelStatus.NEEDS_REVIEW : LabelStatus.MODEL_PROPOSED));
        }
        String note = request.requireHumanVerification()
                ? "已生成候选 Case，建议人工确认 expected_ids 与子意图。"
                : "已生成模型提议 Case，可直接进入批量评测。";
        return new GeneratedCaseSet(cases, note);
    }

    private List<String> inferIntents(String text) {
        String systemPrompt = """
                You are an intent extraction assistant. Given a document chunk, identify 1-3 key intents/topics it covers.
                Return a JSON object with a single field "intents" containing an array of intent strings.
                Intents should be concise Chinese phrases (2-6 characters each).
                Examples: "高可用配置", "负载均衡", "报销额度", "牙科保障", "核心参数".
                If unclear, return ["核心事实", "补充条件"].
                """;
        String userPrompt = "Document chunk:\n" + (text == null ? "" : text);
        try {
            String response = llmClient.chatJson(systemPrompt, userPrompt);
            return parseIntentsJson(response);
        } catch (Exception e) {
            return List.of("核心事实", "补充条件");
        }
    }

    private List<String> parseIntentsJson(String json) {
        try {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end < 0 || end <= start) {
                return List.of("核心事实", "补充条件");
            }
            String arrayContent = json.substring(start + 1, end);
            List<String> intents = new ArrayList<>();
            for (String part : arrayContent.split(",")) {
                String cleaned = part.trim().replace("\"", "").replace("'", "");
                if (!cleaned.isBlank()) {
                    intents.add(cleaned);
                }
            }
            if (intents.isEmpty()) {
                intents.add("核心事实");
            }
            if (intents.size() == 1) {
                intents.add("补充条件");
            }
            return intents.stream().distinct().limit(3).toList();
        } catch (Exception e) {
            return List.of("核心事实", "补充条件");
        }
    }

    private String composeQuestion(String text, List<String> intents) {
        String title = firstSentence(text);
        if (intents.size() >= 2) {
            return title + "，同时说明" + String.join("、", intents.subList(0, 2)) + "。";
        }
        return title + "？";
    }

    private List<DocumentChunk> nearestNeighbours(List<DocumentChunk> corpus, DocumentChunk pivot, int limit) {
        float[] pivotVector = embeddingModel.embed(pivot.text());
        return corpus.stream()
                .filter(chunk -> !chunk.id().equals(pivot.id()))
                .map(chunk -> new RankedChunk(chunk, cosine(pivotVector, embeddingModel.embed(chunk.text()))))
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed())
                .limit(limit)
                .map(RankedChunk::chunk)
                .toList();
    }

    private double cosine(float[] left, float[] right) {
        int n = Math.min(left.length, right.length);
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < n; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "基于文档内容生成的问题";
        }
        String trimmed = text.trim();
        int end = Math.min(trimmed.length(), 80);
        int dot = indexOfSentenceEnd(trimmed, end);
        return trimmed.substring(0, dot).replaceAll("\\s+", " ");
    }

    private int indexOfSentenceEnd(String text, int max) {
        for (int i = 0; i < max; i++) {
            char ch = text.charAt(i);
            if (ch == '。' || ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                return i + 1;
            }
        }
        return max;
    }

    private record RankedChunk(DocumentChunk chunk, double score) {
    }
}
