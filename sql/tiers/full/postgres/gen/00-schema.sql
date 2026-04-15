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

COMMENT ON TABLE gen_table IS 'Code generator table metadata';
COMMENT ON COLUMN gen_table.table_name IS 'Database table name';
COMMENT ON COLUMN gen_table.table_comment IS 'Database table comment';
COMMENT ON COLUMN gen_table.package_name IS 'Generated package name';
COMMENT ON COLUMN gen_table.module_name IS 'Generated module name';
COMMENT ON COLUMN gen_table.business_name IS 'Generated business name';
COMMENT ON COLUMN gen_table.function_name IS 'Generated function label';
COMMENT ON COLUMN gen_table.author IS 'Generated author';
COMMENT ON COLUMN gen_table.parent_menu_id IS 'Parent menu ID';

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

COMMENT ON TABLE gen_table_column IS 'Code generator column metadata';
COMMENT ON COLUMN gen_table_column.table_id IS 'Related table ID';
COMMENT ON COLUMN gen_table_column.column_name IS 'Database column name';
COMMENT ON COLUMN gen_table_column.column_comment IS 'Database column comment';
COMMENT ON COLUMN gen_table_column.column_type IS 'Database column type';
COMMENT ON COLUMN gen_table_column.java_type IS 'Generated Java type';
COMMENT ON COLUMN gen_table_column.java_field IS 'Generated Java field';
COMMENT ON COLUMN gen_table_column.is_pk IS 'Whether the column is a primary key';
COMMENT ON COLUMN gen_table_column.is_increment IS 'Whether the column is auto increment';
COMMENT ON COLUMN gen_table_column.is_required IS 'Whether the column is required';
COMMENT ON COLUMN gen_table_column.is_insert IS 'Whether the column is included on insert';
COMMENT ON COLUMN gen_table_column.is_edit IS 'Whether the column is included on edit';
COMMENT ON COLUMN gen_table_column.is_list IS 'Whether the column is included on list view';
COMMENT ON COLUMN gen_table_column.is_query IS 'Whether the column is queryable';
COMMENT ON COLUMN gen_table_column.query_type IS 'Query operator';
COMMENT ON COLUMN gen_table_column.html_type IS 'Rendered form control';
COMMENT ON COLUMN gen_table_column.dict_type IS 'Bound dictionary type';
COMMENT ON COLUMN gen_table_column.sort IS 'Display order';

CREATE INDEX IF NOT EXISTS idx_gen_table_column_table_id ON gen_table_column (table_id);

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
