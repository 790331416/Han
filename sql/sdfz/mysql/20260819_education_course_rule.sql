-- 课表节次管理权限增量（MySQL 8.4）
SET NAMES utf8mb4;

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608190101, parent.id, CONCAT(parent.ancestors, ',', parent.id),
       '课表节次', 'C', 'course-rule', 'education/course-rule/index',
       'education:course-rule:list', 'clock', 9, 0, 0
FROM sys_menu parent
WHERE parent.perms = 'education:manage'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:course-rule:list');

SET @course_rule_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:course-rule:list');

INSERT INTO sys_menu
    (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @course_rule_id, CONCAT('0,', parent.parent_id, ',', parent.id), source.menu_name,
       'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM sys_menu parent
JOIN (
    SELECT 202608190111 AS id, '新增节次' AS menu_name, 'education:course-rule:add' AS perms, 1 AS sort
    UNION ALL SELECT 202608190112, '编辑节次', 'education:course-rule:edit', 2
    UNION ALL SELECT 202608190113, '删除节次', 'education:course-rule:remove', 3
) source ON 1 = 1
WHERE parent.id = @course_rule_id
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = source.perms);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms LIKE 'education:course-rule:%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );
