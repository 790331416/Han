-- 租户资源配额表
CREATE TABLE IF NOT EXISTS sys_tenant_quota (
    quota_id       BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL,
    user_limit     INT DEFAULT -1,
    storage_limit  BIGINT DEFAULT -1,
    api_limit      BIGINT DEFAULT -1,
    user_used      INT DEFAULT 0,
    storage_used   BIGINT DEFAULT 0,
    api_used       BIGINT DEFAULT 0,
    reset_cycle    VARCHAR(20) DEFAULT 'monthly',
    last_reset_time TIMESTAMP,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_tenant_quota IS '租户资源配额';
COMMENT ON COLUMN sys_tenant_quota.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_tenant_quota.user_limit IS '用户数限制(-1不限)';
COMMENT ON COLUMN sys_tenant_quota.storage_limit IS '存储空间限制(字节,-1不限)';
COMMENT ON COLUMN sys_tenant_quota.api_limit IS 'API调用次数限制(-1不限)';
COMMENT ON COLUMN sys_tenant_quota.user_used IS '已使用用户数';
COMMENT ON COLUMN sys_tenant_quota.storage_used IS '已使用存储(字节)';
COMMENT ON COLUMN sys_tenant_quota.api_used IS '已使用API调用次数';
COMMENT ON COLUMN sys_tenant_quota.reset_cycle IS '重置周期(monthly/yearly/never)';

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_quota_tenant ON sys_tenant_quota(tenant_id);
