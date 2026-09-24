CREATE TABLE catalog_snapshot_seen (
    run_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (run_id, product_id)
);

CREATE TABLE catalog_reconciliation_run (
    run_id VARCHAR(64) PRIMARY KEY,
    cutoff_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source_count BIGINT NOT NULL,
    deleted_count BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
