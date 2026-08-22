-- 巴蜀云校开放平台：第三方应用目录数据按学校范围收敛。
-- 执行前确认目标库已包含 open_app，执行后应用需要重新启动或刷新配置。
SET NAMES utf8mb4;
SET @open_school_scope_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_app'
      AND column_name = 'school_scope'
);
SET @open_school_scope_sql := IF(
    @open_school_scope_exists = 0,
    'ALTER TABLE open_app ADD COLUMN school_scope VARCHAR(2000) NULL COMMENT ''开放目录授权学校ID，逗号分隔''',
    'SELECT 1'
);
PREPARE open_school_scope_stmt FROM @open_school_scope_sql;
EXECUTE open_school_scope_stmt;
DEALLOCATE PREPARE open_school_scope_stmt;
