DO $$
DECLARE
    v_table_name TEXT;
BEGIN
    FOREACH v_table_name IN ARRAY ARRAY[
        'sys_dept',
        'sys_user',
        'sys_post',
        'sys_role',
        'sys_menu',
        'sys_dict_type',
        'sys_dict_data',
        'sys_config',
        'sys_client'
    ]
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = v_table_name
              AND column_name = 'deleted'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = v_table_name
              AND column_name = 'del_flag'
        ) THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN deleted TO del_flag', v_table_name);
        ELSIF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = v_table_name
              AND column_name = 'del_flag'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN del_flag SMALLINT DEFAULT 0', v_table_name);
        END IF;

        EXECUTE format('UPDATE %I SET del_flag = COALESCE(del_flag, 0)', v_table_name);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN del_flag SET DEFAULT 0', v_table_name);
    END LOOP;
END $$;
