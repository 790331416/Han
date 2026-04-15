-- =============================================
-- HAN Cloud 开放平台模块数据库脚本
-- 数据库：MySQL 8.0+
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 开放平台应用表
-- ----------------------------
DROP TABLE IF EXISTS open_app;
CREATE TABLE open_app (
    id              BIGINT          NOT NULL                    COMMENT '应用ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    app_name        VARCHAR(100)    NOT NULL                    COMMENT '应用名称',
    app_key         VARCHAR(64)     NOT NULL                    COMMENT '应用Key(Client ID)',
    app_secret      VARCHAR(128)    NOT NULL                    COMMENT '应用密钥(Client Secret)',
    app_icon        VARCHAR(500)    DEFAULT NULL                COMMENT '应用图标',
    app_desc        VARCHAR(500)    DEFAULT NULL                COMMENT '应用描述',
    app_type        VARCHAR(20)     DEFAULT 'web'               COMMENT '应用类型(web/native/spa)',
    redirect_uris   TEXT            DEFAULT NULL                COMMENT '授权回调地址(多个用逗号分隔)',
    logout_uri      VARCHAR(500)    DEFAULT NULL                COMMENT '登出回调地址',
    scopes          VARCHAR(500)    DEFAULT 'openid,profile'    COMMENT '授权范围',
    grant_types     VARCHAR(200)    DEFAULT 'authorization_code,refresh_token' COMMENT '授权类型',
    access_token_ttl INT            DEFAULT 3600                COMMENT 'AccessToken有效期(秒)',
    refresh_token_ttl INT           DEFAULT 604800              COMMENT 'RefreshToken有效期(秒)',
    require_pkce    TINYINT         DEFAULT 0                   COMMENT '是否启用PKCE(0否 1是)',
    auto_approve    TINYINT         DEFAULT 0                   COMMENT '是否自动授权(0否 1是)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    contact_name    VARCHAR(50)     DEFAULT NULL                COMMENT '联系人',
    contact_phone   VARCHAR(20)     DEFAULT NULL                COMMENT '联系电话',
    contact_email   VARCHAR(100)    DEFAULT NULL                COMMENT '联系邮箱',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_app_key (app_key),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放平台应用表';

-- ----------------------------
-- 2. 用户授权记录表
-- ----------------------------
DROP TABLE IF EXISTS open_user_authorization;
CREATE TABLE open_user_authorization (
    id              BIGINT          NOT NULL                    COMMENT '授权ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    app_id          BIGINT          NOT NULL                    COMMENT '应用ID',
    app_key         VARCHAR(64)     NOT NULL                    COMMENT '应用Key',
    scopes          VARCHAR(500)    DEFAULT NULL                COMMENT '授权范围',
    authorize_time  DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '授权时间',
    last_access_time DATETIME       DEFAULT NULL                COMMENT '最后访问时间',
    status          TINYINT         DEFAULT 0                   COMMENT '授权状态(0有效 1已撤销)',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_app (user_id, app_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_app_id (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户授权记录表';

-- ----------------------------
-- 3. OAuth2授权码表(临时存储)
-- ----------------------------
DROP TABLE IF EXISTS open_authorization_code;
CREATE TABLE open_authorization_code (
    id              BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '主键',
    code            VARCHAR(128)    NOT NULL                    COMMENT '授权码',
    client_id       VARCHAR(64)     NOT NULL                    COMMENT '客户端ID',
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    redirect_uri    VARCHAR(500)    NOT NULL                    COMMENT '重定向URI',
    scopes          VARCHAR(500)    DEFAULT NULL                COMMENT '授权范围',
    code_challenge  VARCHAR(256)    DEFAULT NULL                COMMENT 'PKCE code_challenge',
    code_challenge_method VARCHAR(10) DEFAULT NULL              COMMENT 'PKCE方法(S256/plain)',
    state           VARCHAR(256)    DEFAULT NULL                COMMENT '状态参数',
    nonce           VARCHAR(256)    DEFAULT NULL                COMMENT 'Nonce',
    expire_time     DATETIME        NOT NULL                    COMMENT '过期时间',
    used            TINYINT         DEFAULT 0                   COMMENT '是否已使用(0否 1是)',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_code (code),
    INDEX idx_client_id (client_id),
    INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权码表';

-- ----------------------------
-- 4. OAuth2访问令牌表
-- ----------------------------
DROP TABLE IF EXISTS open_access_token;
CREATE TABLE open_access_token (
    id              BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '主键',
    access_token    VARCHAR(256)    NOT NULL                    COMMENT '访问令牌',
    refresh_token   VARCHAR(256)    DEFAULT NULL                COMMENT '刷新令牌',
    client_id       VARCHAR(64)     NOT NULL                    COMMENT '客户端ID',
    user_id         BIGINT          DEFAULT NULL                COMMENT '用户ID(客户端凭证模式为空)',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    scopes          VARCHAR(500)    DEFAULT NULL                COMMENT '授权范围',
    token_type      VARCHAR(20)     DEFAULT 'Bearer'            COMMENT '令牌类型',
    access_expire_time DATETIME     NOT NULL                    COMMENT 'AccessToken过期时间',
    refresh_expire_time DATETIME    DEFAULT NULL                COMMENT 'RefreshToken过期时间',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_access_token (access_token),
    INDEX idx_refresh_token (refresh_token),
    INDEX idx_client_id (client_id),
    INDEX idx_user_id (user_id),
    INDEX idx_access_expire (access_expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2访问令牌表';

-- ----------------------------
-- 5. API接口权限表
-- ----------------------------
DROP TABLE IF EXISTS open_api_scope;
CREATE TABLE open_api_scope (
    id              BIGINT          NOT NULL                    COMMENT '权限ID',
    scope_code      VARCHAR(50)     NOT NULL                    COMMENT '权限编码',
    scope_name      VARCHAR(100)    NOT NULL                    COMMENT '权限名称',
    scope_desc      VARCHAR(500)    DEFAULT NULL                COMMENT '权限描述',
    api_paths       TEXT            DEFAULT NULL                COMMENT '关联API路径(JSON数组)',
    is_default      TINYINT         DEFAULT 0                   COMMENT '是否默认授予(0否 1是)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_scope_code (scope_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API接口权限表';

-- ----------------------------
-- 初始化API权限范围
-- ----------------------------
INSERT INTO open_api_scope (id, scope_code, scope_name, scope_desc, is_default, status) VALUES
(1, 'openid', 'OpenID', '获取用户唯一标识', 1, 0),
(2, 'profile', '基本信息', '获取用户基本信息(昵称、头像等)', 1, 0),
(3, 'email', '邮箱', '获取用户邮箱', 0, 0),
(4, 'phone', '手机号', '获取用户手机号', 0, 0),
(5, 'user:read', '读取用户', '读取用户详细信息', 0, 0),
(6, 'user:write', '写入用户', '修改用户信息', 0, 0),
(7, 'dept:read', '读取部门', '读取部门信息', 0, 0),
(8, 'role:read', '读取角色', '读取角色信息', 0, 0);

SET FOREIGN_KEY_CHECKS = 1;
