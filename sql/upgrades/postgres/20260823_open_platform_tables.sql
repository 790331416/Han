-- =============================================================
-- 20260823: 开放平台厂商/授权/审批/凭证/调测与接口目录表（PostgreSQL）
-- 背景：MySQL sdfz 侧已新增 open_api_resource / open_vendor 等 9 张表，
--       并为 open_app 补齐 school_scope / vendor_id / lifecycle_status /
--       environment_policy 4 列。PG 侧 clean init（medium/full tier init）
--       与存量升级必须结构一致。
-- 幂等：可重复执行。
--       CREATE TABLE IF NOT EXISTS / ADD COLUMN IF NOT EXISTS /
--       ON CONFLICT (id) DO NOTHING。
-- 说明：先以 CREATE TABLE IF NOT EXISTS 完整补齐 open_app 与
--       open_user_authorization（结构同 medium/full tier init），
--       保证 legacy 存量库（可能无 open_app）也能落表。
-- 回滚：
--   DROP TABLE IF EXISTS open_api_test_run, open_app_credential,
--     open_authorization_request, open_app_resource_grant,
--     open_vendor_application, open_vendor_user, open_vendor,
--     open_api_resource_version, open_api_resource CASCADE;
--   ALTER TABLE open_app DROP COLUMN IF EXISTS environment_policy,
--     DROP COLUMN IF EXISTS lifecycle_status,
--     DROP COLUMN IF EXISTS vendor_id,
--     DROP COLUMN IF EXISTS school_scope;
-- =============================================================

-- ============================================================
-- 0. 开放平台核心表（存量库补齐，结构同 medium/full tier init）
-- ============================================================
CREATE TABLE IF NOT EXISTS open_app (
    id BIGINT NOT NULL PRIMARY KEY,
    vendor_id BIGINT,
    tenant_id BIGINT,
    app_name VARCHAR(100) NOT NULL,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    app_secret VARCHAR(200) NOT NULL,
    app_icon VARCHAR(500),
    app_desc VARCHAR(500),
    app_type VARCHAR(20),
    redirect_uris TEXT,
    logout_uri VARCHAR(500),
    scopes VARCHAR(500),
    school_scope VARCHAR(2000),
    grant_types VARCHAR(200),
    access_token_ttl INT,
    refresh_token_ttl INT,
    require_pkce INT DEFAULT 0,
    auto_approve INT DEFAULT 0,
    status INT DEFAULT 0,
    lifecycle_status SMALLINT NOT NULL DEFAULT 0,
    environment_policy VARCHAR(20) NOT NULL DEFAULT 'SANDBOX_FIRST',
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

COMMENT ON TABLE open_app IS 'Open platform application';

CREATE TABLE IF NOT EXISTS open_user_authorization (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    app_id BIGINT,
    app_key VARCHAR(100),
    scopes VARCHAR(500),
    authorize_time TIMESTAMP,
    last_access_time TIMESTAMP,
    status INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE open_user_authorization IS 'User authorization record';

-- ==============================================
-- 1. 开放接口授权目录（全局目录）
-- ==============================================
CREATE TABLE IF NOT EXISTS open_api_resource (
    id BIGINT NOT NULL PRIMARY KEY,
    resource_code VARCHAR(100) NOT NULL,
    resource_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    path VARCHAR(255) NOT NULL,
    scope_code VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sensitivity VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status SMALLINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    publish_status SMALLINT NOT NULL DEFAULT 0,
    allow_apply SMALLINT NOT NULL DEFAULT 1,
    allow_test SMALLINT NOT NULL DEFAULT 1,
    owner VARCHAR(50),
    CONSTRAINT uk_open_api_resource_code UNIQUE (resource_code),
    CONSTRAINT uk_open_api_resource_path UNIQUE (http_method, path)
);

COMMENT ON TABLE open_api_resource IS '开放接口授权目录';

-- ==============================================
-- 2. 开放接口资源版本表（全局表）
-- ==============================================
CREATE TABLE IF NOT EXISTS open_api_resource_version (
    id BIGINT NOT NULL PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    version VARCHAR(20) NOT NULL,
    openapi_json TEXT,
    request_example_json TEXT,
    response_examples_json TEXT,
    error_examples_json TEXT,
    auth_config_json TEXT,
    sandbox_config_json TEXT,
    status SMALLINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    deprecated_at TIMESTAMP,
    create_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_open_api_resource_version UNIQUE (resource_id, version)
);

COMMENT ON TABLE open_api_resource_version IS '开放接口资源版本表';

-- ==============================================
-- 3. 厂商主体表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    qualification_no VARCHAR(50),
    industry VARCHAR(50),
    contact_name VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    contact_email VARCHAR(100),
    website VARCHAR(255),
    status SMALLINT NOT NULL DEFAULT 0,
    review_info TEXT,
    apply_time TIMESTAMP,
    review_time TIMESTAMP,
    reviewer_id BIGINT,
    create_by BIGINT,
    create_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_name VARCHAR(50),
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uk_open_vendor_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT uk_open_vendor_qualification UNIQUE (tenant_id, qualification_no)
);

COMMENT ON TABLE open_vendor IS '厂商主体表';

-- ==============================================
-- 4. 厂商用户关联表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor_user (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_open_vendor_user UNIQUE (tenant_id, vendor_id, user_id)
);

COMMENT ON TABLE open_vendor_user IS '厂商用户关联表';

-- ==============================================
-- 5. 厂商入驻申请表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor_application (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    application_no VARCHAR(32) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    apply_data TEXT,
    reason VARCHAR(500),
    reviewer_id BIGINT,
    review_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_name VARCHAR(50),
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uk_open_vendor_application_no UNIQUE (application_no)
);

COMMENT ON TABLE open_vendor_application IS '厂商入驻申请表';

-- ==============================================
-- 6. 应用-接口授权关系表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_app_resource_grant (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    version_id BIGINT,
    environment VARCHAR(20) NOT NULL,
    scopes VARCHAR(500) NOT NULL,
    data_scope TEXT,
    quota BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    status SMALLINT NOT NULL DEFAULT 0,
    apply_reason VARCHAR(500),
    review_reason VARCHAR(500),
    reviewer_id BIGINT,
    review_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_name VARCHAR(50),
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uk_open_app_resource_grant UNIQUE (tenant_id, app_id, resource_id, environment)
);

COMMENT ON TABLE open_app_resource_grant IS '应用-接口授权关系表';

-- ==============================================
-- 7. 授权申请/变更审批表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_authorization_request (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    grant_id BIGINT,
    environment VARCHAR(20) NOT NULL,
    request_type SMALLINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    request_data TEXT,
    reason VARCHAR(500),
    review_reason VARCHAR(500),
    applicant_id BIGINT NOT NULL,
    reviewer_id BIGINT,
    review_time TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_name VARCHAR(50),
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_open_authorization_request_app
    ON open_authorization_request (app_id, status);

COMMENT ON TABLE open_authorization_request IS '授权申请/变更审批表';

-- ==============================================
-- 8. 应用分环境凭证表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_app_credential (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_secret_hash VARCHAR(255) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    rotated_at TIMESTAMP,
    expire_at TIMESTAMP,
    create_by BIGINT,
    create_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT,
    update_name VARCHAR(50),
    update_time TIMESTAMP,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uk_open_app_credential_client UNIQUE (client_id)
);

COMMENT ON TABLE open_app_credential IS '应用分环境凭证表';

-- ==============================================
-- 9. 在线调测审计表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_api_test_run (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vendor_id BIGINT NOT NULL,
    app_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    environment VARCHAR(20) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    status_code INTEGER NOT NULL,
    result VARCHAR(20) NOT NULL,
    trace_id VARCHAR(64),
    duration_ms INTEGER NOT NULL,
    redacted_summary VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_open_api_test_run_app
    ON open_api_test_run (app_id, create_time);
CREATE INDEX IF NOT EXISTS idx_open_api_test_run_resource
    ON open_api_test_run (resource_id, create_time);

COMMENT ON TABLE open_api_test_run IS '在线调测审计表';

-- ==============================================
-- 10. open_app 补齐开放平台新增列（幂等；表已由第 0 步保证存在）
-- ==============================================
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS school_scope VARCHAR(2000);
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS vendor_id BIGINT;
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS lifecycle_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE open_app ADD COLUMN IF NOT EXISTS environment_policy VARCHAR(20) NOT NULL DEFAULT 'SANDBOX_FIRST';

-- ==============================================
-- 11. 接口目录种子（幂等）
-- ==============================================
INSERT INTO open_api_resource
    (id, resource_code, resource_name, category, http_method, path, scope_code, description, sensitivity, status, sort)
VALUES
  (1, 'directory.teachers.read', '教师目录', '教育目录', 'GET', '/open/api/v1/directory/teachers', 'edu.teacher.read', '查询授权学校的教师目录', 'NORMAL', 0, 10),
  (2, 'directory.students.read', '学生目录', '教育目录', 'GET', '/open/api/v1/directory/students', 'edu.student.read', '查询授权学校的学生目录', 'NORMAL', 0, 20),
  (3, 'directory.devices.read', '设备目录', '教育目录', 'GET', '/open/api/v1/directory/devices', 'edu.device.read', '查询授权学校的设备目录', 'NORMAL', 0, 30)
ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- source: sdfz/mysql/20260823_open_vendor_portal.sql (PostgreSQL)
-- ============================================================
-- 厂商门户角色与最小自服务权限；不授予厂商审核/管理员权限。
DO $$
BEGIN
  -- legacy_synthetic 只提供旧 sys_role/sys_menu 列，跳过门户 DML 但继续演练开放表。
  IF to_regclass('public.sys_role') IS NOT NULL
     AND to_regclass('public.sys_menu') IS NOT NULL
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'sys_role' AND column_name = 'tenant_id')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'sys_role' AND column_name = 'role_name')
     AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'sys_menu' AND column_name = 'menu_type') THEN
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
SELECT 202608230001, 1, '开放平台厂商', 'openVendor', 90, '5', 0, '开放平台厂商门户角色'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role
    WHERE (id = 202608230001 OR (tenant_id = 1 AND role_key = 'openVendor'))
);

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES
    (5, 0, '0', '开放平台', 'M', 'open', NULL, NULL, 'platform', 5, 0, 0),
    (202608230010, 5, '0,5', '厂商门户', 'C', 'portal', 'open/portal/index', 'open:vendor:my', 'link', 1, 0, 0),
    (202608230101, 202608230010, '0,5,202608230010', '我的厂商', 'F', NULL, NULL, 'open:vendor:my', '#', 1, 0, 0),
    (202608230102, 202608230010, '0,5,202608230010', '厂商申请', 'F', NULL, NULL, 'open:vendor:apply', '#', 2, 0, 0),
    (202608230103, 202608230010, '0,5,202608230010', '厂商查询', 'F', NULL, NULL, 'open:vendor:query', '#', 3, 0, 0),
    (202608230201, 202608230010, '0,5,202608230010', '应用列表', 'F', NULL, NULL, 'open:app:list', '#', 4, 0, 0),
    (202608230202, 202608230010, '0,5,202608230010', '应用查询', 'F', NULL, NULL, 'open:app:query', '#', 5, 0, 0),
    (202608230203, 202608230010, '0,5,202608230010', '应用新增', 'F', NULL, NULL, 'open:app:add', '#', 6, 0, 0),
    (202608230204, 202608230010, '0,5,202608230010', '应用修改', 'F', NULL, NULL, 'open:app:edit', '#', 7, 0, 0),
    (202608230205, 202608230010, '0,5,202608230010', '应用删除', 'F', NULL, NULL, 'open:app:remove', '#', 8, 0, 0),
    (202608230301, 202608230010, '0,5,202608230010', '授权申请', 'F', NULL, NULL, 'open:grant:apply', '#', 9, 0, 0),
    (202608230302, 202608230010, '0,5,202608230010', '授权查询', 'F', NULL, NULL, 'open:grant:query', '#', 10, 0, 0),
    (202608230303, 202608230010, '0,5,202608230010', '授权撤销', 'F', NULL, NULL, 'open:grant:revoke', '#', 11, 0, 0),
    (202608230401, 202608230010, '0,5,202608230010', '凭证查询', 'F', NULL, NULL, 'open:credential:query', '#', 12, 0, 0),
    (202608230402, 202608230010, '0,5,202608230010', '凭证管理', 'F', NULL, NULL, 'open:credential:manage', '#', 13, 0, 0),
    (202608230501, 202608230010, '0,5,202608230010', '接口查询', 'F', NULL, NULL, 'open:api-resource:query', '#', 14, 0, 0),
    (202608230502, 202608230010, '0,5,202608230010', '接口列表', 'F', NULL, NULL, 'open:api-resource:list', '#', 15, 0, 0)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id, ancestors = EXCLUDED.ancestors,
    menu_name = EXCLUDED.menu_name, menu_type = EXCLUDED.menu_type,
    path = EXCLUDED.path, component = EXCLUDED.component, perms = EXCLUDED.perms,
    icon = EXCLUDED.icon, sort = EXCLUDED.sort, visible = EXCLUDED.visible,
    status = EXCLUDED.status;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (
    5, 202608230010, 202608230101, 202608230102, 202608230103,
    202608230201, 202608230202, 202608230203, 202608230204, 202608230205,
    202608230301, 202608230302, 202608230303,
    202608230401, 202608230402, 202608230501, 202608230502
)
WHERE role.tenant_id = 1 AND role.role_key = 'openVendor'
ON CONFLICT DO NOTHING;
  END IF;
END $$;
