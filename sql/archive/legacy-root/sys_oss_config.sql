-- OSS存储配置表
CREATE TABLE IF NOT EXISTS sys_oss_config (
    oss_config_id  BIGSERIAL PRIMARY KEY,
    config_key     VARCHAR(100) NOT NULL,
    access_key     VARCHAR(500),
    secret_key     VARCHAR(500),
    bucket_name    VARCHAR(200),
    prefix         VARCHAR(200) DEFAULT '',
    endpoint       VARCHAR(500),
    region         VARCHAR(100),
    is_https       CHAR(1) DEFAULT '0',
    status         CHAR(1) DEFAULT '1',
    remark         VARCHAR(500),
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_oss_config IS 'OSS存储配置';
