-- =============================================
-- HAN Cloud 代码生成模块数据库脚本
-- 数据库：MySQL 8.0+
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 代码生成业务表
-- ----------------------------
DROP TABLE IF EXISTS gen_table;
CREATE TABLE gen_table (
    id              BIGINT          NOT NULL                    COMMENT '编号',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    table_name      VARCHAR(200)    DEFAULT ''                  COMMENT '表名称',
    table_comment   VARCHAR(500)    DEFAULT ''                  COMMENT '表描述',
    sub_table_name  VARCHAR(64)     DEFAULT NULL                COMMENT '关联子表的表名',
    sub_table_fk_name VARCHAR(64)   DEFAULT NULL                COMMENT '子表关联的外键名',
    class_name      VARCHAR(100)    DEFAULT ''                  COMMENT '实体类名称',
    tpl_category    VARCHAR(200)    DEFAULT 'crud'              COMMENT '使用的模板(crud单表 tree树表 sub主子表)',
    tpl_web_type    VARCHAR(30)     DEFAULT ''                  COMMENT '前端模板类型(element-ui/element-plus)',
    package_name    VARCHAR(100)    DEFAULT NULL                COMMENT '生成包路径',
    module_name     VARCHAR(30)     DEFAULT NULL                COMMENT '生成模块名',
    business_name   VARCHAR(30)     DEFAULT NULL                COMMENT '生成业务名',
    function_name   VARCHAR(50)     DEFAULT NULL                COMMENT '生成功能名',
    function_author VARCHAR(50)     DEFAULT NULL                COMMENT '生成功能作者',
    gen_type        CHAR(1)         DEFAULT '0'                 COMMENT '生成代码方式(0zip压缩包 1自定义路径)',
    gen_path        VARCHAR(200)    DEFAULT '/'                 COMMENT '生成路径(不填默认项目路径)',
    options         VARCHAR(1000)   DEFAULT NULL                COMMENT '其它生成选项',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_table_name (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成业务表';

-- ----------------------------
-- 2. 代码生成业务表字段
-- ----------------------------
DROP TABLE IF EXISTS gen_table_column;
CREATE TABLE gen_table_column (
    id              BIGINT          NOT NULL                    COMMENT '编号',
    table_id        BIGINT          NOT NULL                    COMMENT '归属表编号',
    column_name     VARCHAR(200)    DEFAULT NULL                COMMENT '列名称',
    column_comment  VARCHAR(500)    DEFAULT NULL                COMMENT '列描述',
    column_type     VARCHAR(100)    DEFAULT NULL                COMMENT '列类型',
    java_type       VARCHAR(100)    DEFAULT NULL                COMMENT 'JAVA类型',
    java_field      VARCHAR(200)    DEFAULT NULL                COMMENT 'JAVA字段名',
    is_pk           CHAR(1)         DEFAULT NULL                COMMENT '是否主键(1是)',
    is_increment    CHAR(1)         DEFAULT NULL                COMMENT '是否自增(1是)',
    is_required     CHAR(1)         DEFAULT NULL                COMMENT '是否必填(1是)',
    is_insert       CHAR(1)         DEFAULT NULL                COMMENT '是否为插入字段(1是)',
    is_edit         CHAR(1)         DEFAULT NULL                COMMENT '是否编辑字段(1是)',
    is_list         CHAR(1)         DEFAULT NULL                COMMENT '是否列表字段(1是)',
    is_query        CHAR(1)         DEFAULT NULL                COMMENT '是否查询字段(1是)',
    query_type      VARCHAR(200)    DEFAULT 'EQ'                COMMENT '查询方式(等于、不等于、大于、小于、范围)',
    html_type       VARCHAR(200)    DEFAULT NULL                COMMENT '显示类型(文本框、文本域、下拉框、复选框、单选框、日期控件)',
    dict_type       VARCHAR(200)    DEFAULT ''                  COMMENT '字典类型',
    sort            INT             DEFAULT NULL                COMMENT '排序',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_table_id (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成业务表字段';

SET FOREIGN_KEY_CHECKS = 1;
