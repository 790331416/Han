-- 开放平台厂商、授权、审批、凭证核心表。
-- DDL 逐条幂等：加列用 information_schema 判断；MySQL DDL 隐式提交，本脚本无事务回滚，重复执行安全。
-- 注意：CREATE TABLE IF NOT EXISTS 无法修复已按旧结构建成的半成品表；上线前必须做结构回读，发现旧结构时使用单独的一次性修正脚本（本脚本不负责迁移旧半成品表）。
SET NAMES utf8mb4;

-- ==============================================
-- 1. 厂商主体表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    name                VARCHAR(100)  NOT NULL COMMENT '厂商名称',
    qualification_no    VARCHAR(50)   NULL COMMENT '统一社会信用代码',
    industry            VARCHAR(50)   NULL COMMENT '所属行业',
    contact_name        VARCHAR(50)   NOT NULL COMMENT '联系人姓名',
    contact_phone       VARCHAR(20)   NOT NULL COMMENT '联系电话',
    contact_email       VARCHAR(100)  NULL COMMENT '联系邮箱',
    website             VARCHAR(255)  NULL COMMENT '官网地址',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0待提交 1待验证 2待审核 3补充材料 4审核通过 5审核驳回 6暂停 7注销',
    review_info         TEXT          NULL COMMENT '审核信息/驳回原因',
    apply_time          DATETIME      NULL COMMENT '申请时间',
    review_time         DATETIME      NULL COMMENT '审核时间',
    reviewer_id         BIGINT        NULL COMMENT '审核人ID',
    create_by           BIGINT        NULL COMMENT '创建人',
    create_name         VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept         BIGINT        NULL COMMENT '创建部门ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT        NULL COMMENT '更新人',
    update_name         VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark              VARCHAR(500)  NULL COMMENT '备注',
    UNIQUE KEY uk_open_vendor_tenant_name(tenant_id, name),
    UNIQUE KEY uk_open_vendor_qualification(tenant_id, qualification_no),
    KEY idx_open_vendor_status(status, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='厂商主体表';

-- ==============================================
-- 2. 厂商用户关联表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor_user (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    vendor_id           BIGINT        NOT NULL COMMENT '厂商ID',
    user_id             BIGINT        NOT NULL COMMENT '用户ID（关联sys_user）',
    role                VARCHAR(30)   NOT NULL COMMENT '角色：OWNER所有者、DEVELOPER开发者、VIEWER查看者',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0正常 1停用',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    UNIQUE KEY uk_open_vendor_user(tenant_id, vendor_id, user_id),
    KEY idx_open_vendor_user_user(user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='厂商用户关联表';

-- ==============================================
-- 3. 厂商入驻申请表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_vendor_application (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    vendor_id           BIGINT        NOT NULL COMMENT '关联厂商ID',
    applicant_user_id   BIGINT        NOT NULL COMMENT '申请人用户ID',
    application_no      VARCHAR(32)   NOT NULL COMMENT '申请编号',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0待提交 1待审核 2审核通过 3审核驳回',
    apply_data          TEXT          NULL COMMENT '申请数据快照',
    reason              VARCHAR(500)  NULL COMMENT '审核原因/驳回说明',
    reviewer_id         BIGINT        NULL COMMENT '审核人ID',
    review_time         DATETIME      NULL COMMENT '审核时间',
    create_by           BIGINT        NULL COMMENT '创建人',
    create_name         VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept         BIGINT        NULL COMMENT '创建部门ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT        NULL COMMENT '更新人',
    update_name         VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark              VARCHAR(500)  NULL COMMENT '备注',
    UNIQUE KEY uk_open_vendor_application_no(application_no),
    KEY idx_open_vendor_application_vendor(vendor_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='厂商入驻申请表';

-- ==============================================
-- 4. 扩展open_app表，新增厂商关联字段（幂等，不存在时才添加）
-- ==============================================
SET @open_app_vendor_id_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_app'
      AND column_name = 'vendor_id'
);
SET @open_app_vendor_id_sql := IF(
    @open_app_vendor_id_exists = 0,
    'ALTER TABLE open_app ADD COLUMN vendor_id BIGINT NULL COMMENT ''厂商ID'' AFTER id',
    'SELECT 1'
);
PREPARE open_app_vendor_id_stmt FROM @open_app_vendor_id_sql;
EXECUTE open_app_vendor_id_stmt;
DEALLOCATE PREPARE open_app_vendor_id_stmt;

SET @open_app_lifecycle_status_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_app'
      AND column_name = 'lifecycle_status'
);
SET @open_app_lifecycle_status_sql := IF(
    @open_app_lifecycle_status_exists = 0,
    'ALTER TABLE open_app ADD COLUMN lifecycle_status SMALLINT NOT NULL DEFAULT 0 COMMENT ''生命周期状态：0草稿 1待审核 2沙箱已开通 3调测中 4生产待审核 5生产已开通 6暂停 7撤销'' AFTER status',
    'SELECT 1'
);
PREPARE open_app_lifecycle_status_stmt FROM @open_app_lifecycle_status_sql;
EXECUTE open_app_lifecycle_status_stmt;
DEALLOCATE PREPARE open_app_lifecycle_status_stmt;

SET @open_app_environment_policy_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'open_app'
      AND column_name = 'environment_policy'
);
SET @open_app_environment_policy_sql := IF(
    @open_app_environment_policy_exists = 0,
    'ALTER TABLE open_app ADD COLUMN environment_policy VARCHAR(20) NOT NULL DEFAULT ''SANDBOX_FIRST'' COMMENT ''环境策略：SANDBOX_FIRST仅沙箱、PROD_ONLY仅生产、ALL所有环境'' AFTER lifecycle_status',
    'SELECT 1'
);
PREPARE open_app_environment_policy_stmt FROM @open_app_environment_policy_sql;
EXECUTE open_app_environment_policy_stmt;
DEALLOCATE PREPARE open_app_environment_policy_stmt;

-- ==============================================
-- 5. 应用-接口授权关系表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_app_resource_grant (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    app_id              BIGINT        NOT NULL COMMENT '应用ID',
    resource_id         BIGINT        NOT NULL COMMENT '资源ID',
    version_id          BIGINT        NULL COMMENT '资源版本ID，空表示最新版本',
    environment         VARCHAR(20)   NOT NULL COMMENT '环境：SANDBOX沙箱、PROD生产',
    scopes              VARCHAR(500)  NOT NULL COMMENT '授权Scope列表，逗号分隔',
    data_scope          TEXT          NULL COMMENT '数据范围配置：学校、字段、脱敏级别等',
    quota               BIGINT        NOT NULL DEFAULT 0 COMMENT '调用配额，0表示不限制',
    expires_at          DATETIME      NULL COMMENT '过期时间，空表示永久有效',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已生效 2已驳回 3已过期 4已撤销',
    apply_reason        VARCHAR(500)  NULL COMMENT '申请理由',
    review_reason       VARCHAR(500)  NULL COMMENT '审核原因',
    reviewer_id         BIGINT        NULL COMMENT '审核人ID',
    review_time         DATETIME      NULL COMMENT '审核时间',
    create_by           BIGINT        NULL COMMENT '创建人',
    create_name         VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept         BIGINT        NULL COMMENT '创建部门ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT        NULL COMMENT '更新人',
    update_name         VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark              VARCHAR(500)  NULL COMMENT '备注',
    UNIQUE KEY uk_open_app_resource_grant(tenant_id, app_id, resource_id, environment),
    KEY idx_open_app_resource_grant_status(status, app_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用-接口授权关系表';

-- ==============================================
-- 6. 授权申请/变更审批表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_authorization_request (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    app_id              BIGINT        NOT NULL COMMENT '应用ID',
    grant_id            BIGINT        NULL COMMENT '关联授权ID，新增申请为空',
    environment         VARCHAR(20)   NOT NULL COMMENT '环境：SANDBOX沙箱、PROD生产',
    request_type        SMALLINT      NOT NULL DEFAULT 0 COMMENT '请求类型：0新增授权 1变更授权 2撤销授权',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已通过 2已驳回 3已撤销',
    request_data        TEXT          NULL COMMENT '申请数据快照',
    reason              VARCHAR(500)  NULL COMMENT '申请理由',
    review_reason       VARCHAR(500)  NULL COMMENT '审核原因',
    applicant_id        BIGINT        NOT NULL COMMENT '申请人ID',
    reviewer_id         BIGINT        NULL COMMENT '审核人ID',
    review_time         DATETIME      NULL COMMENT '审核时间',
    create_by           BIGINT        NULL COMMENT '创建人',
    create_name         VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept         BIGINT        NULL COMMENT '创建部门ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT        NULL COMMENT '更新人',
    update_name         VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark              VARCHAR(500)  NULL COMMENT '备注',
    KEY idx_open_authorization_request_app(app_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='授权申请/变更审批表';

-- ==============================================
-- 7. 应用分环境凭证表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_app_credential (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    app_id              BIGINT        NOT NULL COMMENT '应用ID',
    environment         VARCHAR(20)   NOT NULL COMMENT '环境：SANDBOX沙箱、PROD生产',
    client_id           VARCHAR(100)  NOT NULL COMMENT '客户端ID',
    client_secret_hash  VARCHAR(255)  NOT NULL COMMENT '客户端密钥哈希',
    status              SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0正常 1停用 2已轮换',
    rotated_at          DATETIME      NULL COMMENT '轮换时间',
    expire_at           DATETIME      NULL COMMENT '过期时间',
    create_by           BIGINT        NULL COMMENT '创建人',
    create_name         VARCHAR(100)  NULL COMMENT '创建人名称',
    create_dept         BIGINT        NULL COMMENT '创建部门ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT        NULL COMMENT '更新人',
    update_name         VARCHAR(100)  NULL COMMENT '更新人名称',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            SMALLINT      NOT NULL DEFAULT 0 COMMENT '删除标志：0存在，1删除',
    remark              VARCHAR(500)  NULL COMMENT '备注',
    UNIQUE KEY uk_open_app_credential_client(client_id),
    KEY idx_open_app_credential_app(app_id, environment, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用分环境凭证表';

-- ==============================================
-- 8. 在线调测审计表
-- ==============================================
CREATE TABLE IF NOT EXISTS open_api_test_run (
    id                  BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT        NOT NULL COMMENT '租户ID',
    vendor_id           BIGINT        NOT NULL COMMENT '厂商ID',
    app_id              BIGINT        NOT NULL COMMENT '应用ID',
    resource_id         BIGINT        NOT NULL COMMENT '资源ID',
    environment         VARCHAR(20)   NOT NULL COMMENT '环境：SANDBOX沙箱、PROD生产',
    request_method      VARCHAR(10)   NOT NULL COMMENT '请求方法',
    request_path        VARCHAR(255)  NOT NULL COMMENT '请求路径',
    status_code         INTEGER       NOT NULL COMMENT '响应状态码',
    result              VARCHAR(20)   NOT NULL COMMENT '结果：SUCCESS成功、FAIL失败',
    trace_id            VARCHAR(64)   NULL COMMENT '链路ID',
    duration_ms         INTEGER       NOT NULL COMMENT '耗时（毫秒）',
    redacted_summary    VARCHAR(500)  NULL COMMENT '脱敏摘要',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_open_api_test_run_app(app_id, create_time),
    KEY idx_open_api_test_run_resource(resource_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线调测审计表';
