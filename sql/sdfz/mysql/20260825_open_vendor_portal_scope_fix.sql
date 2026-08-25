-- 厂商门户权限收口：移除 openVendor 的开放平台管理端菜单，保留自服务门户。
-- 学校数据权限由平台管理员在“开放平台 -> 应用管理 -> 授权学校”配置，
-- 不给厂商角色开放教育组织树权限。
SET NAMES utf8mb4;

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
WHERE role.tenant_id = 1
  AND role.role_key = 'openVendor'
  AND role.del_flag = 0;
