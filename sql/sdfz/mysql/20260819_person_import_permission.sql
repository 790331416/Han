-- 人员 Excel 导入权限增量（MySQL 8.4）
SET NAMES utf8mb4;

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608190001, parent.id, CONCAT(parent.ancestors, ',', parent.id),
       '人员导入', 'F', '', NULL, 'education:person:import', '#', 4, 0, 0
FROM sys_menu parent
WHERE parent.id = (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:person:list')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:person:import');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms = 'education:person:import'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );
