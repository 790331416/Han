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
    v_next_id BIGINT;
    v_action  RECORD;
BEGIN
    -- 文件管理主菜单（挂系统管理 parent_id=1）
    SELECT id INTO v_menu_id FROM sys_menu WHERE perms = 'file:list' AND menu_type = 'C' LIMIT 1;

    IF v_menu_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 109) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_menu_id FROM sys_menu;
        ELSE
            v_menu_id := 109;
        END IF;

        INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
        VALUES (v_menu_id, 1, '0,1', '文件管理', 'C', 'file', 'system/file/index', 'file:list', 'upload', 10, 0, 0);
    END IF;

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
            VALUES (v_next_id, v_menu_id, '0,1,' || v_menu_id, v_action.menu_name, 'F', '', NULL, v_action.perms, '#', v_action.sort_no, 0, 0);
        END IF;
    END LOOP;

    -- 超管角色补挂新菜单
    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT 1, menu.id
    FROM sys_menu menu
    WHERE menu.perms IN ('file:list', 'file:query', 'file:remove')
      AND NOT EXISTS (
          SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = menu.id
      );
END $$;
