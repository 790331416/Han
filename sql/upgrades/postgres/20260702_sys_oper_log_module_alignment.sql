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

-- 旧列 method / operator_type 代码已不使用，保留不删（含默认值，不影响插入）
