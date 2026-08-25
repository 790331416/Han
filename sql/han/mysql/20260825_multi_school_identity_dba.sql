-- 巴蜀云校：一账号多学校身份生产索引补充
-- 执行人：具备 han.edu_person ALTER 权限的 DBA 账号
-- 执行库：han（已在生产 MySQL 8.0.36 预检）
-- 变更：仅新增二级索引 idx_edu_person_identity，不改数据、不删字段、不改约束。
-- 回滚：ALTER TABLE `edu_person` DROP INDEX `idx_edu_person_identity`;

USE `han`;

-- 1. 执行前核验：应看到 database_name=han、required_columns=7、index_exists=0 或 1。
SELECT
    DATABASE() AS database_name,
    VERSION() AS mysql_version;

SELECT
    COUNT(*) AS required_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'edu_person'
  AND column_name IN (
      'tenant_id', 'user_id', 'school_id', 'person_type',
      'duty_code', 'status', 'del_flag'
  );

SELECT
    COUNT(*) AS index_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'edu_person'
  AND index_name = 'idx_edu_person_identity';

-- 2. 幂等创建索引：已存在时只返回提示，不重复执行 ALTER。
SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'edu_person'
      AND index_name = 'idx_edu_person_identity'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `edu_person` ADD KEY `idx_edu_person_identity` (`tenant_id`, `user_id`, `status`, `del_flag`)',
    'SELECT ''idx_edu_person_identity already exists; no DDL executed'' AS message'
);

PREPARE multi_identity_index_stmt FROM @ddl;
EXECUTE multi_identity_index_stmt;
DEALLOCATE PREPARE multi_identity_index_stmt;

-- 3. 执行后核验：必须只返回一行，列顺序为 tenant_id,user_id,status,del_flag。
SELECT
    index_name,
    GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'edu_person'
  AND index_name = 'idx_edu_person_identity'
GROUP BY index_name;

-- 4. 只读业务核验：不应改变人员数据。
SELECT
    COUNT(*) AS active_person_rows,
    COUNT(DISTINCT user_id) AS bound_account_count
FROM `edu_person`
WHERE del_flag = 0;

-- 如需回滚，仅在确认索引导致问题时单独执行以下一行：
-- ALTER TABLE `edu_person` DROP INDEX `idx_edu_person_identity`;
