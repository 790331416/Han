-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

CREATE TABLE sys_file (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    file_name       VARCHAR(200)    NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_url        VARCHAR(500),
    file_size       BIGINT          DEFAULT 0,
    file_type       VARCHAR(50)     DEFAULT '',
    mime_type       VARCHAR(100)    DEFAULT '',
    storage_type    VARCHAR(20)     DEFAULT 'local',
    bucket          VARCHAR(100)    DEFAULT '',
    md5             VARCHAR(64)     DEFAULT '',
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT        DEFAULT 0
);

CREATE TABLE sys_oss_config (
    oss_config_id  BIGSERIAL       PRIMARY KEY,
    config_key     VARCHAR(100)    NOT NULL,
    access_key     VARCHAR(500),
    secret_key     VARCHAR(500),
    bucket_name    VARCHAR(200),
    prefix         VARCHAR(200)    DEFAULT '',
    endpoint       VARCHAR(500),
    region         VARCHAR(100),
    is_https       CHAR(1)         DEFAULT '0',
    status         CHAR(1)         DEFAULT '1',
    remark         VARCHAR(500),
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_oss_config IS 'OSS存储配置';
