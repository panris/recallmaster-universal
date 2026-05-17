package com.recallmaster.universal.task;

import com.recallmaster.universal.config.RecallMasterProperties;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.evaluation.EvaluationService;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationRun;
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
    private final ExecutorService executorService;
    private final Map<String, EvaluationRun> runs = new ConcurrentHashMap<>();

    public EvaluationRunService(
            EvaluationService evaluationService,
            ConnectorRegistry connectorRegistry,
            RecallMasterProperties properties
    ) {
        this.evaluationService = evaluationService;
        this.connectorRegistry = connectorRegistry;
        this.properties = properties;
        this.executorService = Executors.newFixedThreadPool(properties.getEvaluator().getConcurrency());
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
        try {
            List<CompletableFuture<Void>> futures = cases.stream()
                    .map(evaluationCase -> CompletableFuture
                            .supplyAsync(() -> evaluateOne(run, evaluationCase), executorService)
                            .thenAccept(run::addResult))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            run.addEvent("run:completed");
            run.markCompleted();
        } catch (CompletionException ex) {
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            run.addEvent("run:failed:" + message);
            run.markFailed(message);
        } catch (RuntimeException ex) {
            run.addEvent("run:failed:" + ex.getMessage());
            run.markFailed(ex.getMessage());
        }
    }

    private CaseResult evaluateOne(EvaluationRun run, EvaluationCase evaluationCase) {
        run.addEvent("case:started:" + evaluationCase.id());
        return evaluationService.evaluate(run.getDatabase(), evaluationCase, run.getTopK());
    }
}
