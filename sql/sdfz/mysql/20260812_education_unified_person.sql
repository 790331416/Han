-- 附中三课堂教育主数据补齐（MySQL 8.4）
--
-- 必须保留下面这行：手工用 mysql 客户端执行时，若会话字符集不是 utf8mb4，
-- 脚本里的中文菜单名和角色名会被二次编码后入库，表现为乱码。
SET NAMES utf8mb4;

-- 1. 学期与教室菜单：两张表在 20260811 已建，但当时没有管理入口。
-- 2. 各教育实体的删除按钮权限：删除为逻辑删除，删除后释放业务编码以便重建同编码记录。
-- 3. 人员统一入口新增的归班、任教授权按钮。
-- 4. 普通教师与学生角色：供人员统一入口建号时选择，均不带管理菜单。
-- 重复执行按权限标识与角色标识跳过。

SET @edu_root_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:manage');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @edu_root_id, CONCAT('0,', @edu_root_id), source.menu_name, 'C', source.path,
       source.component, source.perms, source.icon, source.sort, 0, 0
FROM (
    SELECT 202608120001 AS id, '学期管理' AS menu_name, 'semester' AS path, 'education/semester/index' AS component, 'education:semester:list' AS perms, 'calendar' AS icon, 6 AS sort
    UNION ALL SELECT 202608120002, '教室管理', 'room', 'education/room/index', 'education:room:list', 'house', 7
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

SET @edu_school_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:school:list');
SET @edu_class_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:class:list');
SET @edu_person_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:person:list');
SET @edu_subject_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:subject:list');
SET @edu_device_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:device:list');
SET @edu_semester_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:semester:list');
SET @edu_room_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:room:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, source.parent_id, CONCAT('0,', @edu_root_id, ',', source.parent_id), source.menu_name,
       'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608120011 AS id, @edu_school_id AS parent_id, '学校删除' AS menu_name, 'education:school:remove' AS perms, 3 AS sort
    UNION ALL SELECT 202608120021, @edu_class_id, '班级删除', 'education:class:remove', 3
    UNION ALL SELECT 202608120031, @edu_person_id, '人员删除', 'education:person:remove', 3
    UNION ALL SELECT 202608120033, @edu_person_id, '人员导入', 'education:person:import', 4
    UNION ALL SELECT 202608120041, @edu_subject_id, '科目删除', 'education:subject:remove', 3
    UNION ALL SELECT 202608120051, @edu_device_id, '设备删除', 'education:device:remove', 3
    UNION ALL SELECT 202608120061, @edu_semester_id, '学期新增', 'education:semester:add', 1
    UNION ALL SELECT 202608120062, @edu_semester_id, '学期修改', 'education:semester:edit', 2
    UNION ALL SELECT 202608120063, @edu_semester_id, '学期删除', 'education:semester:remove', 3
    UNION ALL SELECT 202608120071, @edu_room_id, '教室新增', 'education:room:add', 1
    UNION ALL SELECT 202608120072, @edu_room_id, '教室修改', 'education:room:edit', 2
    UNION ALL SELECT 202608120073, @edu_room_id, '教室删除', 'education:room:remove', 3
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms LIKE 'education:%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );

-- 教师与学生角色：只用于人员统一入口建号，不挂任何管理菜单，避免"来自学校"就自动获得管理权限。
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark)
SELECT source.id, 1, source.role_name, source.role_key, source.role_sort, '5', 0, source.remark
FROM (
    SELECT 202608120101 AS id, '普通教师' AS role_name, 'teacher' AS role_key, 11 AS role_sort, '三课堂普通教师，仅本人数据' AS remark
    UNION ALL SELECT 202608120102, '学生', 'student', 12, '三课堂学生，仅本人数据'
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_role role WHERE role.role_key = source.role_key AND role.tenant_id = 1);
