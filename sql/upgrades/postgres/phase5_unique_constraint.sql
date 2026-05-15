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

ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS sys_user_username_key;

CREATE UNIQUE INDEX IF NOT EXISTS sys_user_username_tenant_uniq
    ON sys_user (username, tenant_id) WHERE del_flag = 0;
