-- PostgreSQL migration for code generator metadata tables.

CREATE TABLE IF NOT EXISTS gen_table (
    id BIGINT PRIMARY KEY,
    table_name VARCHAR(200) NOT NULL DEFAULT '',
    table_comment VARCHAR(500) DEFAULT '',
    package_name VARCHAR(200) DEFAULT 'com.han.system',
    module_name VARCHAR(50) DEFAULT '',
    business_name VARCHAR(50) DEFAULT '',
    function_name VARCHAR(50) DEFAULT '',
    author VARCHAR(50) DEFAULT 'HanCloud',
    parent_menu_id BIGINT DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE gen_table IS '代码生成业务表';
COMMENT ON COLUMN gen_table.table_name IS '表名称';
COMMENT ON COLUMN gen_table.table_comment IS '表描述';
COMMENT ON COLUMN gen_table.package_name IS '生成包路径';
COMMENT ON COLUMN gen_table.module_name IS '生成模块名';
COMMENT ON COLUMN gen_table.business_name IS '生成业务名';
COMMENT ON COLUMN gen_table.function_name IS '生成功能名（类名）';
COMMENT ON COLUMN gen_table.author IS '作者';
COMMENT ON COLUMN gen_table.parent_menu_id IS '父菜单ID';

CREATE TABLE IF NOT EXISTS gen_table_column (
    id BIGINT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    column_name VARCHAR(200) NOT NULL,
    column_comment VARCHAR(500) DEFAULT '',
    column_type VARCHAR(100) DEFAULT '',
    java_type VARCHAR(50) DEFAULT 'String',
    java_field VARCHAR(200) DEFAULT '',
    is_pk SMALLINT DEFAULT 0,
    is_increment SMALLINT DEFAULT 0,
    is_required SMALLINT DEFAULT 0,
    is_insert SMALLINT DEFAULT 1,
    is_edit SMALLINT DEFAULT 1,
    is_list SMALLINT DEFAULT 1,
    is_query SMALLINT DEFAULT 0,
    query_type VARCHAR(20) DEFAULT 'EQ',
    html_type VARCHAR(50) DEFAULT 'input',
    dict_type VARCHAR(200) DEFAULT '',
    sort INT DEFAULT 0
);

COMMENT ON TABLE gen_table_column IS '代码生成业务表字段';
COMMENT ON COLUMN gen_table_column.table_id IS '归属表ID';
COMMENT ON COLUMN gen_table_column.column_name IS '列名称';
COMMENT ON COLUMN gen_table_column.column_comment IS '列描述';
COMMENT ON COLUMN gen_table_column.column_type IS '列类型';
COMMENT ON COLUMN gen_table_column.java_type IS 'Java类型';
COMMENT ON COLUMN gen_table_column.java_field IS 'Java字段名';
COMMENT ON COLUMN gen_table_column.is_pk IS '是否主键（1是）';
COMMENT ON COLUMN gen_table_column.is_increment IS '是否自增（1是）';
COMMENT ON COLUMN gen_table_column.is_required IS '是否必填（1是）';
COMMENT ON COLUMN gen_table_column.is_insert IS '是否为插入字段（1是）';
COMMENT ON COLUMN gen_table_column.is_edit IS '是否编辑字段（1是）';
COMMENT ON COLUMN gen_table_column.is_list IS '是否列表字段（1是）';
COMMENT ON COLUMN gen_table_column.is_query IS '是否查询字段（1是）';
COMMENT ON COLUMN gen_table_column.query_type IS '查询方式';
COMMENT ON COLUMN gen_table_column.html_type IS '显示类型';
COMMENT ON COLUMN gen_table_column.dict_type IS '字典类型';
COMMENT ON COLUMN gen_table_column.sort IS '排序';

CREATE INDEX IF NOT EXISTS idx_gen_table_column_table_id ON gen_table_column(table_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_gen_table_column_table_id'
    ) THEN
        ALTER TABLE gen_table_column
            ADD CONSTRAINT fk_gen_table_column_table_id
                FOREIGN KEY (table_id) REFERENCES gen_table(id) ON DELETE CASCADE;
    END IF;
END $$;
