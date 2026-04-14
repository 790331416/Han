-- 登录日志表结构对齐脚本
-- 目标：
-- 1. 将旧列名 ipaddr / msg 对齐为代码侧使用的 ip_addr / message
-- 2. 补齐 client_type 字段，避免认证链路写日志时报错

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'ipaddr'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'ip_addr'
    ) THEN
        EXECUTE 'ALTER TABLE sys_login_log RENAME COLUMN ipaddr TO ip_addr';
    END IF;
END $$;

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
        EXECUTE 'ALTER TABLE sys_login_log RENAME COLUMN msg TO message';
    END IF;
END $$;

ALTER TABLE sys_login_log
    ADD COLUMN IF NOT EXISTS client_type VARCHAR(20) DEFAULT '';
