-- =============================================
-- 20260703 文件管理菜单升级（幂等）
-- 系统管理下新增「文件管理」菜单（file:list）与文件查询/删除按钮权限，
-- 配套 han-file 新增的 /file/list、/file/remove/{ids} 管理接口
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE perms IN ('file:list','file:query','file:remove'));
--   DELETE FROM sys_menu WHERE perms IN ('file:list','file:query','file:remove');
-- =============================================

DO $$
DECLARE
    v_menu_id BIGINT;
    v_parent_id BIGINT;
    v_parent_ancestors TEXT;
    v_menu_ancestors TEXT;
    v_next_id BIGINT;
    v_action  RECORD;
BEGIN
    -- 文件管理主菜单挂在「系统管理」目录下。
    -- 20260812 修正：父菜单原来写死 parent_id = 1 / ancestors = '0,1'，那是 tier init 的编号；
    -- 按 phase9 体系回填的旧库里根本没有 id 1，插进去就是父节点悬空的孤儿菜单，
    -- 管理端菜单树里看不到，file:list 功能入口直接缺失。改为按 path 解析真实父目录。
    SELECT id, COALESCE(ancestors, '0')
    INTO v_parent_id, v_parent_ancestors
    FROM sys_menu
    WHERE menu_type = 'M' AND path = 'system'
    ORDER BY id
    LIMIT 1;

    IF v_parent_id IS NULL THEN
        RAISE NOTICE '跳过文件管理菜单：未找到「系统管理」目录菜单（menu_type=M, path=system）';
        RETURN;
    END IF;

    SELECT id INTO v_menu_id FROM sys_menu WHERE perms = 'file:list' AND menu_type = 'C' LIMIT 1;

    IF v_menu_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 109) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_menu_id FROM sys_menu;
        ELSE
            v_menu_id := 109;
        END IF;

        INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
        VALUES (v_menu_id, v_parent_id, v_parent_ancestors || ',' || v_parent_id,
                '文件管理', 'C', 'file', 'system/file/index', 'file:list', 'upload', 10, 0, 0);
    END IF;

    SELECT COALESCE(ancestors, '0') INTO v_menu_ancestors FROM sys_menu WHERE id = v_menu_id;

    -- 按钮权限
    FOR v_action IN
        SELECT * FROM (VALUES
            ('文件查询', 'file:query', 1),
            ('文件删除', 'file:remove', 2)
        ) AS action(menu_name, perms, sort_no)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_action.perms) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

            INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
            VALUES (v_next_id, v_menu_id, v_menu_ancestors || ',' || v_menu_id, v_action.menu_name, 'F', '', NULL, v_action.perms, '#', v_action.sort_no, 0, 0);
        END IF;
    END LOOP;

    -- 补挂给真实存在的超管角色（原来硬编码 role_id = 1，角色不是 1 时权限挂不上任何有效角色）
    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT role.id, menu.id
    FROM sys_role role
    CROSS JOIN sys_menu menu
    WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
      AND menu.perms IN ('file:list', 'file:query', 'file:remove')
    ON CONFLICT DO NOTHING;
END $$;
