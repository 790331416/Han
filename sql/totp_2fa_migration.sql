-- 2FA/TOTP 两步验证字段迁移
-- 为用户表添加 TOTP 密钥和启用标记

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64) DEFAULT NULL COMMENT 'TOTP密钥（2FA绑定后存储）';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS totp_enabled SMALLINT DEFAULT 0 COMMENT '是否启用2FA（0=未启用 1=已启用）';
