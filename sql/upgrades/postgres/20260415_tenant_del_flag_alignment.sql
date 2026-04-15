DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant'
          AND column_name = 'deleted'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant'
          AND column_name = 'del_flag'
    ) THEN
        EXECUTE 'ALTER TABLE sys_tenant RENAME COLUMN deleted TO del_flag';
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant'
          AND column_name = 'del_flag'
    ) THEN
        EXECUTE 'ALTER TABLE sys_tenant ADD COLUMN del_flag SMALLINT DEFAULT 0';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant_package'
          AND column_name = 'deleted'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant_package'
          AND column_name = 'del_flag'
    ) THEN
        EXECUTE 'ALTER TABLE sys_tenant_package RENAME COLUMN deleted TO del_flag';
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_tenant_package'
          AND column_name = 'del_flag'
    ) THEN
        EXECUTE 'ALTER TABLE sys_tenant_package ADD COLUMN del_flag SMALLINT DEFAULT 0';
    END IF;
END $$;

UPDATE sys_tenant
SET del_flag = COALESCE(del_flag, 0)
WHERE del_flag IS NULL;

UPDATE sys_tenant_package
SET del_flag = COALESCE(del_flag, 0)
WHERE del_flag IS NULL;
