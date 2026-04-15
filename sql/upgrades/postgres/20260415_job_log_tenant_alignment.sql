DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_job_log'
          AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE sys_job_log ADD COLUMN tenant_id BIGINT;
    END IF;

    UPDATE sys_job_log log
    SET tenant_id = COALESCE(log.tenant_id, job.tenant_id, 1)
    FROM sys_job job
    WHERE job.job_name = log.job_name
      AND job.job_group = log.job_group
      AND log.tenant_id IS NULL;

    UPDATE sys_job_log SET tenant_id = COALESCE(tenant_id, 1);
END $$;
