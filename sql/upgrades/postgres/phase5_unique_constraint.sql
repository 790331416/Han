-- Phase 5: 修复 sys_user 唯一约束，支持多租户同名用户
-- 原约束: UNIQUE(username) 全局唯一，不支持不同租户使用相同用户名
-- 新约束: UNIQUE(username, tenant_id) WHERE del_flag = 0，按租户隔离且排除已删除记录

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_user'
          AND column_name = 'deleted'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_user'
          AND column_name = 'del_flag'
    ) THEN
        ALTER TABLE sys_user RENAME COLUMN deleted TO del_flag;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_user'
          AND column_name = 'del_flag'
    ) THEN
        ALTER TABLE sys_user ADD COLUMN del_flag SMALLINT DEFAULT 0;
    END IF;

    UPDATE sys_user SET del_flag = COALESCE(del_flag, 0) WHERE del_flag IS NULL;
    ALTER TABLE sys_user ALTER COLUMN del_flag SET DEFAULT 0;
END $$;

-- 20260812 修正：原来只 DROP 固定名 sys_user_username_key，
-- 显式命名的旧约束（uk_sys_user_username 等）和 tier init 自动命名的
-- sys_user_username_tenant_id_key 都躲得过去，结果新建的部分唯一索引形同虚设。
-- 改为按列集合查 pg_constraint，写法对齐 20260720_wechat_social_login.sql。
DO $$
DECLARE
    v_constraint TEXT;
BEGIN
    FOR v_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'sys_user'::regclass
          AND contype = 'u'
          AND (SELECT array_agg(attname ORDER BY attname)
               FROM unnest(conkey) AS k
               JOIN pg_attribute a ON a.attrelid = conrelid AND a.attnum = k)
              IN (ARRAY['username']::name[], ARRAY['tenant_id', 'username']::name[])
    LOOP
        EXECUTE format('ALTER TABLE sys_user DROP CONSTRAINT %I', v_constraint);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS sys_user_username_tenant_uniq
    ON sys_user (username, tenant_id) WHERE del_flag = 0;
