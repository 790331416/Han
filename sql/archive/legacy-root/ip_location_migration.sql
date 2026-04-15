-- IP 归属地字段迁移
-- 为操作日志表和登录日志表添加归属地字段

-- 操作日志表
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS oper_location VARCHAR(255) DEFAULT '' COMMENT '操作归属地';

-- 登录日志表（确认 login_location 列存在）
-- ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS login_location VARCHAR(255) DEFAULT '' COMMENT '登录归属地';
