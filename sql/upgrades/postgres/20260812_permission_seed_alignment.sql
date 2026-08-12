-- =============================================
-- 20260812 权限点种子补齐与权限标识对齐（幂等）
--
-- 背景：
--   控制器 @PreAuthorize 声明的权限串与 sys_menu.perms 大面积对不上。
--   PermissionService.hasAuthority 只对 userId = 1 短路放行，因此 sys_menu 里没有
--   对应记录的权限点无法挂到 sys_role_menu，非超管调用一律 403。
--   本脚本把 AI / job / tenant / OSS / 开放平台 / 日志与在线用户的权限点补齐，
--   并把历史遗留的第二套权限串统一到后端注解口径。
--
-- 幂等策略：
--   1. 全部按语义键（perms / menu_type+path）判断存在性，不依赖固定主键 ID。
--   2. 菜单 ID 优先使用 sql/tiers/*/*-init.sql 与 phase9_base_menu_backfill.sql
--      共用的那套编号，被占用时退化为 MAX(id) + 1。
--   3. 按模块表是否存在做档位裁剪：small 没有 ai_model / open_app / wf_category /
--      sys_oss_config / sys_tenant，对应菜单不会被插入。
--
-- 回滚：
--   DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE perms IN (...));
--   DELETE FROM sys_menu WHERE perms IN (...);
--   （权限串重命名部分需按下方对照表反向 UPDATE）
-- =============================================

-- ---------------------------------------------
-- 1. 权限标识统一到后端 @PreAuthorize 口径
--    历史上同一功能在 tier init 与 phase9 里各有一套写法，这里把 phase9 那套改名过来。
--    目标串已存在时不动（避免制造重复 perms），留给人工确认后清理。
-- ---------------------------------------------
DO $$
DECLARE
    v_rename RECORD;
BEGIN
    FOR v_rename IN
        SELECT * FROM (
            VALUES
                ('system:operlog:list', 'monitor:operlog:list'),
                ('system:loginlog:list', 'monitor:loginlog:list'),
                ('system:monitor:server', 'monitor:server:list'),
                ('system:monitor:cache', 'monitor:cache:list')
        ) AS t(old_perms, new_perms)
    LOOP
        CONTINUE WHEN NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_rename.old_perms);
        CONTINUE WHEN EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_rename.new_perms);

        UPDATE sys_menu SET perms = v_rename.new_perms WHERE perms = v_rename.old_perms;
    END LOOP;
END $$;

-- AI 对话菜单历史上 perms 为空，导致 AiChatController 声明的 ai:chat:list 无法授权
UPDATE sys_menu
SET perms = 'ai:chat:list'
WHERE perms IS NULL
  AND menu_type = 'C'
  AND path = 'chat'
  AND component = 'ai/chat/index'
  AND NOT EXISTS (SELECT 1 FROM sys_menu inner_menu WHERE inner_menu.perms = 'ai:chat:list');

-- ---------------------------------------------
-- 2. 目录菜单（M）：任务调度 / 工作流 / 开放平台 / AI 智能
-- ---------------------------------------------
DO $$
DECLARE
    v_dir RECORD;
    v_next_id BIGINT;
BEGIN
    FOR v_dir IN
        SELECT * FROM (
            VALUES
                ('job', '任务调度', 'timer', 5, 200, 'sys_job'),
                ('workflow', '工作流', 'connection', 6, 300, 'wf_category'),
                ('open', '开放平台', 'platform', 7, 400, 'open_app'),
                ('ai', 'AI智能', 'magic-stick', 8, 500, 'ai_model')
        ) AS t(path, menu_name, icon, sort_no, preferred_id, guard_table)
    LOOP
        CONTINUE WHEN to_regclass('public.' || v_dir.guard_table) IS NULL;
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM sys_menu WHERE menu_type = 'M' AND path = v_dir.path
        );

        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = v_dir.preferred_id) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;
        ELSE
            v_next_id := v_dir.preferred_id;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, 0, '0', v_dir.menu_name, 'M',
            v_dir.path, NULL, NULL, v_dir.icon, v_dir.sort_no, 0, 0
        );
    END LOOP;
END $$;

-- ---------------------------------------------
-- 3. 页面菜单（C）：父目录按 menu_type + path 解析
-- ---------------------------------------------
DO $$
DECLARE
    v_menu RECORD;
    v_parent_id BIGINT;
    v_next_id BIGINT;
BEGIN
    FOR v_menu IN
        SELECT * FROM (
            VALUES
                ('job:list', '定时任务', 'job', 'list', 'job/index', 'clock', 1, 210, 'sys_job'),
                ('job:log:list', '调度日志', 'job', 'log', 'job/log', 'document', 2, 211, 'sys_job_log'),
                ('workflow:definition:list', '流程定义', 'workflow', 'definition', 'workflow/definition/index', 'document', 1, 310, 'wf_category'),
                ('workflow:instance:list', '流程实例', 'workflow', 'instance', 'workflow/instance/index', 'histogram', 2, 311, 'wf_instance_extend'),
                ('workflow:task:todo', '待办任务', 'workflow', 'todo', 'workflow/task/index', 'bell', 3, 312, 'wf_category'),
                ('workflow:task:done', '已办任务', 'workflow', 'done', 'workflow/task/done', 'finished', 4, 313, 'wf_category'),
                ('open:app:list', '应用管理', 'open', 'app', 'open/app/index', 'grid', 1, 410, 'open_app'),
                ('ai:model:list', 'AI模型管理', 'ai', 'model', 'ai/model/index', 'cpu', 1, 510, 'ai_model'),
                ('ai:kb:list', '知识库', 'ai', 'knowledge', 'ai/knowledge/index', 'collection', 2, 511, 'ai_knowledge_base'),
                ('ai:mcp:list', 'MCP管理', 'ai', 'mcp', 'ai/mcp/index', 'link', 3, 512, 'ai_mcp_server'),
                ('ai:agent:list', '智能体', 'ai', 'agent', 'ai/agent/index', 'user-filled', 4, 513, 'ai_agent'),
                ('ai:workflow:list', 'AI工作流', 'ai', 'workflow', 'ai/workflow/index', 'chat-dot-round', 5, 514, 'ai_workflow'),
                ('ai:token:stats', 'Token统计', 'ai', 'token', 'ai/token/index', 'data-analysis', 7, 516, 'ai_token_usage'),
                ('ai:chat:list', 'AI对话', 'ai', 'chat', 'ai/chat/index', 'chat-line-square', 8, 517, 'ai_conversation')
        ) AS t(perms, menu_name, parent_path, path, component, icon, sort_no, preferred_id, guard_table)
    LOOP
        CONTINUE WHEN to_regclass('public.' || v_menu.guard_table) IS NULL;
        CONTINUE WHEN EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_menu.perms);

        SELECT id INTO v_parent_id
        FROM sys_menu
        WHERE menu_type = 'M' AND path = v_menu.parent_path
        ORDER BY id
        LIMIT 1;

        CONTINUE WHEN v_parent_id IS NULL;

        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = v_menu.preferred_id) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;
        ELSE
            v_next_id := v_menu.preferred_id;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, v_parent_id, '0,' || v_parent_id, v_menu.menu_name, 'C',
            v_menu.path, v_menu.component, v_menu.perms, v_menu.icon, v_menu.sort_no, 0, 0
        );
    END LOOP;
END $$;

-- OSS 配置菜单挂在「系统管理」下，父目录解析方式与上面不同，单独处理
DO $$
DECLARE
    v_parent_id BIGINT;
    v_next_id BIGINT;
BEGIN
    IF to_regclass('public.sys_oss_config') IS NULL THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:oss:list') THEN
        RETURN;
    END IF;

    SELECT id INTO v_parent_id
    FROM sys_menu
    WHERE menu_type = 'M' AND path = 'system'
    ORDER BY id
    LIMIT 1;

    IF v_parent_id IS NULL THEN
        RETURN;
    END IF;

    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

    INSERT INTO sys_menu (
        id, tenant_id, parent_id, ancestors, menu_name, menu_type,
        path, component, perms, icon, sort, visible, status
    )
    VALUES (
        v_next_id, NULL, v_parent_id, '0,' || v_parent_id, 'OSS配置', 'C',
        'oss-config', 'system/oss-config/index', 'system:oss:list', 'upload', 11, 0, 0
    );
END $$;

-- 资源配额菜单挂在「租户管理」目录下（没有该目录时退回系统管理目录）
DO $$
DECLARE
    v_parent_id BIGINT;
    v_next_id BIGINT;
BEGIN
    IF to_regclass('public.sys_tenant_quota') IS NULL THEN
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'tenant:quota:query') THEN
        RETURN;
    END IF;

    SELECT id INTO v_parent_id
    FROM sys_menu
    WHERE menu_type = 'M' AND path IN ('tenant', 'system')
    ORDER BY CASE WHEN path = 'tenant' THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_parent_id IS NULL THEN
        RETURN;
    END IF;

    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

    INSERT INTO sys_menu (
        id, tenant_id, parent_id, ancestors, menu_name, menu_type,
        path, component, perms, icon, sort, visible, status
    )
    VALUES (
        v_next_id, NULL, v_parent_id, '0,' || v_parent_id, '资源配额', 'C',
        'quota', 'tenant/quota/index', 'tenant:quota:query', 'pie-chart', 3, 0, 0
    );
END $$;

-- ---------------------------------------------
-- 4. 按钮权限（F）：父菜单按 perms 解析，父菜单不存在说明该档位没部署对应模块，跳过
-- ---------------------------------------------
DO $$
DECLARE
    v_btn RECORD;
    v_parent_id BIGINT;
    v_parent_ancestors TEXT;
    v_next_id BIGINT;
BEGIN
    FOR v_btn IN
        SELECT * FROM (
            VALUES
                -- 用户 / 日志 / 在线用户
                ('system:user:unbind', '社交解绑', 'system:user:list', 8),
                ('monitor:operlog:export', '操作日志导出', 'monitor:operlog:list', 1),
                ('monitor:operlog:remove', '操作日志删除', 'monitor:operlog:list', 2),
                ('monitor:loginlog:export', '登录日志导出', 'monitor:loginlog:list', 1),
                ('monitor:loginlog:remove', '登录日志删除', 'monitor:loginlog:list', 2),
                ('monitor:online:forceLogout', '强制下线', 'monitor:online:list', 1),
                -- OSS 配置
                ('system:oss:query', 'OSS配置查询', 'system:oss:list', 1),
                ('system:oss:add', 'OSS配置新增', 'system:oss:list', 2),
                ('system:oss:edit', 'OSS配置修改', 'system:oss:list', 3),
                ('system:oss:remove', 'OSS配置删除', 'system:oss:list', 4),
                -- 定时任务
                ('job:add', '任务新增', 'job:list', 1),
                ('job:edit', '任务修改', 'job:list', 2),
                ('job:remove', '任务删除', 'job:list', 3),
                ('job:log:remove', '调度日志删除', 'job:log:list', 1),
                -- 租户
                ('tenant:query', '租户查询', 'tenant:list', 1),
                ('tenant:add', '租户新增', 'tenant:list', 2),
                ('tenant:edit', '租户修改', 'tenant:list', 3),
                ('tenant:remove', '租户删除', 'tenant:list', 4),
                ('system:tenant:list', '租户计费查询', 'tenant:list', 5),
                ('system:tenant:edit', '租户计费变更', 'tenant:list', 6),
                ('tenant:package:query', '套餐查询', 'tenant:package:list', 1),
                ('tenant:package:add', '套餐新增', 'tenant:package:list', 2),
                ('tenant:package:edit', '套餐修改', 'tenant:package:list', 3),
                ('tenant:package:remove', '套餐删除', 'tenant:package:list', 4),
                ('tenant:quota:edit', '配额修改', 'tenant:quota:query', 1),
                -- 开放平台
                ('open:app:query', '应用查询', 'open:app:list', 1),
                ('open:app:add', '应用新增', 'open:app:list', 2),
                ('open:app:edit', '应用修改', 'open:app:list', 3),
                ('open:app:remove', '应用删除', 'open:app:list', 4),
                ('open:app:resetSecret', '重置密钥', 'open:app:list', 5),
                -- AI 模型
                ('ai:model:query', 'AI模型查询', 'ai:model:list', 1),
                ('ai:model:add', 'AI模型新增', 'ai:model:list', 2),
                ('ai:model:edit', 'AI模型修改', 'ai:model:list', 3),
                ('ai:model:remove', 'AI模型删除', 'ai:model:list', 4),
                ('ai:model:test', 'AI模型连通性测试', 'ai:model:list', 5),
                -- AI 知识库
                ('ai:kb:query', '知识库查询', 'ai:kb:list', 1),
                ('ai:kb:add', '知识库新增', 'ai:kb:list', 2),
                ('ai:kb:edit', '知识库修改', 'ai:kb:list', 3),
                ('ai:kb:remove', '知识库删除', 'ai:kb:list', 4),
                ('ai:kb:upload', '知识库文档上传', 'ai:kb:list', 5),
                -- AI MCP
                ('ai:mcp:query', 'MCP查询', 'ai:mcp:list', 1),
                ('ai:mcp:add', 'MCP新增', 'ai:mcp:list', 2),
                ('ai:mcp:edit', 'MCP修改', 'ai:mcp:list', 3),
                ('ai:mcp:remove', 'MCP删除', 'ai:mcp:list', 4),
                -- AI 智能体
                ('ai:agent:add', '智能体新增', 'ai:agent:list', 1),
                ('ai:agent:edit', '智能体修改', 'ai:agent:list', 2),
                ('ai:agent:remove', '智能体删除', 'ai:agent:list', 3),
                -- AI 工作流
                ('ai:workflow:add', 'AI工作流新增', 'ai:workflow:list', 1),
                ('ai:workflow:edit', 'AI工作流修改', 'ai:workflow:list', 2),
                ('ai:workflow:remove', 'AI工作流删除', 'ai:workflow:list', 3)
        ) AS t(perms, menu_name, parent_perms, sort_no)
    LOOP
        CONTINUE WHEN EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_btn.perms);

        SELECT id, COALESCE(ancestors, '0')
        INTO v_parent_id, v_parent_ancestors
        FROM sys_menu
        WHERE perms = v_btn.parent_perms
        ORDER BY id
        LIMIT 1;

        CONTINUE WHEN v_parent_id IS NULL;

        SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

        INSERT INTO sys_menu (
            id, tenant_id, parent_id, ancestors, menu_name, menu_type,
            path, component, perms, icon, sort, visible, status
        )
        VALUES (
            v_next_id, NULL, v_parent_id, v_parent_ancestors || ',' || v_parent_id,
            v_btn.menu_name, 'F', '', NULL, v_btn.perms, '#', v_btn.sort_no, 0, 0
        );
    END LOOP;
END $$;

-- ---------------------------------------------
-- 5. 把本脚本涉及的权限点授予真实存在的超管角色
--    写法对齐 sql/tiers/full/full-init.sql 尾部的 AI 菜单授权段。
-- ---------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
CROSS JOIN sys_menu menu
WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
  AND menu.perms IN (
        'system:user:unbind',
        'monitor:operlog:list', 'monitor:operlog:export', 'monitor:operlog:remove',
        'monitor:loginlog:list', 'monitor:loginlog:export', 'monitor:loginlog:remove',
        'monitor:online:list', 'monitor:online:forceLogout',
        'monitor:server:list', 'monitor:cache:list',
        'system:oss:list', 'system:oss:query', 'system:oss:add', 'system:oss:edit', 'system:oss:remove',
        'job:list', 'job:add', 'job:edit', 'job:remove',
        'job:log:list', 'job:log:remove',
        'tenant:list', 'tenant:query', 'tenant:add', 'tenant:edit', 'tenant:remove',
        'system:tenant:list', 'system:tenant:edit',
        'tenant:package:list', 'tenant:package:query', 'tenant:package:add', 'tenant:package:edit', 'tenant:package:remove',
        'tenant:quota:query', 'tenant:quota:edit',
        'workflow:definition:list', 'workflow:instance:list', 'workflow:task:todo', 'workflow:task:done',
        'open:app:list', 'open:app:query', 'open:app:add', 'open:app:edit', 'open:app:remove', 'open:app:resetSecret',
        'ai:chat:list',
        'ai:model:list', 'ai:model:query', 'ai:model:add', 'ai:model:edit', 'ai:model:remove', 'ai:model:test',
        'ai:kb:list', 'ai:kb:query', 'ai:kb:add', 'ai:kb:edit', 'ai:kb:remove', 'ai:kb:upload',
        'ai:mcp:list', 'ai:mcp:query', 'ai:mcp:add', 'ai:mcp:edit', 'ai:mcp:remove',
        'ai:agent:list', 'ai:agent:add', 'ai:agent:edit', 'ai:agent:remove',
        'ai:workflow:list', 'ai:workflow:add', 'ai:workflow:edit', 'ai:workflow:remove',
        'ai:token:stats'
  )
ON CONFLICT DO NOTHING;

-- 目录菜单本身没有 perms，单独把它们也授给超管，否则子菜单在菜单树里挂不出来
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
CROSS JOIN sys_menu menu
WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
  AND menu.menu_type = 'M'
  AND menu.path IN ('job', 'workflow', 'open', 'ai')
ON CONFLICT DO NOTHING;
