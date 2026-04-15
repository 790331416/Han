-- =============================================
-- Phase 9: backfill baseline menus for existing PostgreSQL volumes
-- Safe to re-run: inserts only missing baseline menu rows and role mappings.
-- =============================================

INSERT INTO sys_menu (
    id, tenant_id, menu_name, parent_id, ancestors, sort, order_num, path, component,
    query, menu_type, visible, status, perms, icon, is_frame, is_cache
) VALUES
    (100, NULL, '系统管理', 0, '0', 1, 1, 'system', NULL, NULL, 'M', 0, 0, NULL, 'setting', 1, 0),
    (200, NULL, '任务调度', 0, '0', 2, 2, 'job', NULL, NULL, 'M', 0, 0, NULL, 'timer', 1, 0),
    (300, NULL, '工作流', 0, '0', 3, 3, 'workflow', NULL, NULL, 'M', 0, 0, NULL, 'connection', 1, 0),
    (400, NULL, '开放平台', 0, '0', 4, 4, 'open', NULL, NULL, 'M', 0, 0, NULL, 'platform', 1, 0),
    (500, NULL, 'AI智能', 0, '0', 5, 5, 'ai', NULL, NULL, 'M', 0, 0, NULL, 'magic-stick', 1, 0),
    (110, NULL, '用户管理', 100, '0,100', 1, 1, 'user', 'system/user/index', NULL, 'C', 0, 0, 'system:user:list', 'user', 1, 0),
    (111, NULL, '角色管理', 100, '0,100', 2, 2, 'role', 'system/role/index', NULL, 'C', 0, 0, 'system:role:list', 'user-filled', 1, 0),
    (112, NULL, '菜单管理', 100, '0,100', 3, 3, 'menu', 'system/menu/index', NULL, 'C', 0, 0, 'system:menu:list', 'menu', 1, 0),
    (113, NULL, '部门管理', 100, '0,100', 4, 4, 'dept', 'system/dept/index', NULL, 'C', 0, 0, 'system:dept:list', 'office-building', 1, 0),
    (114, NULL, '岗位管理', 100, '0,100', 5, 5, 'post', 'system/post/index', NULL, 'C', 0, 0, 'system:post:list', 'postcard', 1, 0),
    (115, NULL, '字典管理', 100, '0,100', 6, 6, 'dict', 'system/dict/index', NULL, 'C', 0, 0, 'system:dict:list', 'notebook', 1, 0),
    (116, NULL, '租户管理', 100, '0,100', 7, 7, 'tenant', 'system/tenant/index', NULL, 'C', 0, 0, 'tenant:list', 'coin', 1, 0),
    (117, NULL, '租户套餐', 100, '0,100', 8, 8, 'tenant-package', 'system/tenant/package', NULL, 'C', 0, 0, 'tenant:package:list', 'shopping-bag', 1, 0),
    (118, NULL, '资源配额', 100, '0,100', 9, 9, 'tenant-quota', 'system/tenant/quota', NULL, 'C', 0, 0, 'tenant:quota:query', 'pie-chart', 1, 0),
    (119, NULL, '参数配置', 100, '0,100', 10, 10, 'config', 'system/config/index', NULL, 'C', 0, 0, 'system:config:list', 'tools', 1, 0),
    (120, NULL, '通知公告', 100, '0,100', 11, 11, 'notice', 'system/notice/index', NULL, 'C', 0, 0, 'system:notice:list', 'bell', 1, 0),
    (121, NULL, '操作日志', 100, '0,100', 12, 12, 'operlog', 'system/operlog/index', NULL, 'C', 0, 0, 'system:operlog:list', 'document', 1, 0),
    (122, NULL, '登录日志', 100, '0,100', 13, 13, 'loginlog', 'system/loginlog/index', NULL, 'C', 0, 0, 'system:loginlog:list', 'tickets', 1, 0),
    (123, NULL, '在线用户', 100, '0,100', 14, 14, 'online', 'system/online/index', NULL, 'C', 0, 0, 'monitor:online:list', 'connection', 1, 0),
    (124, NULL, '服务监控', 100, '0,100', 15, 15, 'server', 'system/server/index', NULL, 'C', 0, 0, 'system:monitor:server', 'monitor', 1, 0),
    (125, NULL, '缓存监控', 100, '0,100', 16, 16, 'cache-monitor', 'system/cache-monitor/index', NULL, 'C', 0, 0, 'system:monitor:cache', 'coin', 1, 0),
    (126, NULL, 'OSS配置', 100, '0,100', 17, 17, 'oss-config', 'system/oss-config/index', NULL, 'C', 0, 0, 'system:oss:list', 'upload', 1, 0),
    (210, NULL, '定时任务', 200, '0,200', 1, 1, 'list', 'job/index', NULL, 'C', 0, 0, 'job:list', 'clock', 1, 0),
    (211, NULL, '调度日志', 200, '0,200', 2, 2, 'log', 'job/log', NULL, 'C', 0, 0, 'job:log:list', 'document', 1, 0),
    (310, NULL, '流程定义', 300, '0,300', 1, 1, 'definition', 'workflow/definition/index', NULL, 'C', 0, 0, 'workflow:definition:list', 'document', 1, 0),
    (311, NULL, '流程实例', 300, '0,300', 2, 2, 'instance', 'workflow/instance/index', NULL, 'C', 0, 0, 'workflow:instance:list', 'histogram', 1, 0),
    (312, NULL, '待办任务', 300, '0,300', 3, 3, 'todo', 'workflow/task/index', NULL, 'C', 0, 0, 'workflow:task:todo', 'bell', 1, 0),
    (313, NULL, '已办任务', 300, '0,300', 4, 4, 'done', 'workflow/task/done', NULL, 'C', 0, 0, 'workflow:task:done', 'finished', 1, 0),
    (410, NULL, '应用管理', 400, '0,400', 1, 1, 'app', 'open/app/index', NULL, 'C', 0, 0, 'open:app:list', 'grid', 1, 0),
    (510, NULL, 'AI模型管理', 500, '0,500', 1, 1, 'model', 'ai/model/index', NULL, 'C', 0, 0, 'ai:model:list', 'cpu', 1, 0),
    (511, NULL, '知识库', 500, '0,500', 2, 2, 'knowledge', 'ai/knowledge/index', NULL, 'C', 0, 0, 'ai:kb:list', 'collection', 1, 0),
    (512, NULL, 'MCP管理', 500, '0,500', 3, 3, 'mcp', 'ai/mcp/index', NULL, 'C', 0, 0, 'ai:mcp:list', 'link', 1, 0),
    (513, NULL, '智能体', 500, '0,500', 4, 4, 'agent', 'ai/agent/index', NULL, 'C', 0, 0, 'ai:agent:list', 'user-filled', 1, 0),
    (514, NULL, 'AI工作流', 500, '0,500', 5, 5, 'workflow', 'ai/workflow/index', NULL, 'C', 0, 0, 'ai:workflow:list', 'chat-dot-round', 1, 0),
    (515, NULL, 'Prompt模板', 500, '0,500', 6, 6, 'prompt', 'ai/prompt/index', NULL, 'C', 0, 0, 'ai:prompt:list', 'document', 1, 0),
    (516, NULL, 'Token统计', 500, '0,500', 7, 7, 'token', 'ai/token/index', NULL, 'C', 0, 0, 'ai:token:stats', 'data-analysis', 1, 0),
    (517, NULL, 'AI对话', 500, '0,500', 8, 8, 'chat', 'ai/chat/index', NULL, 'C', 0, 0, NULL, 'chat-line-square', 1, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id
FROM sys_menu
WHERE id BETWEEN 100 AND 517
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_menu', 'id'),
    COALESCE((SELECT MAX(id) FROM sys_menu), 1),
    true
);
