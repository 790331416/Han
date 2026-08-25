SET NAMES utf8mb4;
-- 巴蜀云校生产 MySQL：han-file 文件元数据表
-- 用途：头像与通用文件上传；可重复执行。
-- 执行库：han

-- 执行前检查
SELECT DATABASE() AS current_database;
SHOW TABLES LIKE 'sys_file';

CREATE TABLE IF NOT EXISTS sys_file (
    id           BIGINT       NOT NULL COMMENT '文件主键',
    tenant_id    BIGINT       NULL COMMENT '租户ID',
    file_name    VARCHAR(200) NOT NULL COMMENT '原始文件名',
    file_path    VARCHAR(500) NOT NULL COMMENT '对象存储路径',
    file_url     VARCHAR(500) NULL COMMENT '公开访问地址',
    file_size    BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小',
    file_type    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '文件扩展名',
    mime_type    VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'MIME类型',
    storage_type VARCHAR(20)  NOT NULL DEFAULT 'rustfs' COMMENT '存储类型',
    bucket       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '存储定位符',
    md5          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'MD5',
    create_by    BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    del_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标志',
    PRIMARY KEY (id),
    KEY idx_sys_file_tenant_create (tenant_id, create_time),
    KEY idx_sys_file_bucket_path (bucket, file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- 执行后核验
SHOW COLUMNS FROM sys_file;
SHOW INDEX FROM sys_file;

-- 回滚（仅在确认未产生任何正式上传记录时执行）
-- DROP TABLE IF EXISTS sys_file;
