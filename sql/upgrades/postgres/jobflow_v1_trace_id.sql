-- JobFlow V1 trace id field for PostgreSQL.
ALTER TABLE sys_job_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64) DEFAULT NULL;

COMMENT ON COLUMN sys_job_log.trace_id IS 'trace id';

CREATE INDEX IF NOT EXISTS idx_sys_job_log_trace_id ON sys_job_log(trace_id);
