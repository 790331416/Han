-- =============================================
-- Han Cloud 工作流模块数据库脚本
-- 数据库：MySQL 8.0+
-- 说明：Flowable会自动创建工作流引擎表(ACT_*)
--       此脚本仅创建业务扩展表
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 流程分类表
-- ----------------------------
DROP TABLE IF EXISTS wf_category;
CREATE TABLE wf_category (
    id              BIGINT          NOT NULL                    COMMENT '分类ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    category_code   VARCHAR(50)     NOT NULL                    COMMENT '分类编码',
    category_name   VARCHAR(100)    NOT NULL                    COMMENT '分类名称',
    parent_id       BIGINT          DEFAULT 0                   COMMENT '父分类ID',
    ancestors       VARCHAR(500)    DEFAULT ''                  COMMENT '祖级列表',
    sort            INT             DEFAULT 0                   COMMENT '显示顺序',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_category_code (category_code, tenant_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程分类表';

-- ----------------------------
-- 2. 流程表单表
-- ----------------------------
DROP TABLE IF EXISTS wf_form;
CREATE TABLE wf_form (
    id              BIGINT          NOT NULL                    COMMENT '表单ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    form_name       VARCHAR(100)    NOT NULL                    COMMENT '表单名称',
    form_key        VARCHAR(64)     NOT NULL                    COMMENT '表单Key',
    form_type       VARCHAR(20)     DEFAULT 'custom'            COMMENT '表单类型(custom自定义/external外部)',
    form_content    LONGTEXT        DEFAULT NULL                COMMENT '表单内容(JSON)',
    external_url    VARCHAR(500)    DEFAULT NULL                COMMENT '外部表单URL',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
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
    UNIQUE INDEX uk_form_key (form_key, tenant_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程表单表';

-- ----------------------------
-- 3. 流程定义扩展表(补充Flowable流程定义信息)
-- ----------------------------
DROP TABLE IF EXISTS wf_deploy_extend;
CREATE TABLE wf_deploy_extend (
    id              BIGINT          NOT NULL                    COMMENT '主键',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    deployment_id   VARCHAR(64)     NOT NULL                    COMMENT 'Flowable部署ID',
    process_key     VARCHAR(64)     NOT NULL                    COMMENT '流程Key',
    process_name    VARCHAR(200)    DEFAULT NULL                COMMENT '流程名称',
    category_id     BIGINT          DEFAULT NULL                COMMENT '分类ID',
    form_id         BIGINT          DEFAULT NULL                COMMENT '表单ID',
    icon            VARCHAR(500)    DEFAULT NULL                COMMENT '流程图标',
    sort            INT             DEFAULT 0                   COMMENT '排序',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_deployment_id (deployment_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_process_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义扩展表';

-- ----------------------------
-- 4. 流程实例扩展表(补充业务信息)
-- ----------------------------
DROP TABLE IF EXISTS wf_instance_extend;
CREATE TABLE wf_instance_extend (
    id                  BIGINT          NOT NULL                COMMENT '主键',
    tenant_id           BIGINT          NOT NULL                COMMENT '租户ID',
    process_instance_id VARCHAR(64)     NOT NULL                COMMENT '流程实例ID',
    process_key         VARCHAR(64)     NOT NULL                COMMENT '流程Key',
    process_name        VARCHAR(200)    DEFAULT NULL            COMMENT '流程名称',
    title               VARCHAR(200)    DEFAULT NULL            COMMENT '流程标题',
    business_key        VARCHAR(64)     DEFAULT NULL            COMMENT '业务Key',
    business_table      VARCHAR(100)    DEFAULT NULL            COMMENT '业务表名',
    category_id         BIGINT          DEFAULT NULL            COMMENT '分类ID',
    start_user_id       BIGINT          DEFAULT NULL            COMMENT '发起人ID',
    start_user_name     VARCHAR(50)     DEFAULT NULL            COMMENT '发起人名称',
    start_dept_id       BIGINT          DEFAULT NULL            COMMENT '发起部门ID',
    start_dept_name     VARCHAR(100)    DEFAULT NULL            COMMENT '发起部门名称',
    current_task_name   VARCHAR(200)    DEFAULT NULL            COMMENT '当前节点名称',
    current_assignee    VARCHAR(200)    DEFAULT NULL            COMMENT '当前审批人',
    status              TINYINT         DEFAULT 0               COMMENT '状态(0进行中 1已完成 2已取消)',
    result              TINYINT         DEFAULT NULL            COMMENT '审批结果(1通过 2驳回)',
    start_time          DATETIME        DEFAULT NULL            COMMENT '开始时间',
    end_time            DATETIME        DEFAULT NULL            COMMENT '结束时间',
    duration            BIGINT          DEFAULT NULL            COMMENT '耗时(毫秒)',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag            TINYINT         DEFAULT 0               COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_process_instance (process_instance_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_business_key (business_key),
    INDEX idx_start_user_id (start_user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例扩展表';

-- ----------------------------
-- 5. 流程抄送表
-- ----------------------------
DROP TABLE IF EXISTS wf_copy;
CREATE TABLE wf_copy (
    id                  BIGINT          NOT NULL                COMMENT '主键',
    tenant_id           BIGINT          NOT NULL                COMMENT '租户ID',
    process_instance_id VARCHAR(64)     NOT NULL                COMMENT '流程实例ID',
    task_id             VARCHAR(64)     DEFAULT NULL            COMMENT '任务ID',
    task_name           VARCHAR(200)    DEFAULT NULL            COMMENT '任务名称',
    title               VARCHAR(200)    DEFAULT NULL            COMMENT '流程标题',
    user_id             BIGINT          NOT NULL                COMMENT '抄送人ID',
    user_name           VARCHAR(50)     DEFAULT NULL            COMMENT '抄送人名称',
    origin_user_id      BIGINT          DEFAULT NULL            COMMENT '发起抄送人ID',
    origin_user_name    VARCHAR(50)     DEFAULT NULL            COMMENT '发起抄送人名称',
    is_read             TINYINT         DEFAULT 0               COMMENT '是否已读(0否 1是)',
    read_time           DATETIME        DEFAULT NULL            COMMENT '阅读时间',
    create_time         DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    del_flag            TINYINT         DEFAULT 0               COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id),
    INDEX idx_process_instance (process_instance_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程抄送表';

-- ----------------------------
-- 初始化流程分类
-- ----------------------------
INSERT INTO wf_category (id, tenant_id, category_code, category_name, parent_id, sort, status) VALUES
(1, 1, 'oa', 'OA办公', 0, 1, 0),
(2, 1, 'hr', '人事管理', 0, 2, 0),
(3, 1, 'finance', '财务管理', 0, 3, 0),
(101, 1, 'leave', '请假申请', 1, 1, 0),
(102, 1, 'expense', '报销申请', 1, 2, 0),
(103, 1, 'business_trip', '出差申请', 1, 3, 0),
(201, 1, 'entry', '入职申请', 2, 1, 0),
(202, 1, 'resign', '离职申请', 2, 2, 0),
(301, 1, 'payment', '付款申请', 3, 1, 0),
(302, 1, 'invoice', '发票申请', 3, 2, 0);

SET FOREIGN_KEY_CHECKS = 1;
