-- 教育用户管理与客户端用户管理分离：人员页负责建号/重绑/重置密码，客户端页仅负责查询/解绑。
SET NAMES utf8mb4;
START TRANSACTION;

-- 恢复教育管理下的用户（人员）入口，复用原始人员菜单及其既有操作权限。
UPDATE sys_menu
SET parent_id = 202608110000, ancestors = '0,202608110000', menu_name = '用户管理',
    path = 'person', component = 'education/person/index', perms = NULL,
    menu_type = 'C', icon = 'user', sort = 3, visible = 0, status = 0, permission_type = 'PAGE'
WHERE id = 202608110003 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003, ancestors = '0,202608110000,202608110003', menu_name = '用户列表',
    perms = 'education:person:list', menu_type = 'F', visible = 0, status = 0, permission_type = 'QUERY'
WHERE id = 202608210120 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003, ancestors = '0,202608110000,202608110003', menu_name = '人员导入',
    perms = 'education:person:import', menu_type = 'F', visible = 0, status = 0, permission_type = 'OPERATION'
WHERE id = 202608190001 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003, ancestors = '0,202608110000,202608110003', menu_name = '人员重置密码',
    perms = 'education:person:resetPwd', menu_type = 'F', visible = 0, status = 0, permission_type = 'OPERATION'
WHERE id = 202608200027 AND del_flag = 0;

UPDATE sys_menu
SET parent_id = 202608110003, ancestors = '0,202608110000,202608110003', visible = 0, status = 0
WHERE perms IN ('education:person:add', 'education:person:edit', 'education:person:remove') AND del_flag = 0;

-- 独立的客户端用户入口，禁止新增、编辑、删除、导入及重置密码。
INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status, is_frame, is_cache, permission_type)
VALUES
    (202608240001, 1, '0,1', '客户端用户管理', 'C', 'client-user', 'system/client-user/index', NULL, 'user', 8, 0, 0, 1, 0, 'PAGE'),
    (202608240002, 202608240001, '0,1,202608240001', '客户端用户列表', 'F', NULL, NULL, 'system:client-user:list', '#', 1, 0, 0, 1, 0, 'QUERY'),
    (202608240003, 202608240001, '0,1,202608240001', '客户端用户解绑', 'F', NULL, NULL, 'system:client-user:unbind', '#', 2, 0, 0, 1, 0, 'OPERATION')
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id), ancestors = VALUES(ancestors), menu_name = VALUES(menu_name),
    menu_type = VALUES(menu_type), path = VALUES(path), component = VALUES(component), perms = VALUES(perms),
    icon = VALUES(icon), sort = VALUES(sort), visible = VALUES(visible), status = VALUES(status),
    is_frame = VALUES(is_frame), is_cache = VALUES(is_cache), permission_type = VALUES(permission_type);

-- 超级管理员拥有两套入口；普通管理员从教育用户页完成日常维护，客户端页仅可查询。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (m.id IN (
    1, 100, 202608210100, 1007, 202608110000,
    202608110003, 202608210120, 202608110031, 202608110032, 202608190001, 202608200027,
    202608240001, 202608240002, 202608240003
) OR m.perms = 'education:person:remove')
WHERE r.role_key = 'admin' AND r.del_flag = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (m.id IN (
    1, 202608110000, 202608110003, 202608210120, 202608110031, 202608110032, 202608190001, 202608200027,
    202608240001, 202608240002
) OR m.perms = 'education:person:remove')
WHERE r.role_key = 'common' AND r.del_flag = 0;

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.perms = 'system:client-user:resetPwd';

COMMIT;
