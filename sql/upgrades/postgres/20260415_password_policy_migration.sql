-- Password policy fields for PostgreSQL.
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_update_time TIMESTAMP DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_reset_flag SMALLINT DEFAULT 0;

COMMENT ON COLUMN sys_user.pwd_update_time IS 'password last update time';
COMMENT ON COLUMN sys_user.pwd_reset_flag IS 'password reset flag, 1 means change required';

UPDATE sys_user SET pwd_update_time = create_time WHERE pwd_update_time IS NULL;
