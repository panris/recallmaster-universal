package com.recallmaster.universal.report;

import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.model.EvaluationStatus;
import com.recallmaster.universal.model.JudgeVerdict;
import com.recallmaster.universal.task.EvaluationRunLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final EvaluationRunLookup evaluationRunLookup;

    public ReportService(EvaluationRunLookup evaluationRunLookup) {
        this.evaluationRunLookup = evaluationRunLookup;
    }

    public RunSummary summary(String runId) {
        EvaluationRun run = evaluationRunLookup.get(runId);
        List<CaseResult> results = run.getResults();
        double avgRecall = results.stream().mapToDouble(result -> result.retrievalMetrics().recallRate()).average().orElse(0);
        double avgCoverage = results.stream().mapToDouble(result -> result.aiAnalysis().intentCoverage()).average().orElse(0);
        double avgNoise = results.stream().mapToDouble(result -> result.aiAnalysis().noiseRatio()).average().orElse(0);
        Map<String, Integer> missingIntentCounts = new LinkedHashMap<>();
        Set<String> suggestions = new LinkedHashSet<>();
        for (CaseResult result : results) {
            result.aiAnalysis().judgeVerdicts().stream()
                    .map(JudgeVerdict::missingIntents)
                    .flatMap(List::stream)
                    .forEach(intent -> missingIntentCounts.merge(intent, 1, Integer::sum));
            if (!result.aiAnalysis().suggestion().isBlank()) {
                suggestions.add(result.aiAnalysis().suggestion());
            }
        }
        return new RunSummary(
                run.getId(),
                run.getDatabase(),
                run.getStatus().name(),
                run.getTotal(),
                run.getCompleted(),
                round(avgRecall),
                round(avgCoverage),
                round(avgNoise),
                count(results, EvaluationStatus.FULL_RECALL),
                count(results, EvaluationStatus.PARTIAL_RECALL),
                count(results, EvaluationStatus.MISS),
                (int) results.stream().filter(result -> result.aiAnalysis().needsHumanReview()).count(),
                missingIntentCounts,
                suggestions.stream().limit(5).toList());
    }

    public RunComparison compare(String baselineRunId, String candidateRunId) {
        EvaluationRun baseline = evaluationRunLookup.get(baselineRunId);
        EvaluationRun candidate = evaluationRunLookup.get(candidateRunId);
        Map<String, CaseResult> baselineCases = byStableCaseKey(baseline.getResults());
        Map<String, CaseResult> candidateCases = byStableCaseKey(candidate.getResults());
        Set<String> keys = new LinkedHashSet<>(baselineCases.keySet());
        keys.retainAll(candidateCases.keySet());
        List<CaseComparison> comparisons = new ArrayList<>();
        int regressions = 0;
        int improvements = 0;
        double deltaSum = 0;
        for (String key : keys) {
            CaseResult left = baselineCases.get(key);
            CaseResult right = candidateCases.get(key);
            double leftRecall = left.retrievalMetrics().recallRate();
            double rightRecall = right.retrievalMetrics().recallRate();
            double delta = rightRecall - leftRecall;
            deltaSum += delta;
            if (delta < 0) {
                regressions++;
            } else if (delta > 0) {
                improvements++;
            }
            comparisons.add(new CaseComparison(
                    key,
                    right.caseInfo().question(),
                    left.status().name(),
                    right.status().name(),
                    round(leftRecall),
                    round(rightRecall),
                    round(delta),
                    newlyMissed(left, right),
                    recovered(left, right),
                    left.aiAnalysis().summary(),
                    right.aiAnalysis().summary()));
        }
        comparisons.sort(Comparator.comparingDouble(CaseComparison::recallDelta));
        List<String> onlyInBaseline = new ArrayList<>(baselineCases.keySet());
        onlyInBaseline.removeAll(candidateCases.keySet());
        List<String> onlyInCandidate = new ArrayList<>(candidateCases.keySet());
        onlyInCandidate.removeAll(baselineCases.keySet());
        return new RunComparison(
                baselineRunId,
                candidateRunId,
                keys.size(),
                regressions,
                improvements,
                keys.size() - regressions - improvements,
                round(keys.isEmpty() ? 0 : deltaSum / keys.size()),
                onlyInBaseline,
                onlyInCandidate,
                comparisons);
    }

    public String exportCsv(String runId) {
        EvaluationRun run = evaluationRunLookup.get(runId);
        StringBuilder csv = new StringBuilder();
        csv.append("case_id,question,status,recall_rate,hit_ids,miss_ids,intent_coverage,noise_ratio,needs_review,summary,suggestion\n");
        for (CaseResult result : run.getResults()) {
            csv.append(escape(result.caseInfo().id())).append(',')
                    .append(escape(result.caseInfo().question())).append(',')
                    .append(result.status()).append(',')
                    .append(result.retrievalMetrics().recallRate()).append(',')
                    .append(escape(String.join("|", result.retrievalMetrics().hitIds()))).append(',')
                    .append(escape(String.join("|", result.retrievalMetrics().missIds()))).append(',')
                    .append(result.aiAnalysis().intentCoverage()).append(',')
                    .append(result.aiAnalysis().noiseRatio()).append(',')
                    .append(result.aiAnalysis().needsHumanReview()).append(',')
                    .append(escape(result.aiAnalysis().summary())).append(',')
                    .append(escape(result.aiAnalysis().suggestion())).append('\n');
        }
        return csv.toString();
    }

    private int count(List<CaseResult> results, EvaluationStatus status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }

    private Map<String, CaseResult> byStableCaseKey(List<CaseResult> results) {
        Map<String, CaseResult> map = new LinkedHashMap<>();
        for (CaseResult result : results) {
            map.put(stableCaseKey(result), result);
        }
        return map;
    }

    private String stableCaseKey(CaseResult result) {
        String id = result.caseInfo().id();
        if (id != null && !id.isBlank()) {
            return id;
        }
        return result.caseInfo().question();
    }

    private List<String> newlyMissed(CaseResult baseline, CaseResult candidate) {
        Set<String> baselineMiss = new LinkedHashSet<>(baseline.retrievalMetrics().missIds());
        Set<String> candidateMiss = new LinkedHashSet<>(candidate.retrievalMetrics().missIds());
        candidateMiss.removeAll(baselineMiss);
        return List.copyOf(candidateMiss);
    }

    private List<String> recovered(CaseResult baseline, CaseResult candidate) {
        Set<String> baselineMiss = new LinkedHashSet<>(baseline.retrievalMetrics().missIds());
        baselineMiss.removeAll(candidate.retrievalMetrics().missIds());
        return List.copyOf(baselineMiss);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String escape(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.contains(",") || normalized.contains("\"") || normalized.contains("\n")) {
            return "\"" + normalized.replace("\"", "\"\"") + "\"";
        }
        return normalized;
    }
}
