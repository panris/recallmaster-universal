package com.recallmaster.universal.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.recallmaster.universal.model.AiAnalysis;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.model.EvaluationStatus;
import com.recallmaster.universal.model.LabelStatus;
import com.recallmaster.universal.model.RetrievalMetrics;
import com.recallmaster.universal.model.RunStatus;
import com.recallmaster.universal.model.SearchResult;
import com.recallmaster.universal.task.EvaluationRunLookup;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    @Test
    void summarizesAndComparesRuns() {
        EvaluationRun baseline = run("baseline", result("case-1", 1.0, EvaluationStatus.FULL_RECALL, List.of(), List.of("doc_1")));
        EvaluationRun candidate = run("candidate", result("case-1", 0.0, EvaluationStatus.MISS, List.of("doc_1"), List.of()));
        StubRunLookup runService = new StubRunLookup(Map.of("baseline", baseline, "candidate", candidate));
        ReportService reportService = new ReportService(runService);

        RunSummary summary = reportService.summary("baseline");
        RunComparison comparison = reportService.compare("baseline", "candidate");
        String csv = reportService.exportCsv("candidate");

        assertThat(summary.averageRecall()).isEqualTo(1.0);
        assertThat(summary.fullRecall()).isEqualTo(1);
        assertThat(comparison.regressions()).isEqualTo(1);
        assertThat(comparison.cases().getFirst().newlyMissedIds()).containsExactly("doc_1");
        assertThat(csv).contains("case_id,question,status");
    }

    private EvaluationRun run(String id, CaseResult result) {
        EvaluationRun run = new EvaluationRun(id, "demo-memory", 5, 1);
        run.markRunning();
        run.addResult(result);
        run.markCompleted();
        return run;
    }

    private CaseResult result(
            String caseId,
            double recall,
            EvaluationStatus status,
            List<String> missIds,
            List<String> hitIds
    ) {
        EvaluationCase evaluationCase = new EvaluationCase(
                caseId,
                "问题",
                List.of("意图"),
                List.of("doc_1"),
                Map.of(),
                LabelStatus.HUMAN_VERIFIED);
        AiAnalysis analysis = new AiAnalysis(80, recall, 0.1, false, false, "summary", "suggestion", List.of());
        return new CaseResult(
                evaluationCase,
                "demo-memory",
                new RetrievalMetrics(hitIds, missIds, recall, 5),
                analysis,
                List.of(new SearchResult("doc_1", "text", 1.0, Map.of())),
                status,
                Instant.now());
    }

    private record StubRunLookup(Map<String, EvaluationRun> runs) implements EvaluationRunLookup {
        @Override
        public EvaluationRun get(String id) {
            return runs.get(id);
        }
    }
}
