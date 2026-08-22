-- 开放平台：接口目录独立管理菜单。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, permission_type)
VALUES
  (501, 5, '0,5', '接口目录', 'C', 'api-resource', 'open/api-resource/index', 'open:api-resource:list', 'list', 2, 0, 0, 'PAGE'),
  (5101, 501, '0,5,501', '接口目录查询', 'F', NULL, NULL, 'open:api-resource:query', '#', 1, 0, 0, 'QUERY'),
  (5102, 501, '0,5,501', '接口目录新增', 'F', NULL, NULL, 'open:api-resource:add', '#', 2, 0, 0, 'OPERATION'),
  (5103, 501, '0,5,501', '接口目录修改', 'F', NULL, NULL, 'open:api-resource:edit', '#', 3, 0, 0, 'OPERATION'),
  (5104, 501, '0,5,501', '接口目录删除', 'F', NULL, NULL, 'open:api-resource:remove', '#', 4, 0, 0, 'OPERATION')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
  icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status),
  permission_type = VALUES(permission_type);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (501, 5101, 5102, 5103, 5104)
WHERE role.role_key = 'admin' AND role.del_flag = 0;

COMMIT;
