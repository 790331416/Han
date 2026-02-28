-- =============================================
-- JobFlow 改造 V1: 添加 TraceId 全链路追踪
-- 执行时间: 2026-01-28
-- =============================================

USE han;

-- 1. 为 sys_job_log 表添加 trace_id 字段
ALTER TABLE sys_job_log
ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '全链路追踪ID' AFTER invoke_target;

-- 2. 创建 trace_id 索引（用于快速查询）
CREATE INDEX idx_trace_id ON sys_job_log(trace_id);

-- 3. 验证修改
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM
    INFORMATION_SCHEMA.COLUMNS
WHERE
    TABLE_SCHEMA = 'han'
    AND TABLE_NAME = 'sys_job_log'
    AND COLUMN_NAME = 'trace_id';

-- 执行后应该看到:
-- +-------------+-------------+------------------+
-- | COLUMN_NAME | COLUMN_TYPE | COLUMN_COMMENT   |
-- +-------------+-------------+------------------+
-- | trace_id    | varchar(64) | 全链路追踪ID     |
-- +-------------+-------------+------------------+
