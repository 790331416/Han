-- 开放平台管理端菜单与按钮权限。
-- 复用开放平台父菜单（id=5），仅给 admin 角色关联；普通厂商用户不自动获得管理权限。
SET NAMES utf8mb4;

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, permission_type)
VALUES
  (502, 5, '0,5', '厂商管理', 'C', 'vendor', 'open/vendor/index', 'open:vendor:list', 'user', 3, 0, 0, 'PAGE'),
  (5201, 502, '0,5,502', '厂商查询', 'F', NULL, NULL, 'open:vendor:query', '#', 1, 0, 0, 'QUERY'),
  (5202, 502, '0,5,502', '厂商审核', 'F', NULL, NULL, 'open:vendor:review', '#', 2, 0, 0, 'OPERATION'),
  (5203, 502, '0,5,502', '厂商管理', 'F', NULL, NULL, 'open:vendor:manage', '#', 3, 0, 0, 'OPERATION'),
  (503, 5, '0,5', '授权审批', 'C', 'authorization', 'open/authorization/index', 'open:grant:query', 'lock', 4, 0, 0, 'PAGE'),
  (5301, 503, '0,5,503', '授权查询', 'F', NULL, NULL, 'open:grant:query', '#', 1, 0, 0, 'QUERY'),
  (5302, 503, '0,5,503', '授权审核', 'F', NULL, NULL, 'open:grant:review', '#', 2, 0, 0, 'OPERATION'),
  (5303, 503, '0,5,503', '授权撤销', 'F', NULL, NULL, 'open:grant:revoke', '#', 3, 0, 0, 'OPERATION'),
  (5304, 503, '0,5,503', '凭证查询', 'F', NULL, NULL, 'open:credential:query', '#', 4, 0, 0, 'QUERY'),
  (5305, 503, '0,5,503', '凭证管理', 'F', NULL, NULL, 'open:credential:manage', '#', 5, 0, 0, 'OPERATION')
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
  icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status),
  permission_type = VALUES(permission_type);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (502, 5201, 5202, 5203, 503, 5301, 5302, 5303, 5304, 5305)
WHERE role.role_key = 'admin' AND role.del_flag = 0;
