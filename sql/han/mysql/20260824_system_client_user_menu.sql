-- 同一 sys_user 表的两个受控入口：系统用户完整管理，客户端用户仅查询和重置密码。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_menu SET menu_name = '系统用户'
WHERE id = 100 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 1,
    ancestors = '0',
    menu_name = '客户端用户',
    path = 'client-user',
    component = 'system/client-user/index',
    perms = NULL,
    menu_type = 'C',
    status = 0,
    visible = 0
WHERE id = 202608110003 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003,
    ancestors = '0,202608110003',
    menu_name = '客户端用户列表',
    perms = 'system:client-user:list',
    status = 0,
    visible = 0
WHERE id = 202608210120 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003,
    ancestors = '0,202608110003',
    menu_name = '客户端用户重置密码',
    perms = 'system:client-user:resetPwd',
    status = 0,
    visible = 0
WHERE id = 202608200027 AND del_flag = 0;

UPDATE sys_menu
SET ancestors = '0,202608110003', visible = 1, status = 1
WHERE id IN (202608110031, 202608110032, 202608120031, 202608190001)
  AND del_flag = 0;

-- 普通管理员移除系统用户及旧教育人员编辑权限，只保留客户端用户查询和密码重置。
DELETE rm
FROM sys_role_menu rm
JOIN sys_role r ON r.id = rm.role_id
JOIN sys_menu m ON m.id = rm.menu_id
WHERE r.role_key = 'common'
  AND r.del_flag = 0
  AND (m.id = 100 OR FIND_IN_SET('100', m.ancestors)
       OR m.id = 202608110003 OR FIND_IN_SET('202608110003', m.ancestors));

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (202608110003, 202608210120, 202608200027)
WHERE r.role_key = 'common' AND r.del_flag = 0;

-- 超级管理员可同时访问系统用户和客户端用户。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (100, 202608210100, 1007, 202608110003, 202608210120, 202608200027)
WHERE r.role_key = 'admin' AND r.del_flag = 0;

COMMIT;
