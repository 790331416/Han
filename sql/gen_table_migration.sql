-- 代码生成器表结构

CREATE TABLE IF NOT EXISTS gen_table (
    id              BIGINT PRIMARY KEY,
    table_name      VARCHAR(200) NOT NULL DEFAULT '' COMMENT '表名称',
    table_comment   VARCHAR(500) DEFAULT '' COMMENT '表描述',
    package_name    VARCHAR(200) DEFAULT 'com.han.system' COMMENT '生成包路径',
    module_name     VARCHAR(50)  DEFAULT '' COMMENT '生成模块名',
    business_name   VARCHAR(50)  DEFAULT '' COMMENT '生成业务名',
    function_name   VARCHAR(50)  DEFAULT '' COMMENT '生成功能名（类名）',
    author          VARCHAR(50)  DEFAULT 'HanCloud' COMMENT '作者',
    parent_menu_id  BIGINT       DEFAULT NULL COMMENT '父菜单ID',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    DEFAULT NULL
);

COMMENT ON TABLE gen_table IS '代码生成业务表';

CREATE TABLE IF NOT EXISTS gen_table_column (
    id              BIGINT PRIMARY KEY,
    table_id        BIGINT NOT NULL COMMENT '归属表ID',
    column_name     VARCHAR(200) NOT NULL COMMENT '列名称',
    column_comment  VARCHAR(500) DEFAULT '' COMMENT '列描述',
    column_type     VARCHAR(100) DEFAULT '' COMMENT '列类型',
    java_type       VARCHAR(50)  DEFAULT 'String' COMMENT 'Java类型',
    java_field      VARCHAR(200) DEFAULT '' COMMENT 'Java字段名',
    is_pk           SMALLINT     DEFAULT 0 COMMENT '是否主键（1是）',
    is_increment    SMALLINT     DEFAULT 0 COMMENT '是否自增（1是）',
    is_required     SMALLINT     DEFAULT 0 COMMENT '是否必填（1是）',
    is_insert       SMALLINT     DEFAULT 1 COMMENT '是否为插入字段（1是）',
    is_edit         SMALLINT     DEFAULT 1 COMMENT '是否编辑字段（1是）',
    is_list         SMALLINT     DEFAULT 1 COMMENT '是否列表字段（1是）',
    is_query        SMALLINT     DEFAULT 0 COMMENT '是否查询字段（1是）',
    query_type      VARCHAR(20)  DEFAULT 'EQ' COMMENT '查询方式',
    html_type       VARCHAR(50)  DEFAULT 'input' COMMENT '显示类型',
    dict_type       VARCHAR(200) DEFAULT '' COMMENT '字典类型',
    sort            INT          DEFAULT 0 COMMENT '排序'
);

COMMENT ON TABLE gen_table_column IS '代码生成业务表字段';
CREATE INDEX IF NOT EXISTS idx_gen_table_column_table_id ON gen_table_column(table_id);
