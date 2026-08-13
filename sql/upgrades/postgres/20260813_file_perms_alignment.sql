-- =============================================
-- 20260813 文件管理权限串对齐到仓库约定（幂等）
--
-- 背景：
--   全仓约定是 `:list` 挂列表接口（GET /list）并兼作菜单可见性权限，
--   `:query` 挂按主键查详情的接口（GET /{id}）。二十多个模块都是这个口径。
--   han-file 是唯一的例外：它的 GET /file/list 用的是 file:query，
--   而菜单里播的页面权限是 file:list，两者对不上；file 模块又没有详情接口，
--   于是 file:query 成了一个"挂在列表接口上的详情权限"。
--   本轮把接口注解改成 file:list，file:query 随之失去对应接口，予以下线。
--
-- 影响：
--   已部署环境里，凡是只授了 file:query 而没授 file:list 的角色，
--   改注解后会直接丢掉文件列表权限。本脚本先把 file:list 补授给这些角色，
--   再删除 file:query 菜单及其角色绑定，保证升级前后可见范围不变。
--
-- 幂等策略：
--   1. 全部按权限串（perms）定位，不依赖固定菜单 ID。
--   2. 补授前先查重，已有绑定不重复插入。
--   3. file:query 菜单不存在时整段跳过，可重复执行。
--
-- 回滚：
--   本脚本删除了 file:query 菜单，回滚需要重新插入该菜单并恢复角色绑定；
--   由于 file:list 是超集授权，回滚时应同时评估是否收回新授的 file:list。
-- =============================================

DO $$
DECLARE
    v_query_menu_id BIGINT;
    v_list_menu_id  BIGINT;
    v_granted       INT := 0;
    v_unbound       INT := 0;
BEGIN
    SELECT id INTO v_query_menu_id FROM sys_menu WHERE perms = 'file:query' ORDER BY id LIMIT 1;
    IF v_query_menu_id IS NULL THEN
        RAISE NOTICE '[file-perms] file:query 菜单不存在，无需处理';
        RETURN;
    END IF;

    SELECT id INTO v_list_menu_id FROM sys_menu WHERE perms = 'file:list' ORDER BY id LIMIT 1;
    IF v_list_menu_id IS NULL THEN
        -- 没有 file:list 菜单说明本档位根本没部署文件模块，只需清掉孤立的 file:query
        DELETE FROM sys_role_menu WHERE menu_id = v_query_menu_id;
        DELETE FROM sys_menu WHERE id = v_query_menu_id;
        RAISE NOTICE '[file-perms] 本档位无 file:list 菜单，已清理孤立的 file:query';
        RETURN;
    END IF;

    -- 1. 给持有 file:query 但没有 file:list 的角色补授 file:list
    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT rm.role_id, v_list_menu_id
    FROM sys_role_menu rm
    WHERE rm.menu_id = v_query_menu_id
      AND NOT EXISTS (
          SELECT 1 FROM sys_role_menu x
          WHERE x.role_id = rm.role_id AND x.menu_id = v_list_menu_id
      );
    GET DIAGNOSTICS v_granted = ROW_COUNT;

    -- 2. 解绑并删除 file:query 菜单
    DELETE FROM sys_role_menu WHERE menu_id = v_query_menu_id;
    GET DIAGNOSTICS v_unbound = ROW_COUNT;
    DELETE FROM sys_menu WHERE id = v_query_menu_id;

    RAISE NOTICE '[file-perms] 已为 % 个角色补授 file:list，解绑并删除 file:query（原有 % 条绑定）',
        v_granted, v_unbound;
END $$;
