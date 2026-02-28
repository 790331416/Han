-- =============================================
-- PostgreSQL 数据库初始化脚本
-- 自动创建所有需要的数据库和表结构
-- =============================================

-- 创建 Nacos 数据库（使用 PostgreSQL 兼容语法）
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'nacos') THEN
        CREATE DATABASE nacos;
    END IF;
END $$;

-- 切换到 han 主数据库
\c postgres;

-- 设置字符集
SET client_encoding = 'UTF8';

-- =============================================
-- 系统管理表
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    user_id         BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(100) NOT NULL,
    nick_name       VARCHAR(50),
    email           VARCHAR(100),
    phone           VARCHAR(20),
    sex             CHAR(1) DEFAULT '0',
    avatar          VARCHAR(500),
    status          CHAR(1) DEFAULT '0',
    deleted         CHAR(1) DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_user IS '用户信息表';
COMMENT ON COLUMN sys_user.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_user.username IS '用户账号';
COMMENT ON COLUMN sys_user.password IS '密码';
COMMENT ON COLUMN sys_user.status IS '状态(0正常 1停用)';
COMMENT ON COLUMN sys_user.deleted IS '删除标志(0存在 1删除)';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    role_id         BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    role_name       VARCHAR(50) NOT NULL,
    role_key        VARCHAR(50) NOT NULL,
    role_sort       INT DEFAULT 0,
    status          CHAR(1) DEFAULT '0',
    deleted         CHAR(1) DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_role IS '角色信息表';

-- 菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    menu_id         BIGSERIAL PRIMARY KEY,
    menu_name       VARCHAR(50) NOT NULL,
    parent_id       BIGINT DEFAULT 0,
    order_num       INT DEFAULT 0,
    path            VARCHAR(200),
    component       VARCHAR(200),
    menu_type       CHAR(1),
    visible         CHAR(1) DEFAULT '0',
    status          CHAR(1) DEFAULT '0',
    perms           VARCHAR(100),
    icon            VARCHAR(100),
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_menu IS '菜单权限表';

-- =============================================
-- 定时任务表 (JobFlow)
-- =============================================

-- 任务定义表
CREATE TABLE IF NOT EXISTS sys_job (
    job_id          BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100) NOT NULL,
    job_group       VARCHAR(64) DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    misfire_policy  CHAR(1) DEFAULT '3',
    concurrent      CHAR(1) DEFAULT '1',
    status          CHAR(1) DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_job IS '定时任务调度表';
COMMENT ON COLUMN sys_job.invoke_target IS '调用目标(beanName.methodName)';
COMMENT ON COLUMN sys_job.cron_expression IS 'cron执行表达式';
COMMENT ON COLUMN sys_job.concurrent IS '是否并发(0允许 1禁止)';
COMMENT ON COLUMN sys_job.status IS '状态(0正常 1暂停)';

-- 任务执行日志表
CREATE TABLE IF NOT EXISTS sys_job_log (
    job_log_id      BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    job_group       VARCHAR(64),
    invoke_target   VARCHAR(500),
    trace_id        VARCHAR(64),
    job_message     VARCHAR(500),
    status          CHAR(1) DEFAULT '0',
    exception_info  TEXT,
    start_time      TIMESTAMP,
    stop_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_job_log IS '定时任务执行日志';
COMMENT ON COLUMN sys_job_log.trace_id IS '全链路追踪ID(JobFlow特性)';
COMMENT ON COLUMN sys_job_log.status IS '执行状态(0成功 1失败)';

-- 创建索引
CREATE INDEX idx_job_log_trace_id ON sys_job_log(trace_id);
CREATE INDEX idx_job_log_job_name ON sys_job_log(job_name);
CREATE INDEX idx_job_log_create_time ON sys_job_log(create_time);

-- =============================================
-- 租户表
-- =============================================

CREATE TABLE IF NOT EXISTS sys_tenant (
    tenant_id       BIGSERIAL PRIMARY KEY,
    tenant_name     VARCHAR(100) NOT NULL,
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    package_id      BIGINT,
    expire_time     TIMESTAMP,
    account_count   INT DEFAULT -1,
    status          CHAR(1) DEFAULT '0',
    deleted         CHAR(1) DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP
);

COMMENT ON TABLE sys_tenant IS '租户信息表';
COMMENT ON COLUMN sys_tenant.account_count IS '账号数量(-1不限制)';
COMMENT ON COLUMN sys_tenant.status IS '状态(0正常 1停用)';

-- =============================================
-- 工作流扩展表
-- =============================================

CREATE TABLE IF NOT EXISTS wf_category (
    category_id     BIGSERIAL PRIMARY KEY,
    category_name   VARCHAR(100) NOT NULL,
    category_code   VARCHAR(50) NOT NULL UNIQUE,
    parent_id       BIGINT DEFAULT 0,
    sort_order      INT DEFAULT 0,
    status          CHAR(1) DEFAULT '0',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP
);

COMMENT ON TABLE wf_category IS '流程分类表';

-- =============================================
-- 开放平台表
-- =============================================

CREATE TABLE IF NOT EXISTS open_app (
    app_id          BIGSERIAL PRIMARY KEY,
    app_name        VARCHAR(100) NOT NULL,
    app_key         VARCHAR(100) NOT NULL UNIQUE,
    app_secret      VARCHAR(200) NOT NULL,
    status          CHAR(1) DEFAULT '0',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP
);

COMMENT ON TABLE open_app IS '开放平台应用表';

-- =============================================
-- 初始化数据
-- =============================================

-- 插入管理员用户 (密码: admin123)
INSERT INTO sys_user (user_id, username, password, nick_name, email, status)
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE/T/1RxrmdTBq', '管理员', 'admin@han.com', '0')
ON CONFLICT (username) DO NOTHING;

-- 插入示例任务
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, status, remark)
VALUES 
    ('系统监控', 'SYSTEM', 'sampleTask.execute', '0 0/5 * * * ?', '0', '每5分钟执行一次'),
    ('数据同步', 'SYSTEM', 'sampleShardTask.syncData(100000,5)', '0 0 2 * * ?', '1', '每天凌晨2点执行')
ON CONFLICT DO NOTHING;

-- 创建数据库函数：更新 update_time
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为所有表添加触发器
DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN 
        SELECT table_name 
        FROM information_schema.columns 
        WHERE column_name = 'update_time' 
        AND table_schema = 'public'
    LOOP
        EXECUTE format('
            CREATE TRIGGER update_%I_modtime 
            BEFORE UPDATE ON %I 
            FOR EACH ROW 
            EXECUTE FUNCTION update_modified_column();
        ', t, t);
    END LOOP;
END;
$$;

-- 打印完成信息
DO $$
BEGIN
    RAISE NOTICE '=============================================';
    RAISE NOTICE 'PostgreSQL 数据库初始化完成！';
    RAISE NOTICE '默认管理员账号: admin / admin123';
    RAISE NOTICE '=============================================';
END;
$$;
