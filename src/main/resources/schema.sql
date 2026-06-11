CREATE TABLE IF NOT EXISTS evaluation_run (
    id VARCHAR(64) PRIMARY KEY,
    database_name VARCHAR(100) NOT NULL,
    top_k INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total INT NOT NULL DEFAULT 0,
    completed INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS case_result (
    id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES evaluation_run(id) ON DELETE CASCADE,
    case_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    recall_rate DOUBLE NOT NULL DEFAULT 0,
    intent_coverage DOUBLE NOT NULL DEFAULT 0,
    noise_ratio DOUBLE NOT NULL DEFAULT 0,
    needs_human_review BOOLEAN NOT NULL DEFAULT FALSE,
    summary TEXT,
    intents TEXT,
    expected_ids TEXT,
    actual_ids TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_case_result_run_id ON case_result(run_id);
