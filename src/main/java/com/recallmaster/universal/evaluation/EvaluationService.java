package com.recallmaster.universal.evaluation;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.connector.VectorStoreConnector;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.judge.JudgeModel;
import com.recallmaster.universal.judge.JudgeRegistry;
import com.recallmaster.universal.model.AiAnalysis;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationStatus;
import com.recallmaster.universal.model.JudgeVerdict;
import com.recallmaster.universal.model.LabelStatus;
import com.recallmaster.universal.model.RetrievalMetrics;
import com.recallmaster.universal.model.SearchRequest;
import com.recallmaster.universal.model.SearchResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {

    private final ConnectorRegistry connectorRegistry;
    private final EmbeddingModel embeddingModel;
    private final JudgeRegistry judgeRegistry;
    private final RecallMasterProperties properties;

    public EvaluationService(
            ConnectorRegistry connectorRegistry,
            EmbeddingModel embeddingModel,
            JudgeRegistry judgeRegistry,
            RecallMasterProperties properties
    ) {
        this.connectorRegistry = connectorRegistry;
        this.embeddingModel = embeddingModel;
        this.judgeRegistry = judgeRegistry;
        this.properties = properties;
    }

    public CaseResult evaluate(String databaseName, EvaluationCase evaluationCase, int topK) {
        VectorStoreConnector connector = connectorRegistry.get(databaseName);
        SearchRequest request = new SearchRequest(
                evaluationCase.question(),
                embeddingModel.embed(evaluationCase.question()),
                topK,
                evaluationCase.filters());
        List<SearchResult> retrieved = connector.search(request);
        RetrievalMetrics retrievalMetrics = metrics(evaluationCase, retrieved, topK);
        AiAnalysis aiAnalysis = analyze(evaluationCase, retrieved);
        EvaluationStatus status = status(retrievalMetrics);
        if (properties.getEvaluator().isStrictGroundTruth()
                && evaluationCase.labelStatus() != LabelStatus.HUMAN_VERIFIED
                && evaluationCase.labelStatus() != LabelStatus.IMPORTED) {
            aiAnalysis = new AiAnalysis(
                    aiAnalysis.score(),
                    aiAnalysis.intentCoverage(),
                    aiAnalysis.noiseRatio(),
                    true,
                    aiAnalysis.judgeDisagreement(),
                    aiAnalysis.summary() + " 当前 Ground Truth 由模型提出，建议人工确认后用于硬指标验收。",
                    aiAnalysis.suggestion(),
                    aiAnalysis.judgeVerdicts());
        }
        return new CaseResult(evaluationCase, databaseName, retrievalMetrics, aiAnalysis, retrieved, status, Instant.now());
    }

    private RetrievalMetrics metrics(EvaluationCase evaluationCase, List<SearchResult> retrieved, int topK) {
        Set<String> expectedSet = new LinkedHashSet<>(evaluationCase.expectedIds());
        List<String> retrievedIds = retrieved.stream().map(SearchResult::id).toList();
        List<String> hitIds = new ArrayList<>();
        List<String> missIds = new ArrayList<>();
        for (String expected : evaluationCase.expectedIds()) {
            if (retrievedIds.contains(expected)) {
                hitIds.add(expected);
            } else {
                missIds.add(expected);
            }
        }
        double recall = expectedSet.isEmpty() ? 0 : (double) hitIds.size() / expectedSet.size();
        double hitRate = expectedSet.isEmpty() ? 0 : hitIds.size() > 0 ? 1.0 : 0.0;
        double precisionAtK = retrievedIds.isEmpty() ? 0 : (double) hitIds.size() / topK;
        // MRR: first rank position of any expected ID in retrieved list
        double mrr = 0;
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                mrr = 1.0 / (i + 1);
                break;
            }
        }
        // nDCG: normalized discounted cumulative gain
        double dcg = 0;
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (expectedSet.contains(retrievedIds.get(i))) {
                dcg += 1.0 / log2(i + 2); // log2(rank+1), rank is 1-based
            }
        }
        double idealDcg = 0;
        int idealCount = Math.min(expectedSet.size(), topK);
        for (int i = 0; i < idealCount; i++) {
            idealDcg += 1.0 / log2(i + 2);
        }
        double ndcg = idealDcg == 0 ? 0 : dcg / idealDcg;
        return new RetrievalMetrics(hitIds, missIds, recall, hitRate, precisionAtK, mrr, ndcg, topK);
    }

    private AiAnalysis analyze(EvaluationCase evaluationCase, List<SearchResult> retrieved) {
        List<JudgeVerdict> verdicts = new ArrayList<>();
        JudgeModel primary = judgeRegistry.get(properties.getEvaluator().getPrimaryJudge());
        if (primary != null) {
            verdicts.add(primary.judge(evaluationCase, retrieved));
        }
        JudgeModel secondary = judgeRegistry.get(properties.getEvaluator().getSecondaryJudge());
        if (secondary != null) {
            verdicts.add(secondary.judge(evaluationCase, retrieved));
        }
        if (verdicts.isEmpty()) {
            throw new IllegalStateException("No judge configured");
        }
        boolean disagreement = hasDisagreement(verdicts);
        double averageScore = verdicts.stream().mapToInt(JudgeVerdict::score).average().orElse(0);
        double averageNoise = verdicts.stream().mapToDouble(JudgeVerdict::noiseRatio).average().orElse(0);
        Set<String> covered = new LinkedHashSet<>();
        Set<String> missing = new LinkedHashSet<>();
        for (JudgeVerdict verdict : verdicts) {
            covered.addAll(verdict.coveredIntents());
            missing.addAll(verdict.missingIntents());
        }
        missing.removeAll(covered);
        double coverage = evaluationCase.intents().isEmpty()
                ? 1.0
                : (double) covered.size() / evaluationCase.intents().size();
        String summary = missing.isEmpty()
                ? "召回内容覆盖全部子意图。"
                : "缺失子意图：" + String.join("、", missing) + "。";
        String suggestion = verdicts.getFirst().suggestion();
        boolean needsReview = disagreement || evaluationCase.labelStatus() == LabelStatus.MODEL_PROPOSED
                || evaluationCase.labelStatus() == LabelStatus.NEEDS_REVIEW;
        return new AiAnalysis(
                (int) Math.round(averageScore),
                coverage,
                averageNoise,
                needsReview,
                disagreement,
                summary,
                suggestion,
                verdicts);
    }

    private boolean hasDisagreement(List<JudgeVerdict> verdicts) {
        if (verdicts.size() < 2) {
            return false;
        }
        int threshold = properties.getEvaluator().getDisagreementThreshold();
        for (int i = 0; i < verdicts.size(); i++) {
            for (int j = i + 1; j < verdicts.size(); j++) {
                if (Math.abs(verdicts.get(i).score() - verdicts.get(j).score()) > threshold) {
                    return true;
                }
                if (!new LinkedHashSet<>(verdicts.get(i).missingIntents())
                        .equals(new LinkedHashSet<>(verdicts.get(j).missingIntents()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private double log2(double x) {
        return Math.log(x) / Math.log(2);
    }

    private EvaluationStatus status(RetrievalMetrics metrics) {
        if (metrics.recallRate() >= 1.0) {
            return EvaluationStatus.FULL_RECALL;
        }
        if (metrics.recallRate() > 0) {
            return EvaluationStatus.PARTIAL_RECALL;
        }
        return EvaluationStatus.MISS;
    }
}
