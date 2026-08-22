-- 扩展 open_api_resource 表，新增发布/申请/调测相关字段，并新增资源版本表。
-- DDL 逐条幂等：加列用 information_schema 判断；MySQL DDL 隐式提交，本脚本无事务回滚，重复执行安全。
SET NAMES utf8mb4;

-- 新增字段（幂等，不存在时才添加）
SET @publish_status_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_api_resource'
      AND column_name = 'publish_status'
);
SET @publish_status_sql := IF(
    @publish_status_exists = 0,
    'ALTER TABLE open_api_resource ADD COLUMN publish_status SMALLINT NOT NULL DEFAULT 0 COMMENT ''发布状态：0草稿 1待审核 2已发布 3已下线'' AFTER status',
    'SELECT 1'
);
PREPARE publish_status_stmt FROM @publish_status_sql;
EXECUTE publish_status_stmt;
DEALLOCATE PREPARE publish_status_stmt;

SET @allow_apply_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_api_resource'
      AND column_name = 'allow_apply'
);
SET @allow_apply_sql := IF(
    @allow_apply_exists = 0,
    'ALTER TABLE open_api_resource ADD COLUMN allow_apply SMALLINT NOT NULL DEFAULT 1 COMMENT ''是否允许厂商申请：0否 1是'' AFTER publish_status',
    'SELECT 1'
);
PREPARE allow_apply_stmt FROM @allow_apply_sql;
EXECUTE allow_apply_stmt;
DEALLOCATE PREPARE allow_apply_stmt;

SET @allow_test_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_api_resource'
      AND column_name = 'allow_test'
);
SET @allow_test_sql := IF(
    @allow_test_exists = 0,
    'ALTER TABLE open_api_resource ADD COLUMN allow_test SMALLINT NOT NULL DEFAULT 1 COMMENT ''是否允许在线调测：0否 1是'' AFTER allow_apply',
    'SELECT 1'
);
PREPARE allow_test_stmt FROM @allow_test_sql;
EXECUTE allow_test_stmt;
DEALLOCATE PREPARE allow_test_stmt;

SET @owner_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_api_resource'
      AND column_name = 'owner'
);
SET @owner_sql := IF(
    @owner_exists = 0,
    'ALTER TABLE open_api_resource ADD COLUMN owner VARCHAR(50) NULL COMMENT ''负责人'' AFTER allow_test',
    'SELECT 1'
);
PREPARE owner_stmt FROM @owner_sql;
EXECUTE owner_stmt;
DEALLOCATE PREPARE owner_stmt;

-- 新增资源版本表
CREATE TABLE IF NOT EXISTS open_api_resource_version (
    id              BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    resource_id     BIGINT        NOT NULL COMMENT '关联资源ID',
    version         VARCHAR(20)   NOT NULL COMMENT '版本号，如v1、v2',
    openapi_json    TEXT          NULL COMMENT 'OpenAPI 3.1 JSON契约',
    request_example_json TEXT      NULL COMMENT '请求实例JSON',
    response_examples_json TEXT    NULL COMMENT '响应实例JSON，包含成功和常见失败响应',
    error_examples_json TEXT       NULL COMMENT '错误实例JSON',
    auth_config_json TEXT          NULL COMMENT '认证配置JSON',
    sandbox_config_json TEXT       NULL COMMENT '沙箱配置JSON',
    status          SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0草稿 1已发布 2已废弃',
    published_at    DATETIME      NULL COMMENT '发布时间',
    deprecated_at   DATETIME      NULL COMMENT '废弃时间',
    create_by       BIGINT        NULL COMMENT '创建人',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT        NULL COMMENT '更新人',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    UNIQUE KEY uk_open_api_resource_version(resource_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放接口资源版本表';
