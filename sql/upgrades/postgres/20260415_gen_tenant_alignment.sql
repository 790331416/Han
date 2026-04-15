DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'gen_table'
          AND column_name = 'tenant_id'
    ) THEN
        EXECUTE 'ALTER TABLE gen_table ADD COLUMN tenant_id BIGINT DEFAULT 0';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'gen_table_column'
          AND column_name = 'tenant_id'
    ) THEN
        EXECUTE 'ALTER TABLE gen_table_column ADD COLUMN tenant_id BIGINT DEFAULT 0';
    END IF;
END $$;

UPDATE gen_table
SET tenant_id = COALESCE(tenant_id, 0)
WHERE tenant_id IS NULL;

UPDATE gen_table_column
SET tenant_id = COALESCE(tenant_id, 0)
WHERE tenant_id IS NULL;
