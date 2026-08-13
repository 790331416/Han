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

-- 20260812 补充：新旧列并存时把历史数据搬到新列
-- 上面的 RENAME 只在「旧列存在且新列不存在」时触发；两列同时存在时旧列里的历史
-- 登录记录会被永久孤立，管理端只读新列。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_login_log' AND column_name = 'ipaddr'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_login_log' AND column_name = 'ip_addr'
    ) THEN
        EXECUTE 'UPDATE sys_login_log SET ip_addr = ipaddr WHERE COALESCE(ip_addr, '''') = '''' AND COALESCE(ipaddr, '''') <> ''''';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_login_log' AND column_name = 'msg'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_login_log' AND column_name = 'message'
    ) THEN
        EXECUTE 'UPDATE sys_login_log SET message = msg WHERE COALESCE(message, '''') = '''' AND COALESCE(msg, '''') <> ''''';
    END IF;
END $$;
