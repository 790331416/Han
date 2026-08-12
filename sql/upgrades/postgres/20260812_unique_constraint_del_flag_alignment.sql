-- =============================================
-- 20260812 唯一约束与逻辑删除对齐 + sys_user_social 结构兜底（幂等）
--
-- 背景：
--   BaseEntity.delFlag 标了 @TableLogic，删除是逻辑删除。但唯一约束都建在业务列上、
--   不含 del_flag，软删除一条记录后就再也建不出同名记录。
--   phase5_unique_constraint.sql 只处理了 sys_user，角色名称/权限字符、岗位编码、
--   字典类型、字典值、参数键名、客户端 key 都还是旧的完整唯一约束或干脆没有约束。
--
-- 幂等策略：
--   1. 旧唯一约束按「列集合」查 pg_constraint 后删除，不依赖固定约束名。
--   2. 表不存在、关键列缺失时跳过，兼容 small/medium 档位和缺列旧库。
--   3. 建索引前先查重复；有重复时只 RAISE NOTICE 跳过，不让整条升级链中断。
--      **有重复的环境需要人工清理后重跑本脚本**，NOTICE 里会打印表名与重复组数。
--
-- 回滚：
--   DROP INDEX IF EXISTS uk_sys_role_key_tenant, uk_sys_role_name_tenant,
--     uk_sys_post_code_tenant, uk_sys_dict_type_tenant, uk_sys_dict_data_tenant,
--     uk_sys_config_key_tenant, uk_sys_client_key;
-- =============================================

-- ---------------------------------------------
-- 1. sys_user_social 结构兜底
--    20260415_social_login_migration.sql 建表时带了全局 UNIQUE(provider, open_id)，
--    20260720_wechat_social_login.sql 会把它换成两个租户隔离唯一索引。
--    这里只在两个脚本都没跑过的库上补建表，保证三档 init 与升级路径结构一致。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_social (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    tenant_id       BIGINT,
    provider        VARCHAR(32) NOT NULL,
    open_id         VARCHAR(128) NOT NULL,
    access_token    VARCHAR(512),
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    extra           TEXT,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_social_user_id ON sys_user_social (user_id);
CREATE INDEX IF NOT EXISTS idx_user_social_provider_openid ON sys_user_social (provider, open_id);

-- ---------------------------------------------
-- 2. 把不带 del_flag 条件的唯一约束换成部分唯一索引
-- ---------------------------------------------
DO $$
DECLARE
    v_spec RECORD;
    v_missing TEXT;
    v_con TEXT;
    v_dup BIGINT;
BEGIN
    FOR v_spec IN
        SELECT * FROM (VALUES
            ('sys_role', 'uk_sys_role_key_tenant', 'tenant_id, role_key',
             ARRAY['tenant_id', 'role_key']),
            ('sys_role', 'uk_sys_role_name_tenant', 'tenant_id, role_name',
             ARRAY['tenant_id', 'role_name']),
            ('sys_post', 'uk_sys_post_code_tenant', 'tenant_id, post_code',
             ARRAY['tenant_id', 'post_code']),
            ('sys_dict_type', 'uk_sys_dict_type_tenant', 'COALESCE(tenant_id, 0), dict_type',
             ARRAY['tenant_id', 'dict_type']),
            ('sys_dict_data', 'uk_sys_dict_data_tenant', 'COALESCE(tenant_id, 0), dict_type, dict_value',
             ARRAY['tenant_id', 'dict_type', 'dict_value']),
            ('sys_config', 'uk_sys_config_key_tenant', 'COALESCE(tenant_id, 0), config_key',
             ARRAY['tenant_id', 'config_key']),
            ('sys_client', 'uk_sys_client_key', 'client_key',
             ARRAY['client_key'])
        ) AS t(tbl, idx, cols, key_cols)
    LOOP
        CONTINUE WHEN to_regclass('public.' || v_spec.tbl) IS NULL;

        -- 关键列或 del_flag 缺失时跳过（兼容缺列旧库）
        SELECT string_agg(c, ', ') INTO v_missing
        FROM unnest(v_spec.key_cols || ARRAY['del_flag']) AS c
        WHERE NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = v_spec.tbl
              AND column_name = c
        );
        IF v_missing IS NOT NULL THEN
            RAISE NOTICE '跳过 %：% 缺少列 %', v_spec.idx, v_spec.tbl, v_missing;
            CONTINUE;
        END IF;

        -- 唯一 CONSTRAINT 无法带 WHERE，凡是落在这批业务列上的都要换成部分唯一索引
        FOR v_con IN
            SELECT conname
            FROM pg_constraint
            WHERE conrelid = ('public.' || v_spec.tbl)::regclass
              AND contype = 'u'
              AND (SELECT array_agg(attname)
                   FROM unnest(conkey) AS k
                   JOIN pg_attribute a ON a.attrelid = conrelid AND a.attnum = k)
                  <@ v_spec.key_cols::name[]
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', v_spec.tbl, v_con);
            RAISE NOTICE '已删除旧唯一约束 %.%', v_spec.tbl, v_con;
        END LOOP;

        CONTINUE WHEN EXISTS (
            SELECT 1 FROM pg_class
            WHERE relname = v_spec.idx
              AND relkind = 'i'
              AND relnamespace = 'public'::regnamespace
        );

        EXECUTE format(
            'SELECT COUNT(*) FROM (SELECT 1 FROM %I WHERE COALESCE(del_flag, 0) = 0 GROUP BY %s HAVING COUNT(*) > 1) d',
            v_spec.tbl, v_spec.cols
        ) INTO v_dup;

        IF v_dup > 0 THEN
            RAISE NOTICE '跳过 %：% 存在 % 组重复数据，请先人工清理后重跑本脚本',
                v_spec.idx, v_spec.tbl, v_dup;
            CONTINUE;
        END IF;

        EXECUTE format(
            'CREATE UNIQUE INDEX %I ON %I (%s) WHERE del_flag = 0',
            v_spec.idx, v_spec.tbl, v_spec.cols
        );
    END LOOP;
END $$;
