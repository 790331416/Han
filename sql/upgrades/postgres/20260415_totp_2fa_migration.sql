-- TOTP 2FA fields for PostgreSQL.
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64) DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS totp_enabled SMALLINT DEFAULT 0;

COMMENT ON COLUMN sys_user.totp_secret IS 'TOTP secret';
COMMENT ON COLUMN sys_user.totp_enabled IS '2FA enabled flag, 0 disabled, 1 enabled';
