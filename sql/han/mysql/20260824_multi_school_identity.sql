-- 一账号多学校身份：身份查询组合索引（幂等迁移）
-- 执行库：han（MySQL 8.4，SDFZ 教育库）
-- 影响范围：仅 edu_person 增加一条组合索引，不改表结构、不写业务数据、不删历史数据
-- 回滚：DROP INDEX idx_edu_person_identity ON edu_person;
SET NAMES utf8mb4;

-- ==============================================
-- 1. 执行前检查（只读）：确认身份列与外部幂等唯一键齐备
-- ==============================================
SELECT 'precheck:edu_person_identity_columns' AS chk,
       SUM(CASE WHEN column_name IN ('user_id','school_id','person_type','duty_code','status','leave_flag','external_identity_id')
                THEN 1 ELSE 0 END) AS hit
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'edu_person'
GROUP BY table_name;

-- ==============================================
-- 2. 迁移：身份列表热路径组合索引（user_id + status + del_flag）
--    幂等：information_schema 判断存在则跳过
-- ==============================================
SET @idx_identity_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'edu_person'
      AND index_name = 'idx_edu_person_identity'
);
SET @idx_identity_sql := IF(
    @idx_identity_exists = 0,
    'ALTER TABLE edu_person ADD KEY idx_edu_person_identity (tenant_id, user_id, status, del_flag)',
    'SELECT 1'
);
PREPARE idx_identity_stmt FROM @idx_identity_sql;
EXECUTE idx_identity_stmt;
DEALLOCATE PREPARE idx_identity_stmt;

-- ==============================================
-- 3. 执行后核验：索引存在
-- ==============================================
SELECT 'postcheck:idx_edu_person_identity' AS chk,
       COUNT(*) AS hit
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'edu_person'
  AND index_name = 'idx_edu_person_identity';

-- ==============================================
-- 4. 回滚说明
--    若需回滚，执行：
--    ALTER TABLE edu_person DROP INDEX idx_edu_person_identity;
--    该索引仅优化身份列表查询，删除不影响数据与功能正确性。
-- ==============================================
