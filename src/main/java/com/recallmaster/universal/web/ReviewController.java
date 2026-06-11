package com.recallmaster.universal.web;

import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.model.LabelStatus;
import com.recallmaster.universal.task.EvaluationRunService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final EvaluationRunService evaluationRunService;

    public ReviewController(EvaluationRunService evaluationRunService) {
        this.evaluationRunService = evaluationRunService;
    }

    @GetMapping("/needs-review")
    public List<CaseResult> listNeedsReview() {
        return evaluationRunService.list().stream()
                .flatMap(run -> run.getResults().stream())
                .filter(cr -> cr.caseInfo().labelStatus() == LabelStatus.NEEDS_REVIEW)
                .toList();
    }

    @GetMapping("/needs-review/{runId}")
    public List<CaseResult> listNeedsReviewByRun(@PathVariable String runId) {
        EvaluationRun run = evaluationRunService.get(runId);
        if (run == null) throw new IllegalArgumentException("Run not found: " + runId);
        return run.getResults().stream()
                .filter(cr -> cr.caseInfo().labelStatus() == LabelStatus.NEEDS_REVIEW)
                .toList();
    }

    @PostMapping("/{runId}/{caseId}")
    public CaseResult reviewCase(@PathVariable String runId, @PathVariable String caseId,
                                  @RequestBody ReviewRequest request) {
        EvaluationRun run = evaluationRunService.get(runId);
        if (run == null) throw new IllegalArgumentException("Run not found: " + runId);
        CaseResult original = run.getResults().stream()
                .filter(cr -> cr.caseInfo().id().equals(caseId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
        EvaluationCase updated = new EvaluationCase(
                caseId,
                request.question() != null ? request.question() : original.caseInfo().question(),
                request.intents() != null ? request.intents() : original.caseInfo().intents(),
                request.expectedIds() != null ? request.expectedIds() : original.caseInfo().expectedIds(),
                request.filters() != null ? request.filters() : original.caseInfo().filters(),
                LabelStatus.HUMAN_VERIFIED
        );
        CaseResult updatedResult = new CaseResult(
                updated, run.getDatabase(), original.retrievalMetrics(),
                original.aiAnalysis(), original.retrieved(),
                original.status(), original.finishedAt()
        );
        run.getResults().removeIf(cr -> cr.caseInfo().id().equals(caseId));
        run.getResults().add(updatedResult);
        run.addEvent("case:reviewed:" + caseId);
        evaluationRunService.saveRun(run);
        return updatedResult;
    }

    public record ReviewRequest(
            String question,
            List<String> intents,
            List<String> expectedIds,
            java.util.Map<String, String> filters
    ) {}
}