-- =============================================
-- 20260720 微信扫码登录升级（幂等）
-- 1. sys_user_social 唯一键按租户隔离：
--    旧约束 UNIQUE(provider, open_id) → 新规则「租户内一个第三方身份只绑一个账号」+
--    「一个账号同 provider 只绑一个第三方身份」（与 SysUserSocialService 校验一致）
-- 2. sys_config 新增登录方式开关 sys.login.wechatEnabled（默认 false，需管理员显式开启）
-- 3. sys_menu 新增用户管理「社交解绑」按钮权限 system:user:unbind，并补挂超管角色
-- 注意：本脚本会**物理删除**违反新唯一规则的历史社交绑定行。
--       执行前请确保已做逻辑备份；脚本自身也会先把待删行写进
--       sys_user_social_conflict_backup_20260720 备份表。
-- 回滚：
--   DROP INDEX IF EXISTS uq_user_social_tenant_provider_openid;
--   DROP INDEX IF EXISTS uq_user_social_user_provider;
--   INSERT INTO sys_user_social SELECT * FROM sys_user_social_conflict_backup_20260720;
--   ALTER TABLE sys_user_social ADD CONSTRAINT sys_user_social_provider_open_id_key UNIQUE (provider, open_id);
--   DELETE FROM sys_config WHERE config_key = 'sys.login.wechatEnabled';
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE perms = 'system:user:unbind');
--   DELETE FROM sys_menu WHERE perms = 'system:user:unbind';
-- =============================================

-- 1. 绑定表（历史环境可能未执行过 20260415_social_login_migration.sql，先兜底建表）
CREATE TABLE IF NOT EXISTS sys_user_social (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    tenant_id       BIGINT,
    provider        VARCHAR(32) NOT NULL,
    open_id         VARCHAR(128) NOT NULL,
    access_token    VARCHAR(512) DEFAULT NULL,
    nickname        VARCHAR(100) DEFAULT NULL,
    avatar          VARCHAR(500) DEFAULT NULL,
    extra           TEXT DEFAULT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    v_constraint TEXT;
BEGIN
    -- 去掉全局唯一约束（跨租户允许同一第三方身份分别绑定）
    SELECT conname INTO v_constraint
    FROM pg_constraint
    WHERE conrelid = 'sys_user_social'::regclass
      AND contype = 'u'
      AND (SELECT array_agg(attname ORDER BY attname)
           FROM unnest(conkey) AS k
           JOIN pg_attribute a ON a.attrelid = conrelid AND a.attnum = k) = ARRAY['open_id', 'provider']::name[]
    LIMIT 1;

    IF v_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE sys_user_social DROP CONSTRAINT %I', v_constraint);
    END IF;
END $$;

-- 清理违反新唯一规则的历史脏数据（保留最早一条）
-- 20260812 补充：这是本升级链里唯一一处真实删数据的操作，且删掉的绑定行无法恢复。
-- 删除前先把待删行落进备份表，回滚时可以从备份表 INSERT 回去。
CREATE TABLE IF NOT EXISTS sys_user_social_conflict_backup_20260720 (LIKE sys_user_social);

INSERT INTO sys_user_social_conflict_backup_20260720
SELECT s.*
FROM sys_user_social s
WHERE (
        EXISTS (
            SELECT 1 FROM sys_user_social keep
            WHERE keep.provider = s.provider
              AND keep.open_id = s.open_id
              AND COALESCE(keep.tenant_id, 0) = COALESCE(s.tenant_id, 0)
              AND keep.id < s.id
        )
        OR EXISTS (
            SELECT 1 FROM sys_user_social keep
            WHERE keep.user_id = s.user_id
              AND keep.provider = s.provider
              AND keep.id < s.id
        )
      )
  AND NOT EXISTS (
        SELECT 1 FROM sys_user_social_conflict_backup_20260720 b WHERE b.id = s.id
      );

DELETE FROM sys_user_social s
USING sys_user_social keep
WHERE s.provider = keep.provider
  AND s.open_id = keep.open_id
  AND COALESCE(s.tenant_id, 0) = COALESCE(keep.tenant_id, 0)
  AND s.id > keep.id;

DELETE FROM sys_user_social s
USING sys_user_social keep
WHERE s.user_id = keep.user_id
  AND s.provider = keep.provider
  AND s.id > keep.id;

-- 租户内一个第三方身份只绑一个账号（tenant_id 为空按 0 归一，避免 NULL 逃逸唯一约束）
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_social_tenant_provider_openid
    ON sys_user_social (COALESCE(tenant_id, 0), provider, open_id);

-- 一个账号同 provider 只绑一个第三方身份
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_social_user_provider
    ON sys_user_social (user_id, provider);

-- 2. 登录方式开关（默认关闭，管理员在参数设置中显式开启）
-- 20260812 修正：旧库的 sys_config 可能只有 (id, config_key, del_flag)，
-- 直接 INSERT 这五列会报 column does not exist 并中断整条升级链，先补列兜底。
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_name VARCHAR(100);
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_value VARCHAR(2000) DEFAULT '';
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_type CHAR(1) DEFAULT 'N';
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS remark VARCHAR(500);

INSERT INTO sys_config (id, config_name, config_key, config_value, config_type, remark)
SELECT (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config),
       '用户登录-微信扫码登录开关', 'sys.login.wechatEnabled', 'false', 'Y',
       '是否开启微信扫码登录（true开启，false关闭）；开启前需在服务端配置 WECHAT_OPEN_APP_ID/WECHAT_OPEN_APP_SECRET'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'sys.login.wechatEnabled');

-- 3. 用户管理「社交解绑」按钮权限
-- 20260812 修正：父菜单原来写死 parent_id = 100 / ancestors = '0,1,100'，
-- 那是 tier init 的编号；按 phase9 体系回填的旧库里 id 100 是「系统管理」目录，
-- 插进去的按钮会挂错父节点。改为按 system:user:list 解析真实父菜单。
-- 角色授权也不再硬编码 role_id = 1。
DO $$
DECLARE
    v_parent_id BIGINT;
    v_parent_ancestors TEXT;
    v_next_id BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:user:unbind') THEN
        SELECT id, COALESCE(ancestors, '0')
        INTO v_parent_id, v_parent_ancestors
        FROM sys_menu
        WHERE perms = 'system:user:list'
        ORDER BY id
        LIMIT 1;

        IF v_parent_id IS NOT NULL THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

            INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
            VALUES (v_next_id, v_parent_id, v_parent_ancestors || ',' || v_parent_id,
                    '社交解绑', 'F', '', NULL, 'system:user:unbind', '#', 8, 0, 0);
        END IF;
    END IF;

    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT role.id, menu.id
    FROM sys_role role
    CROSS JOIN sys_menu menu
    WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
      AND menu.perms = 'system:user:unbind'
    ON CONFLICT DO NOTHING;
END $$;
