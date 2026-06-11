package com.recallmaster.universal.task;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.evaluation.EvaluationService;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationStatus;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.model.RunStatus;
import com.recallmaster.universal.repository.RunRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class EvaluationRunService implements EvaluationRunLookup {

    private final EvaluationService evaluationService;
    private final ConnectorRegistry connectorRegistry;
    private final RecallMasterProperties properties;
    private final RunRepository runRepository;
    private final ExecutorService executorService;
    private final Map<String, EvaluationRun> runs = new ConcurrentHashMap<>();

    public EvaluationRunService(
            EvaluationService evaluationService,
            ConnectorRegistry connectorRegistry,
            RecallMasterProperties properties,
            RunRepository runRepository
    ) {
        this.evaluationService = evaluationService;
        this.connectorRegistry = connectorRegistry;
        this.properties = properties;
        this.runRepository = runRepository;
        this.executorService = Executors.newFixedThreadPool(properties.getEvaluator().getConcurrency());
    }

    @PostConstruct
    public void loadHistory() {
        for (var row : runRepository.listRuns(200)) {
            if (row.status().equals("RUNNING") || row.status().equals("PENDING")) {
                continue; // Skip incomplete runs from previous session
            }
            EvaluationRun run = new EvaluationRun(row.id(), row.databaseName(), row.topK(), row.total());
            run.setStatus(RunStatus.valueOf(row.status()));
            if (row.finishedAt() != null) {
                run.setFinishedAt(row.finishedAt());
            }
            for (var cr : runRepository.findCaseResultsByRunId(row.id())) {
                CaseResult result = reconstructCaseResult(cr);
                run.addResult(result);
            }
            runs.putIfAbsent(run.getId(), run);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    public EvaluationRun start(EvaluationRunRequest request) {
        String database = request.database() == null || request.database().isBlank()
                ? connectorRegistry.defaultName()
                : request.database();
        int topK = request.topK() < 1 ? properties.getDefaultTopK() : request.topK();
        List<EvaluationCase> cases = request.cases();
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        EvaluationRun run = new EvaluationRun(UUID.randomUUID().toString(), database, topK, cases.size());
        runs.put(run.getId(), run);
        persistRun(run);
        Thread.startVirtualThread(() -> execute(run, cases));
        return run;
    }

    public EvaluationRun get(String id) {
        EvaluationRun run = runs.get(id);
        if (run == null) {
            throw new IllegalArgumentException("Unknown evaluation run: " + id);
        }
        return run;
    }

    public List<EvaluationRun> list() {
        return runs.values().stream()
                .sorted(Comparator.comparing(EvaluationRun::getCreatedAt).reversed())
                .toList();
    }

    private void execute(EvaluationRun run, List<EvaluationCase> cases) {
        run.markRunning();
        run.addEvent("run:started");
        List<CompletableFuture<Void>> futures = cases.stream()
                .map(evaluationCase -> CompletableFuture
                        .supplyAsync(() -> safeEvaluateOne(run, evaluationCase), executorService)
                        .thenAccept(run::addResult))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ex) {
            // Should not happen since safeEvaluateOne catches exceptions,
            // but handle defensively
            run.addEvent("run:unexpected-error:" + ex.getMessage());
        }

        long errorCount = run.getResults().stream()
                .filter(r -> r.status() == EvaluationStatus.ERROR)
                .count();
        if (errorCount == 0) {
            run.addEvent("run:completed");
            run.markCompleted();
        } else if (errorCount < run.getTotal()) {
            run.addEvent("run:completed-with-errors:" + errorCount);
            run.markCompletedWithErrors();
        } else {
            run.addEvent("run:failed:all-cases-errored");
            run.markFailed("All " + run.getTotal() + " cases failed");
        }
        persistRun(run);
        for (CaseResult cr : run.getResults()) {
            persistCaseResult(run.getId(), cr);
        }
    }

    private CaseResult reconstructCaseResult(RunRepository.CaseResultRow cr) {
        EvaluationCase evalCase = new EvaluationCase(
                cr.caseId(), cr.question(),
                cr.intents() != null && !cr.intents().isBlank() ? List.of(cr.intents().split(",")) : List.of(),
                cr.expectedIds() != null && !cr.expectedIds().isBlank() ? List.of(cr.expectedIds().split(",")) : List.of(),
                Map.of(), null);
        var rm = new com.recallmaster.universal.model.RetrievalMetrics(
                List.of(), List.of(), cr.recallRate(), 0);
        var ai = new com.recallmaster.universal.model.AiAnalysis(
                0, cr.intentCoverage(), cr.noiseRatio(), cr.needsHumanReview(), false,
                cr.summary() != null ? cr.summary() : "", "", List.of());
        return new CaseResult(evalCase, "", rm, ai,
                List.of(),
                EvaluationStatus.valueOf(cr.status()), cr.createdAt());
    }

    private void persistRun(EvaluationRun run) {
        try {
            runRepository.saveRun(run.getId(), run.getDatabase(), run.getTopK(),
                    run.getStatus(), run.getTotal(), run.getCompleted(), run.getErrorCount(),
                    run.getCreatedAt(), run.getFinishedAt());
        } catch (Exception e) {
            // Persistence failure should not break evaluation
            run.addEvent("persist:error:" + e.getMessage());
        }
    }

    private void persistCaseResult(String runId, CaseResult cr) {
        try {
            String intents = cr.caseInfo() != null && cr.caseInfo().intents() != null
                    ? String.join(",", cr.caseInfo().intents()) : null;
            String expectedIds = cr.caseInfo() != null && cr.caseInfo().expectedIds() != null
                    ? String.join(",", cr.caseInfo().expectedIds()) : null;
            String actualIds = cr.retrieved() != null
                    ? cr.retrieved().stream().map(com.recallmaster.universal.model.SearchResult::id).collect(java.util.stream.Collectors.joining(",")) : null;
            String summary = cr.aiAnalysis() != null ? cr.aiAnalysis().summary() : null;
            String errorMessage = cr.status() == EvaluationStatus.ERROR ? "Evaluation error" : null;
            runRepository.saveCaseResult(UUID.randomUUID().toString(), runId,
                    cr.caseInfo() != null ? cr.caseInfo().id() : "",
                    cr.caseInfo() != null ? cr.caseInfo().question() : "",
                    cr.status().name(),
                    cr.retrievalMetrics() != null ? cr.retrievalMetrics().recallRate() : 0,
                    cr.aiAnalysis() != null ? cr.aiAnalysis().intentCoverage() : 0,
                    cr.aiAnalysis() != null ? cr.aiAnalysis().noiseRatio() : 0,
                    cr.aiAnalysis() != null && cr.aiAnalysis().needsHumanReview(),
                    summary, intents, expectedIds, actualIds, errorMessage, cr.finishedAt());
        } catch (Exception e) {
            // Persistence failure should not break evaluation
        }
    }

    private CaseResult safeEvaluateOne(EvaluationRun run, EvaluationCase evaluationCase) {
        try {
            run.addEvent("case:started:" + evaluationCase.id());
            return evaluationService.evaluate(run.getDatabase(), evaluationCase, run.getTopK());
        } catch (Exception ex) {
            run.addEvent("case:error:" + evaluationCase.id() + ":" + ex.getMessage());
            return new CaseResult(
                    evaluationCase,
                    run.getDatabase(),
                    null,
                    null,
                    List.of(),
                    EvaluationStatus.ERROR,
                    Instant.now()
            );
        }
    }
}
