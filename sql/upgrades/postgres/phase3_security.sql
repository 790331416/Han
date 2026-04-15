-- =============================================
-- Phase 3 — 权限与安全 SQL 脚本
-- =============================================

-- 3.1 角色-部门关联表（自定义数据权限）
CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);

-- 3.4 登录日志表
CREATE TABLE IF NOT EXISTS sys_login_log (
    id          BIGINT PRIMARY KEY,
    username    VARCHAR(50),
    tenant_id   BIGINT,
    ip_addr     VARCHAR(128),
    status      SMALLINT DEFAULT 0,
    message     VARCHAR(255),
    client_type VARCHAR(20),
    browser     VARCHAR(50),
    os          VARCHAR(50),
    login_time  TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_log_time ON sys_login_log(login_time DESC);
CREATE INDEX IF NOT EXISTS idx_login_log_user ON sys_login_log(username);
CREATE INDEX IF NOT EXISTS idx_login_log_tenant ON sys_login_log(tenant_id);
