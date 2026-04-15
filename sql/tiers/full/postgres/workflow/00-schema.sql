-- PostgreSQL 版工作流扩展表。
-- Flowable 引擎运行时表由引擎自身维护，本文件只负责 Han 业务扩展表。

CREATE TABLE IF NOT EXISTS wf_category (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    category_code   VARCHAR(50)     NOT NULL,
    category_name   VARCHAR(100)    NOT NULL,
    parent_id       BIGINT          DEFAULT 0,
    ancestors       VARCHAR(500)    DEFAULT '',
    sort            INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    CONSTRAINT uk_wf_category_code UNIQUE (category_code, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_category_tenant ON wf_category(tenant_id);

CREATE TABLE IF NOT EXISTS wf_form (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    form_name       VARCHAR(100)    NOT NULL,
    form_key        VARCHAR(64)     NOT NULL,
    form_type       VARCHAR(20)     DEFAULT 'custom',
    form_content    TEXT,
    external_url    VARCHAR(500),
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    CONSTRAINT uk_wf_form_key UNIQUE (form_key, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_form_tenant ON wf_form(tenant_id);

CREATE TABLE IF NOT EXISTS wf_deploy_extend (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    deployment_id   VARCHAR(64)     NOT NULL,
    process_key     VARCHAR(64)     NOT NULL,
    process_name    VARCHAR(200),
    category_id     BIGINT,
    form_id         BIGINT,
    icon            VARCHAR(500),
    sort            INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_deployment ON wf_deploy_extend(deployment_id);
CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_tenant ON wf_deploy_extend(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_process_key ON wf_deploy_extend(process_key);

CREATE TABLE IF NOT EXISTS wf_instance_extend (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL,
    process_instance_id VARCHAR(64)     NOT NULL,
    process_key         VARCHAR(64)     NOT NULL,
    process_name        VARCHAR(200),
    title               VARCHAR(200),
    business_key        VARCHAR(64),
    business_table      VARCHAR(100),
    category_id         BIGINT,
    start_user_id       BIGINT,
    start_user_name     VARCHAR(50),
    start_dept_id       BIGINT,
    start_dept_name     VARCHAR(100),
    current_task_name   VARCHAR(200),
    current_assignee    VARCHAR(200),
    status              SMALLINT        DEFAULT 0,
    result              SMALLINT,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    duration            BIGINT,
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    del_flag            SMALLINT        DEFAULT 0,
    CONSTRAINT uk_wf_instance_extend_pi UNIQUE (process_instance_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_tenant ON wf_instance_extend(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_business ON wf_instance_extend(business_key);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_start_user ON wf_instance_extend(start_user_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_status ON wf_instance_extend(status);

CREATE TABLE IF NOT EXISTS wf_copy (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL,
    process_instance_id VARCHAR(64)     NOT NULL,
    task_id             VARCHAR(64),
    task_name           VARCHAR(200),
    title               VARCHAR(200),
    user_id             BIGINT          NOT NULL,
    user_name           VARCHAR(50),
    origin_user_id      BIGINT,
    origin_user_name    VARCHAR(50),
    is_read             SMALLINT        DEFAULT 0,
    read_time           TIMESTAMP,
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag            SMALLINT        DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wf_copy_process_instance ON wf_copy(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_copy_user ON wf_copy(user_id);
CREATE INDEX IF NOT EXISTS idx_wf_copy_tenant ON wf_copy(tenant_id);
