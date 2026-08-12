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

    -- 必须带 WHERE：sys_job_log 随运行时间线性增长，无条件 UPDATE 每次重放都会
    -- 全表 rewrite，在 95 这种长期运行的库上会造成长时间行锁与表膨胀。
    UPDATE sys_job_log SET tenant_id = 1 WHERE tenant_id IS NULL;
END $$;
