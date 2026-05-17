package com.recallmaster.universal.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EvaluationRun {

    private final String id;
    private final String database;
    private final int topK;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private volatile RunStatus status;
    private volatile String error;
    private volatile int total;
    private final List<CaseResult> results = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    public EvaluationRun(String id, String database, int topK, int total) {
        this.id = id;
        this.database = database;
        this.topK = topK;
        this.total = total;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = RunStatus.QUEUED;
    }

    public synchronized void markRunning() {
        this.status = RunStatus.RUNNING;
        touch();
    }

    public synchronized void markCompleted() {
        this.status = RunStatus.COMPLETED;
        touch();
    }

    public synchronized void markFailed(String error) {
        this.status = RunStatus.FAILED;
        this.error = error;
        touch();
    }

    public synchronized void addResult(CaseResult result) {
        this.results.add(result);
        this.events.add("case:" + result.caseInfo().id() + ":" + result.status());
        touch();
    }

    public synchronized void addEvent(String event) {
        this.events.add(event);
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDatabase() {
        return database;
    }

    public int getTopK() {
        return topK;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public int getTotal() {
        return total;
    }

    public int getCompleted() {
        return results.size();
    }

    public synchronized List<CaseResult> getResults() {
        return List.copyOf(results);
    }

    public synchronized List<String> getEvents() {
        return List.copyOf(events);
    }
}
