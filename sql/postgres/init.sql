-- =============================================
-- PostgreSQL 数据库初始化脚本
-- 列名严格匹配实体模型（BaseEntity/TenantEntity/BizEntity）
-- =============================================

-- 创建 Nacos 数据库（docker-entrypoint-initdb.d 仅在首次初始化时执行）
CREATE DATABASE nacos;

-- 切换到 han 主数据库（POSTGRES_DB=han 已自动创建）
\c han;

SET client_encoding = 'UTF8';

-- =============================================
-- 系统管理表（匹配 BaseEntity→TenantEntity→BizEntity 字段）
-- BaseEntity:  id, create_time, update_time, del_flag
-- TenantEntity: tenant_id
-- BizEntity:   create_by, create_name, update_by, update_name, create_dept, remark
-- =============================================

-- 用户表（实体: com.han.system.domain.entity.User）
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    dept_id         BIGINT,
    username        VARCHAR(50) NOT NULL UNIQUE,
    nickname        VARCHAR(50),
    password        VARCHAR(100) NOT NULL,
    avatar          VARCHAR(500),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    sex             INT DEFAULT 0,
    status          INT DEFAULT 0,
    login_ip        VARCHAR(128),
    login_time      TIMESTAMP,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_user IS '用户信息表';
COMMENT ON COLUMN sys_user.status IS '状态(0正常 1停用)';
COMMENT ON COLUMN sys_user.del_flag IS '删除标志(0存在 1删除)';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    role_name       VARCHAR(50) NOT NULL,
    role_key        VARCHAR(50) NOT NULL,
    role_sort       INT DEFAULT 0,
    data_scope      VARCHAR(10) DEFAULT '1',
    menu_check_strictly INT DEFAULT 1,
    dept_check_strictly INT DEFAULT 1,
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_role IS '角色信息表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- 菜单表 (TreeEntity: parent_id, ancestors, sort)
CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    menu_name       VARCHAR(50) NOT NULL,
    parent_id       BIGINT DEFAULT 0,
    ancestors       VARCHAR(500),
    sort            INT DEFAULT 0,
    order_num       INT DEFAULT 0,
    path            VARCHAR(200),
    component       VARCHAR(200),
    query           VARCHAR(255),
    menu_type       CHAR(1),
    visible         INT DEFAULT 0,
    status          INT DEFAULT 0,
    perms           VARCHAR(100),
    icon            VARCHAR(100),
    is_frame        INT DEFAULT 1,
    is_cache        INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_menu IS '菜单权限表';

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

-- =============================================
-- 部门表 (TreeEntity: parent_id, ancestors, sort)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_dept (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    parent_id       BIGINT DEFAULT 0,
    ancestors       VARCHAR(500),
    sort            INT DEFAULT 0,
    dept_name       VARCHAR(50),
    order_num       INT DEFAULT 0,
    leader          VARCHAR(50),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_dept IS '部门表';

-- =============================================
-- 岗位表 (BizEntity)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_post (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    post_code       VARCHAR(64),
    post_name       VARCHAR(50),
    post_sort       INT DEFAULT 0,
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_post IS '岗位信息表';

-- 用户岗位关联表
CREATE TABLE IF NOT EXISTS sys_user_post (
    user_id         BIGINT NOT NULL,
    post_id         BIGINT NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

-- =============================================
-- 字典类型表 (BizEntity)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    dict_name       VARCHAR(100),
    dict_type       VARCHAR(100),
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_dict_type IS '字典类型表';

-- =============================================
-- 字典数据表 (BizEntity)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    dict_sort       INT DEFAULT 0,
    dict_label      VARCHAR(100),
    dict_value      VARCHAR(100),
    dict_type       VARCHAR(100),
    css_class       VARCHAR(100),
    list_class      VARCHAR(100),
    is_default      VARCHAR(1) DEFAULT 'N',
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_dict_data IS '字典数据表';

-- =============================================
-- 参数配置表 (BizEntity)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_config (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    config_name     VARCHAR(100),
    config_key      VARCHAR(100),
    config_value    VARCHAR(500),
    config_type     CHAR(1) DEFAULT 'N',
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_config IS '参数配置表';

-- =============================================
-- 通知公告表 (BizEntity)
-- =============================================

CREATE TABLE IF NOT EXISTS sys_notice (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    notice_title    VARCHAR(100),
    notice_type     VARCHAR(1),
    notice_content  TEXT,
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_notice IS '通知公告表';

-- =============================================
-- 操作日志表
-- =============================================

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    title           VARCHAR(50),
    business_type   INT DEFAULT 0,
    method          VARCHAR(200),
    request_method  VARCHAR(10),
    oper_name       VARCHAR(50),
    oper_url        VARCHAR(500),
    oper_ip         VARCHAR(128),
    oper_param      TEXT,
    json_result     TEXT,
    status          INT DEFAULT 0,
    error_msg       TEXT,
    oper_time       TIMESTAMP
);

COMMENT ON TABLE sys_oper_log IS '操作日志记录';

-- =============================================
-- 登录日志表
-- =============================================

CREATE TABLE IF NOT EXISTS sys_login_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    username        VARCHAR(50),
    ipaddr          VARCHAR(128),
    login_location  VARCHAR(255),
    browser         VARCHAR(50),
    os              VARCHAR(50),
    status          INT DEFAULT 0,
    msg             VARCHAR(255),
    login_time      TIMESTAMP
);

COMMENT ON TABLE sys_login_log IS '系统登录日志';

-- =============================================
-- 定时任务表
-- =============================================

CREATE TABLE IF NOT EXISTS sys_job (
    job_id          BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    job_group       VARCHAR(64) DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    misfire_policy  CHAR(1) DEFAULT '3',
    concurrent      CHAR(1) DEFAULT '1',
    status          VARCHAR(1) DEFAULT '0',
    remark          VARCHAR(500),
    create_by       VARCHAR(50),
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP
);

COMMENT ON TABLE sys_job IS '定时任务调度表';

CREATE TABLE IF NOT EXISTS sys_job_log (
    job_log_id      BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    job_group       VARCHAR(64),
    invoke_target   VARCHAR(500),
    trace_id        VARCHAR(64),
    job_message     VARCHAR(500),
    status          VARCHAR(1) DEFAULT '0',
    exception_info  TEXT,
    start_time      TIMESTAMP,
    stop_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_log_trace_id ON sys_job_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_job_log_job_name ON sys_job_log(job_name);

-- =============================================
-- 租户表
-- =============================================

CREATE TABLE IF NOT EXISTS sys_tenant (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    tenant_name     VARCHAR(100) NOT NULL,
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(100),
    package_id      BIGINT,
    user_limit      INT DEFAULT -1,
    account_limit   INT DEFAULT -1,
    expire_time     TIMESTAMP,
    isolation_type  VARCHAR(20) DEFAULT 'logical',
    domain          VARCHAR(200),
    status          INT DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE sys_tenant IS '租户信息表';

-- 租户套餐表 (BaseEntity: id, create_time, update_time, del_flag)
CREATE TABLE IF NOT EXISTS sys_tenant_package (
    id              BIGSERIAL PRIMARY KEY,
    package_name    VARCHAR(100),
    menu_ids        TEXT,
    status          INT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0
);

COMMENT ON TABLE sys_tenant_package IS '租户套餐表';

-- =============================================
-- 工作流扩展表
-- =============================================

CREATE TABLE IF NOT EXISTS wf_category (
    id              BIGSERIAL PRIMARY KEY,
    category_name   VARCHAR(100) NOT NULL,
    category_code   VARCHAR(50) NOT NULL UNIQUE,
    parent_id       BIGINT DEFAULT 0,
    sort_order      INT DEFAULT 0,
    status          INT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0
);

-- =============================================
-- 开放平台表
-- =============================================

CREATE TABLE IF NOT EXISTS open_app (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    app_name        VARCHAR(100) NOT NULL,
    app_key         VARCHAR(100) NOT NULL UNIQUE,
    app_secret      VARCHAR(200) NOT NULL,
    app_icon        VARCHAR(500),
    app_desc        VARCHAR(500),
    app_type        VARCHAR(20),
    redirect_uris   TEXT,
    logout_uri      VARCHAR(500),
    scopes          VARCHAR(500),
    grant_types     VARCHAR(200),
    access_token_ttl INT,
    refresh_token_ttl INT,
    require_pkce    INT DEFAULT 0,
    auto_approve    INT DEFAULT 0,
    status          INT DEFAULT 0,
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(100),
    create_by       BIGINT,
    create_name     VARCHAR(50),
    update_by       BIGINT,
    update_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0,
    remark          VARCHAR(500)
);

COMMENT ON TABLE open_app IS '开放平台应用表';

-- 用户授权记录表 (TenantEntity: tenant_id)
CREATE TABLE IF NOT EXISTS open_user_authorization (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT,
    user_id         BIGINT,
    app_id          BIGINT,
    app_key         VARCHAR(100),
    scopes          VARCHAR(500),
    authorize_time  TIMESTAMP,
    last_access_time TIMESTAMP,
    status          INT DEFAULT 0,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        INT DEFAULT 0
);

COMMENT ON TABLE open_user_authorization IS '用户授权记录表';

-- =============================================
-- 初始化数据
-- =============================================

-- 管理员用户 (密码: admin123, BCrypt cost=10)
INSERT INTO sys_user (id, username, password, nickname, email, status)
VALUES (1, 'admin', '$2a$10$zgZ11pHorrsLGLWRWAUTcuxvwr5YEL7dTP8FfUnx.1NbIw5b14V6y', '管理员', 'admin@han.com', 0)
ON CONFLICT (username) DO NOTHING;

-- 管理员角色
INSERT INTO sys_role (id, role_name, role_key, role_sort)
VALUES (1, '超级管理员', 'admin', 1)
ON CONFLICT DO NOTHING;

-- 管理员角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1) ON CONFLICT DO NOTHING;

-- 打印完成信息
DO $$
BEGIN
    RAISE NOTICE '=============================================';
    RAISE NOTICE 'PostgreSQL 数据库初始化完成！';
    RAISE NOTICE '默认管理员账号: admin / admin123';
    RAISE NOTICE '=============================================';
END;
$$;
