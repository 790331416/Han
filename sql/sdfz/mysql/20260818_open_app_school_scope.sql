-- 巴蜀云校开放平台：第三方应用目录数据按学校范围收敛。
-- 执行前确认目标库已包含 open_app，执行后应用需要重新启动或刷新配置。
ALTER TABLE open_app
    ADD COLUMN IF NOT EXISTS school_scope VARCHAR(2000) NULL COMMENT '开放目录授权学校ID，逗号分隔';
