package com.recallmaster.universal.web;

import com.recallmaster.universal.casegen.CaseGenerationRequest;
import com.recallmaster.universal.casegen.CaseGeneratorService;
import com.recallmaster.universal.casegen.CaseImportService;
import com.recallmaster.universal.casegen.GeneratedCaseSet;
import com.recallmaster.universal.casegen.ImportedCaseSet;
import com.recallmaster.universal.connector.ConnectorDescriptor;
import com.recallmaster.universal.connector.ConnectorHealth;
import com.recallmaster.universal.connector.ConnectorHealthService;
import com.recallmaster.universal.connector.ConnectorRegistry;
import com.recallmaster.universal.connector.VectorStoreConnector;
import com.recallmaster.universal.embedding.EmbeddingModel;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.report.ReportService;
import com.recallmaster.universal.report.RunComparison;
import com.recallmaster.universal.report.RunSummary;
import com.recallmaster.universal.task.EvaluationRunRequest;
import com.recallmaster.universal.task.EvaluationRunService;
import com.recallmaster.universal.model.DocumentChunk;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CaseGeneratorService caseGeneratorService;
    private final CaseImportService caseImportService;
    private final EvaluationRunService evaluationRunService;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorHealthService connectorHealthService;
    private final ReportService reportService;
    private final EmbeddingModel embeddingModel;

    public ApiController(
            CaseGeneratorService caseGeneratorService,
            CaseImportService caseImportService,
            EvaluationRunService evaluationRunService,
            ConnectorRegistry connectorRegistry,
            ConnectorHealthService connectorHealthService,
            ReportService reportService,
            EmbeddingModel embeddingModel
    ) {
        this.caseGeneratorService = caseGeneratorService;
        this.caseImportService = caseImportService;
        this.evaluationRunService = evaluationRunService;
        this.connectorRegistry = connectorRegistry;
        this.connectorHealthService = connectorHealthService;
        this.reportService = reportService;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/connectors")
    public List<ConnectorDescriptor> listConnectors() {
        return connectorRegistry.describeAll();
    }

    @GetMapping("/connectors/{name}")
    public ConnectorDescriptor getConnector(@PathVariable String name) {
        return connectorRegistry.describe(name);
    }

    @PostMapping("/connectors/health")
    public List<ConnectorHealth> checkAllConnectors() {
        return connectorHealthService.checkAll();
    }

    @PostMapping("/connectors/{name}/health")
    public ConnectorHealth checkConnector(@PathVariable String name) {
        return connectorHealthService.check(name);
    }

    @PostMapping("/cases/generate")
    public GeneratedCaseSet generateCases(@jakarta.validation.Valid @RequestBody CaseGenerationRequest request) {
        return caseGeneratorService.generate(request);
    }

    @PostMapping(value = "/cases/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ImportedCaseSet importCases(@RequestBody String json) {
        return caseImportService.importJson(json);
    }

    @PostMapping("/runs")
    public EvaluationRun startRun(@jakarta.validation.Valid @RequestBody EvaluationRunRequest request) {
        return evaluationRunService.start(request);
    }

    @GetMapping("/runs")
    public List<EvaluationRun> listRuns() {
        return evaluationRunService.list();
    }

    @GetMapping("/runs/{id}")
    public EvaluationRun getRun(@PathVariable String id) {
        return evaluationRunService.get(id);
    }

    @GetMapping(value = "/runs/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L);
        Thread.startVirtualThread(() -> {
            int sent = 0;
            try {
                while (true) {
                    EvaluationRun run = evaluationRunService.get(id);
                    List<String> events = run.getEvents();
                    for (int i = sent; i < events.size(); i++) {
                        emitter.send(SseEmitter.event().name("progress").data(events.get(i)));
                    }
                    sent = events.size();
                    emitter.send(SseEmitter.event().name("status").data(run.getStatus().name()));
                    if (run.getStatus().name().equals("COMPLETED") || run.getStatus().name().equals("COMPLETED_WITH_ERRORS") || run.getStatus().name().equals("FAILED")) {
                        emitter.complete();
                        break;
                    }
                    Thread.sleep(1000);
                }
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @GetMapping("/runs/{id}/results")
    public List<CaseResult> getResults(@PathVariable String id) {
        return evaluationRunService.get(id).getResults();
    }

    @GetMapping("/runs/{id}/summary")
    public RunSummary getSummary(@PathVariable String id) {
        return reportService.summary(id);
    }

    @GetMapping("/runs/{baselineId}/compare/{candidateId}")
    public RunComparison compareRuns(@PathVariable String baselineId, @PathVariable String candidateId) {
        return reportService.compare(baselineId, candidateId);
    }

    @GetMapping(value = "/runs/{id}/report.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("recallmaster-run-" + id + ".csv")
                        .build()
                        .toString())
                .body(reportService.exportCsv(id));
    }

    @GetMapping(value = "/runs/{id}/cases.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> exportCases(@PathVariable String id) {
        List<EvaluationCase> cases = evaluationRunService.get(id).getResults().stream()
                .map(CaseResult::caseInfo)
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("recallmaster-cases-" + id + ".json")
                        .build()
                        .toString())
                .body(caseImportService.exportJson(cases));
    }

    @PostMapping("/connectors/{name}/upsert")
    public void upsert(@PathVariable String name, @RequestBody UpsertRequest request) {
        VectorStoreConnector connector = connectorRegistry.get(name);
        if (connector == null) {
            throw new IllegalArgumentException("Unknown connector: " + name);
        }
        List<DocumentChunk> chunks = request.documents().stream()
                .map(doc -> {
                    String id = doc.id() != null ? doc.id() : UUID.randomUUID().toString();
                    float[] vector = embeddingModel.embed(doc.text());
                    return new DocumentChunk(id, doc.text(), vector, doc.metadata());
                })
                .toList();
        connector.upsert(chunks);
    }
}
