-- 统一文件与多对象存储加法型升级（PostgreSQL）
-- 不插入任何 AK/SK 或主密钥；现有明文 OSS 配置必须在管理端重新录入并加密保存。

ALTER TABLE IF EXISTS sys_file
    ADD COLUMN IF NOT EXISTS school_id BIGINT,
    ADD COLUMN IF NOT EXISTS storage_config_id BIGINT,
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS biz_type VARCHAR(64) DEFAULT 'general',
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(16) DEFAULT 'PRIVATE',
    ADD COLUMN IF NOT EXISTS checksum VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_sys_file_storage_config ON sys_file(storage_config_id, del_flag);
CREATE INDEX IF NOT EXISTS idx_sys_file_school_biz ON sys_file(school_id, biz_type, del_flag);

ALTER TABLE IF EXISTS sys_oss_config
    ADD COLUMN IF NOT EXISTS config_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(32) DEFAULT 'S3',
    ADD COLUMN IF NOT EXISTS public_endpoint VARCHAR(500),
    ADD COLUMN IF NOT EXISTS path_style BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS access_key_ciphertext VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS secret_key_ciphertext VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS key_version INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS config_version INTEGER DEFAULT 1;

ALTER TABLE IF EXISTS sys_oss_config
    ALTER COLUMN tenant_id SET DEFAULT 0;

UPDATE sys_oss_config SET tenant_id = 0 WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_oss_config_tenant_key
    ON sys_oss_config(tenant_id, config_key);

CREATE TABLE IF NOT EXISTS sys_storage_active (
    tenant_id BIGINT PRIMARY KEY,
    oss_config_id BIGINT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    update_by BIGINT,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE IF EXISTS ai_document ADD COLUMN IF NOT EXISTS file_id BIGINT;

-- 回滚边界：本次为加法型升级。若未完成人工迁移与引用核对，不删除新增列、表、索引或旧明文列。
