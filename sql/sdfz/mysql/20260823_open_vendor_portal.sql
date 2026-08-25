-- 厂商门户账号与公开申请配套菜单。
-- 仅授予 tenant=1 的 openVendor 角色门户自服务权限，不授予审核/管理员权限。
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_role
    (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
VALUES
    (202608230001, 1, '开放平台厂商', 'openVendor', 90, '5', 0, '开放平台厂商门户角色');

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, permission_type)
VALUES
    (202608230010, 5, '0,5', '厂商门户', 'C', 'portal', 'open/portal/index', 'open:vendor:my', 'link', 1, 0, 0, 'PAGE'),
    (202608230101, 202608230010, '0,5,202608230010', '我的厂商', 'F', NULL, NULL, 'open:vendor:my', '#', 1, 0, 0, 'QUERY'),
    (202608230102, 202608230010, '0,5,202608230010', '厂商申请', 'F', NULL, NULL, 'open:vendor:apply', '#', 2, 0, 0, 'OPERATION'),
    (202608230103, 202608230010, '0,5,202608230010', '厂商查询', 'F', NULL, NULL, 'open:vendor:query', '#', 3, 0, 0, 'QUERY'),
    (202608230201, 202608230010, '0,5,202608230010', '应用列表', 'F', NULL, NULL, 'open:app:list', '#', 4, 0, 0, 'QUERY'),
    (202608230202, 202608230010, '0,5,202608230010', '应用查询', 'F', NULL, NULL, 'open:app:query', '#', 5, 0, 0, 'QUERY'),
    (202608230203, 202608230010, '0,5,202608230010', '应用新增', 'F', NULL, NULL, 'open:app:add', '#', 6, 0, 0, 'OPERATION'),
    (202608230204, 202608230010, '0,5,202608230010', '应用修改', 'F', NULL, NULL, 'open:app:edit', '#', 7, 0, 0, 'OPERATION'),
    (202608230205, 202608230010, '0,5,202608230010', '应用删除', 'F', NULL, NULL, 'open:app:remove', '#', 8, 0, 0, 'OPERATION'),
    (202608230301, 202608230010, '0,5,202608230010', '授权申请', 'F', NULL, NULL, 'open:grant:apply', '#', 9, 0, 0, 'OPERATION'),
    (202608230302, 202608230010, '0,5,202608230010', '授权查询', 'F', NULL, NULL, 'open:grant:query', '#', 10, 0, 0, 'QUERY'),
    (202608230303, 202608230010, '0,5,202608230010', '授权撤销', 'F', NULL, NULL, 'open:grant:revoke', '#', 11, 0, 0, 'OPERATION'),
    (202608230401, 202608230010, '0,5,202608230010', '凭证查询', 'F', NULL, NULL, 'open:credential:query', '#', 12, 0, 0, 'QUERY'),
    (202608230402, 202608230010, '0,5,202608230010', '凭证管理', 'F', NULL, NULL, 'open:credential:manage', '#', 13, 0, 0, 'OPERATION'),
    (202608230501, 202608230010, '0,5,202608230010', '接口查询', 'F', NULL, NULL, 'open:api-resource:query', '#', 14, 0, 0, 'QUERY'),
    (202608230502, 202608230010, '0,5,202608230010', '接口列表', 'F', NULL, NULL, 'open:api-resource:list', '#', 15, 0, 0, 'QUERY')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
    icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status),
    permission_type = VALUES(permission_type);

-- openVendor 只保留厂商自服务门户。清理早期误授的开放平台管理端页面及按钮；
-- 管理员仍通过自身角色访问应用管理、厂商管理、接口目录和授权审批。
DELETE role_menu
FROM sys_role_menu role_menu
JOIN sys_role role ON role.id = role_menu.role_id
WHERE role.tenant_id = 1
  AND role.role_key = 'openVendor'
  AND role.del_flag = 0
  AND role_menu.menu_id IN (
    500, 501, 502, 503,
    5001, 5002, 5003, 5004,
    5101, 5102, 5103, 5104,
    5201, 5202, 5203,
    5301, 5304, 5305,
    202608210117
  );

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (
    5, 202608230010, 202608230101, 202608230102, 202608230103,
    202608230201, 202608230202, 202608230203, 202608230204, 202608230205,
    202608230301, 202608230302, 202608230303,
    202608230401, 202608230402, 202608230501, 202608230502
)
WHERE role.tenant_id = 1 AND role.role_key = 'openVendor' AND role.del_flag = 0;
