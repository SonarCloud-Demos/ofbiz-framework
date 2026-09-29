CREATE TABLE catalog_snapshot_seen (
    run_id VARCHAR(64) NOT NULL,
    record_type VARCHAR(16) NOT NULL,
    record_id VARCHAR(192) NOT NULL,
    PRIMARY KEY (run_id, record_type, record_id)
);

CREATE TABLE catalog_reconciliation_run (
    run_id VARCHAR(64) PRIMARY KEY,
    source_name VARCHAR(64) NOT NULL,
    source_count BIGINT NOT NULL,
    deleted_count BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
