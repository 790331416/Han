-- 附中三课堂教育主数据（MySQL 8.4）
-- 运行时设备在线状态、心跳、课堂控制和视频数据不进入这些表。
--
-- 必须保留下面这行：手工用 mysql 客户端执行时，若会话字符集不是 utf8mb4，
-- 脚本里的中文菜单名会被按客户端默认字符集二次编码后入库，表现为乱码。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS edu_school (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    school_code VARCHAR(64) NOT NULL,
    school_name VARCHAR(128) NOT NULL,
    school_role VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    area_code VARCHAR(32),
    status SMALLINT NOT NULL DEFAULT 0,
    sync_hash VARCHAR(64),
    last_sync_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_school_code (tenant_id, school_code),
    UNIQUE KEY uq_edu_school_external (tenant_id, source_system, external_id),
    KEY idx_edu_school_parent (tenant_id, parent_id, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_class (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    grade_code VARCHAR(32),
    class_code VARCHAR(64) NOT NULL,
    class_name VARCHAR(128) NOT NULL,
    class_role VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    sync_hash VARCHAR(64),
    last_sync_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_class_code (tenant_id, school_id, class_code),
    UNIQUE KEY uq_edu_class_external (tenant_id, source_system, external_id),
    KEY idx_edu_class_school (tenant_id, school_id, status, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_person (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    school_id BIGINT NOT NULL,
    person_no VARCHAR(64) NOT NULL,
    person_name VARCHAR(128) NOT NULL,
    person_type VARCHAR(16) NOT NULL,
    phone VARCHAR(20),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_user_id VARCHAR(128),
    external_identity_id VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    sync_hash VARCHAR(64),
    last_sync_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_person_no (tenant_id, school_id, person_no),
    UNIQUE KEY uq_edu_person_external (tenant_id, source_system, external_identity_id),
    KEY idx_edu_person_user (tenant_id, user_id, del_flag),
    KEY idx_edu_person_school (tenant_id, school_id, person_type, status, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_person_class (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    membership_role VARCHAR(32) NOT NULL,
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_edu_person_class (tenant_id, person_id, class_id, membership_role),
    KEY idx_edu_person_class_class (tenant_id, class_id, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_subject_code (tenant_id, subject_code),
    UNIQUE KEY uq_edu_subject_external (tenant_id, source_system, external_id)
);

CREATE TABLE IF NOT EXISTS edu_person_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    class_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_edu_person_subject (tenant_id, person_id, subject_id, class_id),
    KEY idx_edu_person_subject_subject (tenant_id, subject_id, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_room (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    room_code VARCHAR(64) NOT NULL,
    room_name VARCHAR(128) NOT NULL,
    room_type VARCHAR(32),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_room_code (tenant_id, school_id, room_code),
    UNIQUE KEY uq_edu_room_external (tenant_id, source_system, external_id)
);

CREATE TABLE IF NOT EXISTS edu_device (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    room_id BIGINT,
    device_code VARCHAR(128) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    model VARCHAR(128),
    serial_number VARCHAR(128),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    asset_status VARCHAR(32) NOT NULL DEFAULT 'IN_SERVICE',
    status SMALLINT NOT NULL DEFAULT 0,
    sync_hash VARCHAR(64),
    last_sync_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_device_code (tenant_id, device_code),
    UNIQUE KEY uq_edu_device_external (tenant_id, source_system, external_id),
    KEY idx_edu_device_room (tenant_id, room_id, status, del_flag)
);

CREATE TABLE IF NOT EXISTS edu_semester (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    semester_code VARCHAR(64) NOT NULL,
    semester_name VARCHAR(128) NOT NULL,
    begin_date DATE NOT NULL,
    end_date DATE NOT NULL,
    current_flag SMALLINT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    UNIQUE KEY uq_edu_semester_code (tenant_id, semester_code),
    KEY idx_edu_semester_current (tenant_id, current_flag, status, del_flag),
    CONSTRAINT chk_edu_semester_dates CHECK (end_date >= begin_date)
);

-- 教育管理菜单与按钮权限。使用独立高位 ID，重复执行时按权限标识跳过。
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608110000, 0, '0', '教育管理', 'M', 'education', NULL, 'education:manage', 'school', 5, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:manage');

SET @edu_root_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:manage');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @edu_root_id, CONCAT('0,', @edu_root_id), source.menu_name, 'C', source.path,
       source.component, source.perms, source.icon, source.sort, 0, 0
FROM (
    SELECT 202608110001 AS id, '学校管理' AS menu_name, 'school' AS path, 'education/school/index' AS component, 'education:school:list' AS perms, 'office-building' AS icon, 1 AS sort
    UNION ALL SELECT 202608110002, '班级管理', 'class', 'education/class/index', 'education:class:list', 'collection', 2
    UNION ALL SELECT 202608110003, '人员管理', 'person', 'education/person/index', 'education:person:list', 'user', 3
    UNION ALL SELECT 202608110004, '科目管理', 'subject', 'education/subject/index', 'education:subject:list', 'notebook', 4
    UNION ALL SELECT 202608110005, '设备管理', 'device', 'education/device/index', 'education:device:list', 'monitor', 5
) source
WHERE NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

SET @edu_school_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:school:list');
SET @edu_class_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:class:list');
SET @edu_person_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:person:list');
SET @edu_subject_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:subject:list');
SET @edu_device_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:device:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, source.parent_id, CONCAT('0,', @edu_root_id, ',', source.parent_id), source.menu_name,
       'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608110011 AS id, @edu_school_id AS parent_id, '学校新增' AS menu_name, 'education:school:add' AS perms, 1 AS sort
    UNION ALL SELECT 202608110012, @edu_school_id, '学校修改', 'education:school:edit', 2
    UNION ALL SELECT 202608110021, @edu_class_id, '班级新增', 'education:class:add', 1
    UNION ALL SELECT 202608110022, @edu_class_id, '班级修改', 'education:class:edit', 2
    UNION ALL SELECT 202608110031, @edu_person_id, '人员新增', 'education:person:add', 1
    UNION ALL SELECT 202608110032, @edu_person_id, '人员修改', 'education:person:edit', 2
    UNION ALL SELECT 202608110041, @edu_subject_id, '科目新增', 'education:subject:add', 1
    UNION ALL SELECT 202608110042, @edu_subject_id, '科目修改', 'education:subject:edit', 2
    UNION ALL SELECT 202608110051, @edu_device_id, '设备新增', 'education:device:add', 1
    UNION ALL SELECT 202608110052, @edu_device_id, '设备修改', 'education:device:edit', 2
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
