-- 菜单授权、前端按钮与后端接口统一使用同一权限标识。
SET NAMES utf8mb4;
START TRANSACTION;

-- 监控：基础信息、缓存键列表、删除缓存；JVM、服务器信息、完整监控分别可授权。
UPDATE sys_menu SET permission_type = 'QUERY', status = 0, visible = 0
WHERE id IN (202608200024, 202608200025, 202608200026, 202608210112, 202608210113) AND del_flag = 0;

-- 租户页面此前错误使用 system:tenant:*，实际租户服务使用 tenant:*。
UPDATE sys_menu SET perms = 'tenant:list', menu_name = '租户列表', permission_type = 'QUERY'
WHERE id = 202608210115 AND del_flag = 0;
UPDATE sys_menu SET perms = 'tenant:edit', menu_name = '编辑租户', permission_type = 'OPERATION'
WHERE id = 202608200008 AND del_flag = 0;

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, is_frame, is_cache, permission_type)
VALUES
    (202608240011, 400, '0,4,400', '租户查询', 'F', NULL, NULL, 'tenant:query', '#', 2, 0, 0, 1, 0, 'QUERY'),
    (202608240012, 400, '0,4,400', '租户新增', 'F', NULL, NULL, 'tenant:add', '#', 3, 0, 0, 1, 0, 'OPERATION'),
    (202608240013, 400, '0,4,400', '租户删除', 'F', NULL, NULL, 'tenant:remove', '#', 4, 0, 0, 1, 0, 'OPERATION'),
    (202608240021, 401, '0,4,401', '套餐查询', 'F', NULL, NULL, 'tenant:package:query', '#', 2, 0, 0, 1, 0, 'QUERY'),
    (202608240022, 401, '0,4,401', '套餐新增', 'F', NULL, NULL, 'tenant:package:add', '#', 3, 0, 0, 1, 0, 'OPERATION'),
    (202608240023, 401, '0,4,401', '套餐修改', 'F', NULL, NULL, 'tenant:package:edit', '#', 4, 0, 0, 1, 0, 'OPERATION'),
    (202608240024, 401, '0,4,401', '套餐删除', 'F', NULL, NULL, 'tenant:package:remove', '#', 5, 0, 0, 1, 0, 'OPERATION')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type), perms = VALUES(perms), sort = VALUES(sort), visible = VALUES(visible),
    status = VALUES(status), permission_type = VALUES(permission_type);

-- 无页面入口或无后端实现的旧权限不再出现在角色编辑树中。
UPDATE sys_menu SET status = 1, visible = 1
WHERE id IN (1065, 202608200005, 5005, 202608200007) AND del_flag = 0;

-- 普通管理员不承担跨用户数据范围配置，避免展示只读且无法选择人员的空页面。
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
WHERE r.role_key = 'common' AND rm.menu_id IN (202608170014, 202608210127, 202608200022);

COMMIT;
