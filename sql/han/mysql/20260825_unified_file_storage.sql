SET NAMES utf8mb4;
-- 巴蜀云校 MySQL：统一文件与多对象存储（加法型升级）
-- 目标：管理端、校端和 H5 统一由 han-file 管理文件；新业务文件写 school-lj。
-- 安全：本脚本不写入任何 AccessKey、SecretKey 或主密钥；密钥只能通过管理端加密入库。
-- 执行前：确认当前数据库为 han，已备份；本脚本不删除表、列和业务对象。

SELECT DATABASE() AS current_database;

CREATE TABLE IF NOT EXISTS sys_oss_config (
    oss_config_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '存储配置主键',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID，0表示全局',
    config_key VARCHAR(64) NOT NULL COMMENT '稳定配置标识',
    config_name VARCHAR(128) NULL COMMENT '配置名称',
    provider_type VARCHAR(32) NOT NULL DEFAULT 'S3' COMMENT '存储协议类型',
    endpoint VARCHAR(512) NOT NULL COMMENT '服务端Endpoint',
    public_endpoint VARCHAR(512) NULL COMMENT '可选浏览器Endpoint',
    region VARCHAR(64) NOT NULL COMMENT 'S3签名Region',
    bucket_name VARCHAR(128) NOT NULL COMMENT '桶名称',
    prefix VARCHAR(256) NOT NULL DEFAULT '' COMMENT '对象Key前缀',
    is_https CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否HTTPS：0是、1否',
    path_style TINYINT NOT NULL DEFAULT 1 COMMENT '路径式访问：1是、0否',
    access_key_ciphertext VARCHAR(1024) NOT NULL COMMENT 'AES-GCM加密后的AccessKey',
    secret_key_ciphertext VARCHAR(1024) NOT NULL COMMENT 'AES-GCM加密后的SecretKey',
    key_version INT NOT NULL DEFAULT 1 COMMENT '主密钥版本',
    status CHAR(1) NOT NULL DEFAULT '1' COMMENT '0正常、1停用、2只读',
    config_version INT NOT NULL DEFAULT 1 COMMENT '配置缓存版本',
    remark VARCHAR(500) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志',
    PRIMARY KEY (oss_config_id),
    UNIQUE KEY uk_sys_oss_config_tenant_key (tenant_id, config_key),
    KEY idx_sys_oss_config_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一对象存储配置';

CREATE TABLE IF NOT EXISTS sys_storage_active (
    tenant_id BIGINT NOT NULL COMMENT '租户ID，0表示全局',
    oss_config_id BIGINT NOT NULL COMMENT '当前默认写入存储配置',
    version INT NOT NULL DEFAULT 1 COMMENT '活动指针版本',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (tenant_id),
    KEY idx_sys_storage_active_config (oss_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前默认写入对象存储指针';

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'school_id'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN school_id BIGINT NULL COMMENT ''所属学校ID'' AFTER tenant_id');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'storage_config_id'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN storage_config_id BIGINT NULL COMMENT ''上传时对象存储配置ID'' AFTER bucket');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'object_key'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN object_key VARCHAR(500) NULL COMMENT ''对象Key'' AFTER storage_config_id');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'biz_type'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN biz_type VARCHAR(64) NOT NULL DEFAULT ''general'' COMMENT ''业务类型'' AFTER object_key');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'visibility'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT ''PRIVATE'' COMMENT ''访问范围：PUBLIC/PRIVATE'' AFTER biz_type');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND column_name = 'checksum'),
    'SELECT 1',
    'ALTER TABLE sys_file ADD COLUMN checksum VARCHAR(128) NULL COMMENT ''文件校验值'' AFTER visibility');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ai_document')
    AND NOT EXISTS(SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'ai_document' AND column_name = 'file_id'),
    'ALTER TABLE ai_document ADD COLUMN file_id BIGINT NULL COMMENT ''Han统一文件服务ID'' AFTER file_path',
    'SELECT 1');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND index_name = 'idx_sys_file_storage_config'),
    'SELECT 1',
    'CREATE INDEX idx_sys_file_storage_config ON sys_file(storage_config_id, del_flag)');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'sys_file' AND index_name = 'idx_sys_file_school_biz'),
    'SELECT 1',
    'CREATE INDEX idx_sys_file_school_biz ON sys_file(school_id, biz_type, del_flag)');
PREPARE statement_from_sql FROM @sql;
EXECUTE statement_from_sql;
DEALLOCATE PREPARE statement_from_sql;

-- 执行后核验：不应插入明文密钥，当前活动配置需通过管理端测试连接并设为默认写入。
SHOW COLUMNS FROM sys_oss_config;
SHOW COLUMNS FROM sys_storage_active;
SHOW COLUMNS FROM sys_file;

-- 回滚边界：本脚本为加法型结构升级。未确认没有引用数据前，不删除表、列或索引。
