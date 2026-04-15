-- =============================================
-- Phase 4 — 管理优化 SQL 脚本
-- =============================================

-- 4.4 操作日志表
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT,
    module          VARCHAR(50),
    oper_type       SMALLINT DEFAULT 0,
    oper_name       VARCHAR(50),
    oper_user_id    BIGINT,
    dept_name       VARCHAR(50),
    oper_url        VARCHAR(255),
    oper_ip         VARCHAR(128),
    request_method  VARCHAR(10),
    oper_param      TEXT,
    json_result     TEXT,
    status          SMALLINT DEFAULT 0,
    error_msg       TEXT,
    cost_time       BIGINT DEFAULT 0,
    oper_time       TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_oper_log_time ON sys_oper_log(oper_time DESC);
CREATE INDEX IF NOT EXISTS idx_oper_log_tenant ON sys_oper_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_oper_log_user ON sys_oper_log(oper_name);
