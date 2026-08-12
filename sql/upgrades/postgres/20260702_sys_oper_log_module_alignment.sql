-- =============================================
-- sys_oper_log 列名对齐脚本（2026-07-02）
-- 背景：95 库 sys_oper_log 仍为旧列名（title / business_type，缺 oper_user_id），
--       代码侧 SysOperLogPo 使用新列名（module / oper_type / oper_user_id），
--       导致 OperLogAspect 异步写入报 column "module" does not exist，操作日志全部丢失。
-- 幂等：可重复执行。
-- =============================================

-- title -> module
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'title'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'module'
    ) THEN
        EXECUTE 'ALTER TABLE sys_oper_log RENAME COLUMN title TO module';
    END IF;
END $$;

-- business_type -> oper_type
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'business_type'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'oper_type'
    ) THEN
        EXECUTE 'ALTER TABLE sys_oper_log RENAME COLUMN business_type TO oper_type';
    END IF;
END $$;

-- 补齐代码侧需要的列
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS module VARCHAR(100) DEFAULT '';
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS oper_type SMALLINT DEFAULT 0;
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS oper_user_id BIGINT;
ALTER TABLE sys_oper_log ADD COLUMN IF NOT EXISTS oper_location VARCHAR(255) DEFAULT '';

-- 20260812 补充：新旧列并存时把历史数据搬到新列
-- 上面的 RENAME 只在「旧列存在且新列不存在」时触发。若某个库先跑过 phase4_management.sql
-- （它建的 sys_oper_log 自带 module / oper_type），就会出现新旧列同时存在的情况：
-- RENAME 分支跳过、ADD COLUMN IF NOT EXISTS 也是空操作，旧列里的历史日志被永久孤立，
-- 管理端只读新列，日志历史看起来「凭空消失」。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'title'
    ) THEN
        EXECUTE 'UPDATE sys_oper_log SET module = title WHERE COALESCE(module, '''') = '''' AND COALESCE(title, '''') <> ''''';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_oper_log' AND column_name = 'business_type'
    ) THEN
        EXECUTE 'UPDATE sys_oper_log SET oper_type = business_type WHERE COALESCE(oper_type, 0) = 0 AND COALESCE(business_type, 0) <> 0';
    END IF;
END $$;

-- 旧列 method / operator_type 代码已不使用，保留不删（含默认值，不影响插入）
