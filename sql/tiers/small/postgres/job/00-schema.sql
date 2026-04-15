-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- =============================================
-- 定时任务表
-- =============================================
CREATE TABLE sys_job (
    job_id          BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100)    NOT NULL,
    job_group       VARCHAR(64)     DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500)    NOT NULL,
    service_name    VARCHAR(100),
    handler         VARCHAR(200),
    cron_expression VARCHAR(255)    NOT NULL,
    misfire_policy  CHAR(1)         DEFAULT '3',
    concurrent      CHAR(1)         DEFAULT '1',
    status          CHAR(1)         DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

CREATE TABLE sys_job_log (
    job_log_id      BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100)    NOT NULL,
    job_group       VARCHAR(64),
    invoke_target   VARCHAR(500),
    trace_id        VARCHAR(64),
    job_message     VARCHAR(500),
    status          CHAR(1)         DEFAULT '0',
    exception_info  TEXT,
    start_time      TIMESTAMP,
    stop_time       TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
