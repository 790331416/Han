-- Password policy fields for PostgreSQL.
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_update_time TIMESTAMP DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_reset_flag SMALLINT DEFAULT 0;

COMMENT ON COLUMN sys_user.pwd_update_time IS 'password last update time';
COMMENT ON COLUMN sys_user.pwd_reset_flag IS 'password reset flag, 1 means change required';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_user'
          AND column_name = 'create_time'
    ) THEN
        UPDATE sys_user
        SET pwd_update_time = create_time
        WHERE pwd_update_time IS NULL;
    ELSE
        UPDATE sys_user
        SET pwd_update_time = CURRENT_TIMESTAMP
        WHERE pwd_update_time IS NULL;
    END IF;
END $$;
