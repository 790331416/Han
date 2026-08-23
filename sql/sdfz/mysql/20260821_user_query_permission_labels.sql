-- 系统用户与客户端用户查询权限的明确中文名称。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_menu
SET menu_name = '系统用户查询'
WHERE perms = 'system:user:list' AND del_flag = 0;

UPDATE sys_menu
SET menu_name = '客户端用户查询'
WHERE perms = 'education:person:list' AND del_flag = 0;

UPDATE sys_menu
SET menu_name = '系统用户重置密码'
WHERE perms = 'system:user:resetPwd' AND del_flag = 0;

UPDATE sys_menu
SET menu_name = '客户端用户重置密码'
WHERE perms = 'education:person:resetPwd' AND del_flag = 0;

COMMIT;
