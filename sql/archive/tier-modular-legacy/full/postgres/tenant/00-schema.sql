-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

CREATE TABLE sys_tenant (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    tenant_name     VARCHAR(100)    NOT NULL,
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(100),
    package_id      BIGINT,
    user_limit      INT             DEFAULT -1,
    account_limit   INT             DEFAULT -1,
    expire_time     TIMESTAMP,
    isolation_type  VARCHAR(20)     DEFAULT 'logical',
    domain          VARCHAR(200),
    status          SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0
);

-- =============================================
-- 2. 租户套餐表
-- =============================================
CREATE TABLE sys_tenant_package (
    id              BIGINT          NOT NULL PRIMARY KEY,
    package_name    VARCHAR(100)    NOT NULL,
    menu_ids        TEXT,
    status          SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0
);

-- =============================================
-- 22. 租户资源配额表
-- =============================================
CREATE TABLE sys_tenant_quota (
    quota_id        BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    user_limit      INT             DEFAULT -1,
    storage_limit   BIGINT          DEFAULT -1,
    api_limit       BIGINT          DEFAULT -1,
    user_used       INT             DEFAULT 0,
    storage_used    BIGINT          DEFAULT 0,
    api_used        BIGINT          DEFAULT 0,
    reset_cycle     VARCHAR(20)     DEFAULT 'monthly',
    last_reset_time TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_tenant_quota IS '租户资源配额';
CREATE UNIQUE INDEX idx_tenant_quota_tenant ON sys_tenant_quota(tenant_id);
