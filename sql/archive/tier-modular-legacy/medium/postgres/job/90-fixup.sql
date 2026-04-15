-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- ============================================================
-- JobFlow 迁移 SQL 脚本
-- 说明：将 Quartz 调度替换为 JobFlow 自研调度框架
-- 执行环境：PostgreSQL
-- ============================================================

-- 1. 为 sys_job 表新增 JobFlow 所需字段
ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS service_name VARCHAR(100);
ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS handler VARCHAR(100);

COMMENT ON COLUMN sys_job.service_name IS '执行器服务名（Nacos 中的服务名，远程调用时使用）';
COMMENT ON COLUMN sys_job.handler IS '执行器处理方法（远程调用时的 handler 路径）';

-- 2. 清理 Quartz 相关表（按依赖顺序删除）
DROP TABLE IF EXISTS qrtz_fired_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_paused_trigger_grps CASCADE;
DROP TABLE IF EXISTS qrtz_scheduler_state CASCADE;
DROP TABLE IF EXISTS qrtz_locks CASCADE;
DROP TABLE IF EXISTS qrtz_simprop_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_simple_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_cron_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_blob_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_job_details CASCADE;
DROP TABLE IF EXISTS qrtz_calendars CASCADE;

-- 3. 验证变更
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'sys_job' AND column_name IN ('service_name', 'handler');
