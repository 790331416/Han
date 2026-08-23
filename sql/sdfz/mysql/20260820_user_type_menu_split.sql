-- 用户管理：明确区分系统用户与客户端用户；复用现有权限和接口，不新增账号字段。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_menu
SET menu_name = '系统用户'
WHERE id = 100 AND perms = 'system:user:list' AND del_flag = 0;

UPDATE sys_menu
SET menu_name = '客户端用户'
WHERE id = 202608110003 AND perms = 'education:person:list' AND del_flag = 0;

UPDATE sys_menu SET perms = 'education:region:list'
WHERE id = 202608170018 AND perms = 'education:region:manage' AND del_flag = 0;

UPDATE sys_menu SET perms = 'education:scope:list'
WHERE id = 202608170014 AND perms = 'education:scope:manage' AND del_flag = 0;

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, perms, icon, sort, visible, status, is_frame, is_cache)
VALUES
  (202608200020, 202608170018, '0,202608110000,202608170018', '区域新增', 'F', 'education:region:add', '#', 1, 0, 0, 1, 0),
  (202608200021, 202608170018, '0,202608110000,202608170018', '区域修改', 'F', 'education:region:edit', '#', 2, 0, 0, 1, 0),
  (202608200022, 202608170014, '0,202608110000,202608170014', '范围授权', 'F', 'education:scope:edit', '#', 1, 0, 0, 1, 0),
  (202608200023, 201, '0,2,201', '操作日志详情', 'F', 'monitor:operlog:query', '#', 3, 0, 0, 1, 0),
  (202608200024, 203, '0,2,203', 'Redis信息', 'F', 'monitor:cache:info', '#', 1, 0, 0, 1, 0),
  (202608200025, 204, '0,2,204', 'JVM信息', 'F', 'monitor:server:jvm', '#', 1, 0, 0, 1, 0),
  (202608200026, 204, '0,2,204', '服务器信息', 'F', 'monitor:server:system', '#', 2, 0, 0, 1, 0),
  (202608200027, 202608110003, '0,202608110000,202608110003', '重置客户端密码', 'F', 'education:person:resetPwd', '#', 4, 0, 0, 1, 0)
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
  menu_type = VALUES(menu_type), perms = VALUES(perms), icon = VALUES(icon), sort = VALUES(sort),
  visible = VALUES(visible), status = VALUES(status), is_frame = VALUES(is_frame), is_cache = VALUES(is_cache);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.id IN (202608200024, 202608200025, 202608200026, 202608200027)
WHERE role.role_key = 'admin' AND role.del_flag = 0;

COMMIT;
