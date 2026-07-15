-- Backfill AIVideo admin menu routes and permissions for active administrator roles.
-- Idempotent and compatible with installations whose AI root/menu IDs differ from full init.

BEGIN;

DO $$
DECLARE
    v_ai_root_id BIGINT;
    v_task_menu_id BIGINT;
    v_setting_menu_id BIGINT;
    v_menu_id BIGINT;
    v_parent_id BIGINT;
    v_next_id BIGINT;
    v_menu RECORD;
    v_action RECORD;
BEGIN
    SELECT id
    INTO v_ai_root_id
    FROM sys_menu
    WHERE (path = 'ai' AND menu_type = 'M') OR menu_name = 'AI智能'
    ORDER BY CASE WHEN id = 500 THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_ai_root_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 500) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_ai_root_id FROM sys_menu;
        ELSE
            v_ai_root_id := 500;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
            query, menu_type, visible, status, perms, icon, is_frame, is_cache
        )
        VALUES (
            v_ai_root_id, NULL, 'AI智能', 0, '0', 5, 'ai', NULL,
            NULL, 'M', 0, 0, NULL, 'magic-stick', 1, 0
        );
    END IF;

    FOR v_menu IN
        SELECT * FROM (
            VALUES
                (518::BIGINT, '短剧任务监管', 'aivideo/tasks', 'ai/aivideo/tasks/index', 'ai:aivideo:task:list', 'video-camera', 9),
                (519::BIGINT, '短剧基础配置', 'aivideo/settings', 'ai/aivideo/settings/index', 'ai:aivideo:setting:query', 'tools', 10)
        ) AS menu(preferred_id, menu_name, path, component, perms, icon, sort_no)
    LOOP
        SELECT id
        INTO v_menu_id
        FROM sys_menu target
        WHERE target.perms = v_menu.perms
           OR (target.path = v_menu.path AND target.component = v_menu.component)
        ORDER BY CASE WHEN target.id = v_menu.preferred_id THEN 0 ELSE 1 END, target.id
        LIMIT 1;

        IF v_menu_id IS NULL THEN
            IF EXISTS (SELECT 1 FROM sys_menu WHERE id = v_menu.preferred_id) THEN
                SELECT COALESCE(MAX(id), 0) + 1 INTO v_menu_id FROM sys_menu;
            ELSE
                v_menu_id := v_menu.preferred_id;
            END IF;

            INSERT INTO sys_menu (
                id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
                query, menu_type, visible, status, perms, icon, is_frame, is_cache
            )
            VALUES (
                v_menu_id, NULL, v_menu.menu_name, v_ai_root_id, '0,' || v_ai_root_id,
                v_menu.sort_no, v_menu.path, v_menu.component, NULL, 'C', 0, 0,
                v_menu.perms, v_menu.icon, 1, 0
            );
        ELSE
            UPDATE sys_menu
            SET menu_name = v_menu.menu_name,
                parent_id = v_ai_root_id,
                ancestors = '0,' || v_ai_root_id,
                sort = v_menu.sort_no,
                path = v_menu.path,
                component = v_menu.component,
                query = NULL,
                menu_type = 'C',
                visible = 0,
                status = 0,
                perms = v_menu.perms,
                icon = v_menu.icon,
                is_frame = 1,
                is_cache = 0,
                del_flag = 0
            WHERE id = v_menu_id;
        END IF;

        IF v_menu.perms = 'ai:aivideo:task:list' THEN
            v_task_menu_id := v_menu_id;
        ELSE
            v_setting_menu_id := v_menu_id;
        END IF;
    END LOOP;

    FOR v_action IN
        SELECT * FROM (
            VALUES
                (1081::BIGINT, '短剧任务查询', 'ai:aivideo:task:query', 'task', 1),
                (1082::BIGINT, '短剧配置编辑', 'ai:aivideo:setting:edit', 'setting', 1)
        ) AS action(preferred_id, menu_name, perms, parent_kind, sort_no)
    LOOP
        v_parent_id := CASE WHEN v_action.parent_kind = 'task' THEN v_task_menu_id ELSE v_setting_menu_id END;

        SELECT id
        INTO v_menu_id
        FROM sys_menu
        WHERE perms = v_action.perms
        ORDER BY CASE WHEN id = v_action.preferred_id THEN 0 ELSE 1 END, id
        LIMIT 1;

        IF v_menu_id IS NULL THEN
            IF EXISTS (SELECT 1 FROM sys_menu WHERE id = v_action.preferred_id) THEN
                SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;
            ELSE
                v_next_id := v_action.preferred_id;
            END IF;

            INSERT INTO sys_menu (
                id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
                query, menu_type, visible, status, perms, icon, is_frame, is_cache
            )
            VALUES (
                v_next_id, NULL, v_action.menu_name, v_parent_id,
                '0,' || v_ai_root_id || ',' || v_parent_id,
                v_action.sort_no, '', NULL, NULL, 'F', 0, 0,
                v_action.perms, '#', 1, 0
            );
        ELSE
            UPDATE sys_menu
            SET menu_name = v_action.menu_name,
                parent_id = v_parent_id,
                ancestors = '0,' || v_ai_root_id || ',' || v_parent_id,
                sort = v_action.sort_no,
                path = '',
                component = NULL,
                query = NULL,
                menu_type = 'F',
                visible = 0,
                status = 0,
                icon = '#',
                is_frame = 1,
                is_cache = 0,
                del_flag = 0
            WHERE id = v_menu_id;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_role'
          AND column_name = 'status'
    ) THEN
        INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT role.id, menu.id
        FROM sys_role role
        CROSS JOIN sys_menu menu
        WHERE COALESCE(role.del_flag, 0) = 0
          AND COALESCE(role.status, 0) = 0
          AND (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
          AND (
              menu.id IN (v_ai_root_id, v_task_menu_id, v_setting_menu_id)
              OR menu.perms IN (
                  'ai:aivideo:task:list',
                  'ai:aivideo:task:query',
                  'ai:aivideo:setting:query',
                  'ai:aivideo:setting:edit'
              )
          )
        ON CONFLICT DO NOTHING;
    ELSE
        INSERT INTO sys_role_menu (role_id, menu_id)
        SELECT role.id, menu.id
        FROM sys_role role
        CROSS JOIN sys_menu menu
        WHERE COALESCE(role.del_flag, 0) = 0
          AND (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
          AND (
              menu.id IN (v_ai_root_id, v_task_menu_id, v_setting_menu_id)
              OR menu.perms IN (
                  'ai:aivideo:task:list',
                  'ai:aivideo:task:query',
                  'ai:aivideo:setting:query',
                  'ai:aivideo:setting:edit'
              )
          )
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
COMMIT;
