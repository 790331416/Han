-- Add IP location fields for PostgreSQL.
ALTER TABLE sys_oper_log
    ADD COLUMN IF NOT EXISTS oper_location VARCHAR(255) DEFAULT '';

COMMENT ON COLUMN sys_oper_log.oper_location IS 'operation location';

-- sys_login_log already contains login_location in current tier init SQL.
