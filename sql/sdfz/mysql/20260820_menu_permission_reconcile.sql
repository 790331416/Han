-- 菜单表是前后端权限的唯一来源：补齐既有页面的操作权限，并统一旧登录日志标识。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_menu
SET perms = 'system:tenant:list'
WHERE id = 400 AND perms = 'tenant:list';

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, is_frame, is_cache)
VALUES
  (202608200009, 1, '0,1', '系统设置', 'C', 'brand', 'system/brand/index', 'system:brand:query', 'setting', 9, 0, 0, 1, 0),
  (202608200011, 0, '0', '首页', 'C', 'dashboard', 'dashboard/index', 'dashboard:view', 'house', 0, 0, 0, 1, 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
  icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status),
  is_frame = VALUES(is_frame), is_cache = VALUES(is_cache);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, perms, icon, sort, visible, status, is_frame, is_cache)
VALUES
  (202608200001, 200, '0,2,200', '强制下线', 'F', 'monitor:online:forceLogout', '#', 1, 0, 0, 1, 0),
  (202608200002, 201, '0,2,201', '删除日志', 'F', 'monitor:operlog:remove', '#', 1, 0, 0, 1, 0),
  (202608200003, 201, '0,2,201', '导出日志', 'F', 'monitor:operlog:export', '#', 2, 0, 0, 1, 0),
  (202608200004, 202, '0,2,202', '删除日志', 'F', 'monitor:loginlog:remove', '#', 1, 0, 0, 1, 0),
  (202608200005, 202, '0,2,202', '导出日志', 'F', 'monitor:loginlog:export', '#', 2, 0, 0, 1, 0),
  (202608200006, 203, '0,2,203', '删除缓存', 'F', 'monitor:cache:remove', '#', 1, 0, 0, 1, 0),
  (202608200007, 100, '0,1,100', '解绑用户', 'F', 'system:user:unbind', '#', 8, 0, 0, 1, 0),
  (202608200008, 400, '0,4,400', '编辑租户', 'F', 'system:tenant:edit', '#', 1, 0, 0, 1, 0),
  (202608200010, 202608200009, '0,1,202608200009', '修改系统设置', 'F', 'system:brand:edit', '#', 1, 0, 0, 1, 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), perms = VALUES(perms), icon = VALUES(icon), sort = VALUES(sort),
  visible = VALUES(visible), status = VALUES(status), is_frame = VALUES(is_frame), is_cache = VALUES(is_cache);

-- 超级管理员在角色编辑页也应默认勾选全部菜单；其他角色仍由菜单配置显式授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
CROSS JOIN sys_menu menu
WHERE role.role_key = 'admin'
  AND role.del_flag = 0
  AND menu.del_flag = 0;

COMMIT;
