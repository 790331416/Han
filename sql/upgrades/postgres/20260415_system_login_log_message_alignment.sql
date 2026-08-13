DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'msg'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'message'
    ) THEN
        ALTER TABLE sys_login_log RENAME COLUMN msg TO message;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'message'
    ) THEN
        ALTER TABLE sys_login_log ADD COLUMN message VARCHAR(255) DEFAULT '';
    END IF;

    -- 必须带 WHERE：sys_login_log 随运行时间线性增长，无条件 UPDATE 每次重放都会
    -- 全表 rewrite，在 95 这种长期运行的库上会造成长时间行锁与表膨胀。
    UPDATE sys_login_log SET message = '' WHERE message IS NULL;
    ALTER TABLE sys_login_log ALTER COLUMN message SET DEFAULT '';
END $$;
