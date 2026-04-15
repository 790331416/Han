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

    UPDATE sys_login_log SET message = COALESCE(message, '');
    ALTER TABLE sys_login_log ALTER COLUMN message SET DEFAULT '';
END $$;
