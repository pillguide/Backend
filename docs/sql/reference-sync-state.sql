-- Apply after reference-scan-state.sql. No data refresh starts merely by creating this table.
BEGIN;
CREATE TABLE IF NOT EXISTS reference_sync_state (
    id bigint PRIMARY KEY CHECK (id = 1),
    current_version varchar(36),
    source_as_of date,
    last_success_at timestamp,
    last_attempt_at timestamp,
    last_failure_code varchar(80),
    active_run uuid,
    lease_until timestamp,
    budget_date date,
    requests_used integer NOT NULL DEFAULT 0 CHECK (requests_used >= 0),
    run_count bigint NOT NULL DEFAULT 0 CHECK (run_count >= 0)
);
INSERT INTO reference_sync_state (id, requests_used, run_count)
VALUES (1, 0, 0) ON CONFLICT (id) DO NOTHING;
COMMIT;
