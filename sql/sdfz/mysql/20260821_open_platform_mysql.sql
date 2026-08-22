-- 巴蜀云校开放平台正式启用：应用、学校范围与管理端菜单。
-- 仅新增/幂等补齐，不预置第三方应用或任何密钥。
SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS open_app (
    id                   BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id            BIGINT        NULL COMMENT '租户ID',
    app_name             VARCHAR(100) NOT NULL COMMENT '应用名称',
    app_key              VARCHAR(100) NOT NULL COMMENT 'OAuth2 Client ID',
    app_secret           VARCHAR(255) NOT NULL COMMENT 'OAuth2 Client Secret哈希',
    app_icon             VARCHAR(500) NULL COMMENT '应用图标',
    app_desc             VARCHAR(1000) NULL COMMENT '应用描述',
    app_type             VARCHAR(20)  NOT NULL COMMENT '应用类型：web/mobile/server',
    redirect_uris        VARCHAR(2000) NULL COMMENT '授权回调地址，逗号分隔',
    logout_uri           VARCHAR(2000) NULL COMMENT '登出回调地址',
    scopes               VARCHAR(1000) NOT NULL COMMENT '授权范围，逗号分隔',
    school_scope         VARCHAR(2000) NULL COMMENT '开放目录授权学校ID，逗号分隔',
    grant_types          VARCHAR(500) NOT NULL COMMENT '授权类型，逗号分隔',
    access_token_ttl     INT           NOT NULL DEFAULT 7200 COMMENT 'AccessToken有效期（秒）',
    refresh_token_ttl    INT           NOT NULL DEFAULT 604800 COMMENT 'RefreshToken有效期（秒）',
    require_pkce         SMALLINT      NOT NULL DEFAULT 0 COMMENT '是否要求PKCE：0否，1是',
    auto_approve         SMALLINT      NOT NULL DEFAULT 0 COMMENT '是否自动授权：0否，1是',
    status               SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0正常，1停用',
    contact_name         VARCHAR(100)  NULL COMMENT '联系人',
    contact_phone        VARCHAR(50)   NULL COMMENT '联系电话',
    contact_email        VARCHAR(255)  NULL COMMENT '联系邮箱',
    create_by            BIGINT        NULL COMMENT '创建人ID',
    create_name          VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept          BIGINT        NULL COMMENT '创建部门ID',
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by            BIGINT        NULL COMMENT '更新人ID',
    update_name          VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time          TIMESTAMP     NULL DEFAULT NULL COMMENT '更新时间',
    del_flag             SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark               VARCHAR(500)  NULL COMMENT '备注',
    UNIQUE KEY uk_open_app_key (app_key),
    KEY idx_open_app_tenant_name (tenant_id, app_name, del_flag),
    KEY idx_open_app_tenant_status (tenant_id, status, del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放平台应用';

-- MySQL 8.0.36 不支持 ADD COLUMN IF NOT EXISTS；用 information_schema 做幂等判断。
SET @open_school_scope_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_app'
      AND column_name = 'school_scope'
);
SET @open_school_scope_sql := IF(
    @open_school_scope_exists = 0,
    'ALTER TABLE open_app ADD COLUMN school_scope VARCHAR(2000) NULL COMMENT ''开放目录授权学校ID，逗号分隔''',
    'SELECT 1'
);
PREPARE open_school_scope_stmt FROM @open_school_scope_sql;
EXECUTE open_school_scope_stmt;
DEALLOCATE PREPARE open_school_scope_stmt;

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
  (5, 0, '0', '开放平台', 'M', 'open', NULL, NULL, 'platform', 5, 0, 0),
  (500, 5, '0,5', '应用管理', 'C', 'app', 'open/app/index', 'open:app:list', 'grid', 1, 0, 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
  icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, perms, icon, sort, visible, status)
VALUES
  (5001, 500, '0,5,500', '应用查询', 'F', 'open:app:query', '#', 1, 0, 0),
  (5002, 500, '0,5,500', '应用新增', 'F', 'open:app:add', '#', 2, 0, 0),
  (5003, 500, '0,5,500', '应用修改', 'F', 'open:app:edit', '#', 3, 0, 0),
  (5004, 500, '0,5,500', '应用删除', 'F', 'open:app:remove', '#', 4, 0, 0),
  (5005, 500, '0,5,500', '重置应用密钥', 'F', 'open:app:resetSecret', '#', 5, 0, 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), perms = VALUES(perms), icon = VALUES(icon), sort = VALUES(sort),
  visible = VALUES(visible), status = VALUES(status);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (5, 500, 5001, 5002, 5003, 5004, 5005)
WHERE role.role_key = 'admin' AND role.del_flag = 0;

COMMIT;
