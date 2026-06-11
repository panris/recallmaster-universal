package com.recallmaster.universal.web;

import com.recallmaster.universal.casegen.CaseImportService;
import com.recallmaster.universal.model.CaseResult;
import com.recallmaster.universal.model.EvaluationCase;
import com.recallmaster.universal.model.EvaluationRun;
import com.recallmaster.universal.report.ReportService;
import com.recallmaster.universal.report.RunComparison;
import com.recallmaster.universal.report.RunSummary;
import com.recallmaster.universal.task.EvaluationRunRequest;
import com.recallmaster.universal.task.EvaluationRunService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/runs")
public class ApiController {

    private final EvaluationRunService evaluationRunService;
    private final ReportService reportService;
    private final CaseImportService caseImportService;

    public ApiController(EvaluationRunService evaluationRunService,
                         ReportService reportService,
                         CaseImportService caseImportService) {
        this.evaluationRunService = evaluationRunService;
        this.reportService = reportService;
        this.caseImportService = caseImportService;
    }

    @PostMapping
    public EvaluationRun startRun(@jakarta.validation.Valid @RequestBody EvaluationRunRequest request) {
        return evaluationRunService.start(request);
    }

    @GetMapping
    public List<EvaluationRun> listRuns() {
        return evaluationRunService.list();
    }

    @GetMapping("/{id}")
    public EvaluationRun getRun(@PathVariable String id) {
        return evaluationRunService.get(id);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String id) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.onTimeout(() -> { completed.set(true); emitter.complete(); });
        emitter.onError(ex -> { completed.set(true); emitter.complete(); });
        Thread.startVirtualThread(() -> {
            int sent = 0;
            try {
                while (!completed.get()) {
                    EvaluationRun run = evaluationRunService.get(id);
                    if (run == null) {
                        emitter.send(SseEmitter.event().name("status").data("NOT_FOUND"));
                        emitter.complete();
                        break;
                    }
                    List<String> snapshot = List.copyOf(run.getEvents());
                    for (int i = sent; i < snapshot.size(); i++) {
                        if (completed.get()) break;
                        emitter.send(SseEmitter.event().name("progress").data(snapshot.get(i)));
                    }
                    sent = snapshot.size();
                    if (completed.get()) break;
                    emitter.send(SseEmitter.event().name("status").data(run.getStatus().name()));
                    String status = run.getStatus().name();
                    if (status.equals("COMPLETED") || status.equals("COMPLETED_WITH_ERRORS") || status.equals("FAILED")) {
                        emitter.complete();
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (IOException ex) {
                if (!completed.get()) emitter.completeWithError(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (!completed.get()) emitter.completeWithError(ex);
            } catch (IllegalStateException ex) {
                if (!completed.get()) emitter.complete();
            }
        });
        return emitter;
    }

    @GetMapping("/{id}/results")
    public List<CaseResult> getResults(@PathVariable String id) {
        return evaluationRunService.get(id).getResults();
    }

    @GetMapping("/{id}/summary")
    public RunSummary getSummary(@PathVariable String id) {
        return reportService.summary(id);
    }

    @GetMapping("/{baselineId}/compare/{candidateId}")
    public RunComparison compareRuns(@PathVariable String baselineId, @PathVariable String candidateId) {
        return reportService.compare(baselineId, candidateId);
    }

    @GetMapping(value = "/{id}/report.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("recallmaster-run-" + id + ".csv")
                        .build()
                        .toString())
                .body(reportService.exportCsv(id));
    }

    @GetMapping(value = "/{id}/report.md", produces = "text/markdown")
    public ResponseEntity<String> exportMarkdown(@PathVariable String id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("recallmaster-run-" + id + ".md")
                        .build()
                        .toString())
                .body(reportService.exportMarkdown(id));
    }

    @GetMapping(value = "/{id}/cases.json", produces = MediaType.APPLICATION_JSON_VALUE)
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
}