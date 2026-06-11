package com.recallmaster.universal.repository;

import com.recallmaster.universal.model.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RunRepository {

    private final JdbcClient jdbc;

    public RunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void saveRun(String id, String database, int topK, RunStatus status,
                        int total, int completed, int errorCount,
                        Instant createdAt, Instant finishedAt) {
        jdbc.sql("""
                MERGE INTO evaluation_run (id, database_name, top_k, status, total, completed, error_count, created_at, finished_at)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(id, database, topK, status.name(), total, completed, errorCount, createdAt, finishedAt)
                .update();
    }

    public Optional<RunRow> findRun(String id) {
        return jdbc.sql("""
                SELECT id, database_name, top_k, status, total, completed, error_count, created_at, finished_at
                FROM evaluation_run WHERE id = ?
                """).param(id).query(RunRow.class).optional();
    }

    public List<RunRow> listRuns(int limit) {
        return jdbc.sql("""
                SELECT id, database_name, top_k, status, total, completed, error_count, created_at, finished_at
                FROM evaluation_run ORDER BY created_at DESC LIMIT ?
                """).param(limit).query(RunRow.class).list();
    }

    public void saveCaseResult(String id, String runId, String caseId, String question,
                               String status, double recallRate, double intentCoverage,
                               double noiseRatio, boolean needsHumanReview, String summary,
                               String intents, String expectedIds, String actualIds,
                               String errorMessage, Instant createdAt) {
        jdbc.sql("""
                MERGE INTO case_result (id, run_id, case_id, question, status, recall_rate, intent_coverage,
                    noise_ratio, needs_human_review, summary, intents, expected_ids, actual_ids, error_message, created_at)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(id, runId, caseId, question, status, recallRate, intentCoverage,
                        noiseRatio, needsHumanReview, summary, intents, expectedIds,
                        actualIds, errorMessage, createdAt)
                .update();
    }

    public List<CaseResultRow> findCaseResultsByRunId(String runId) {
        return jdbc.sql("""
                SELECT id, run_id, case_id, question, status, recall_rate, intent_coverage,
                    noise_ratio, needs_human_review, summary, intents, expected_ids, actual_ids, error_message, created_at
                FROM case_result WHERE run_id = ? ORDER BY created_at
                """).param(runId).query(CaseResultRow.class).list();
    }

    public record RunRow(String id, String databaseName, int topK, String status,
                         int total, int completed, int errorCount,
                         Instant createdAt, Instant finishedAt) {
    }

    public record CaseResultRow(String id, String runId, String caseId, String question,
                                String status, double recallRate, double intentCoverage,
                                double noiseRatio, boolean needsHumanReview, String summary,
                                String intents, String expectedIds, String actualIds,
                                String errorMessage, Instant createdAt) {
    }
}
