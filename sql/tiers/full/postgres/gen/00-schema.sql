-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

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

COMMENT ON TABLE gen_table IS '浠ｇ爜鐢熸垚涓氬姟琛?;
COMMENT ON COLUMN gen_table.table_name IS '琛ㄥ悕绉?;
COMMENT ON COLUMN gen_table.table_comment IS '琛ㄦ弿杩?;
COMMENT ON COLUMN gen_table.package_name IS '鐢熸垚鍖呰矾寰?;
COMMENT ON COLUMN gen_table.module_name IS '鐢熸垚妯″潡鍚?;
COMMENT ON COLUMN gen_table.business_name IS '鐢熸垚涓氬姟鍚?;
COMMENT ON COLUMN gen_table.function_name IS '鐢熸垚鍔熻兘鍚嶏紙绫诲悕锛?;
COMMENT ON COLUMN gen_table.author IS '浣滆€?;
COMMENT ON COLUMN gen_table.parent_menu_id IS '鐖惰彍鍗旾D';

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

COMMENT ON TABLE gen_table_column IS '浠ｇ爜鐢熸垚涓氬姟琛ㄥ瓧娈?;
COMMENT ON COLUMN gen_table_column.table_id IS '褰掑睘琛↖D';
COMMENT ON COLUMN gen_table_column.column_name IS '鍒楀悕绉?;
COMMENT ON COLUMN gen_table_column.column_comment IS '鍒楁弿杩?;
COMMENT ON COLUMN gen_table_column.column_type IS '鍒楃被鍨?;
COMMENT ON COLUMN gen_table_column.java_type IS 'Java绫诲瀷';
COMMENT ON COLUMN gen_table_column.java_field IS 'Java瀛楁鍚?;
COMMENT ON COLUMN gen_table_column.is_pk IS '鏄惁涓婚敭锛?鏄級';
COMMENT ON COLUMN gen_table_column.is_increment IS '鏄惁鑷锛?鏄級';
COMMENT ON COLUMN gen_table_column.is_required IS '鏄惁蹇呭～锛?鏄級';
COMMENT ON COLUMN gen_table_column.is_insert IS '鏄惁涓烘彃鍏ュ瓧娈碉紙1鏄級';
COMMENT ON COLUMN gen_table_column.is_edit IS '鏄惁缂栬緫瀛楁锛?鏄級';
COMMENT ON COLUMN gen_table_column.is_list IS '鏄惁鍒楄〃瀛楁锛?鏄級';
COMMENT ON COLUMN gen_table_column.is_query IS '鏄惁鏌ヨ瀛楁锛?鏄級';
COMMENT ON COLUMN gen_table_column.query_type IS '鏌ヨ鏂瑰紡';
COMMENT ON COLUMN gen_table_column.html_type IS '鏄剧ず绫诲瀷';
COMMENT ON COLUMN gen_table_column.dict_type IS '瀛楀吀绫诲瀷';
COMMENT ON COLUMN gen_table_column.sort IS '鎺掑簭';

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
