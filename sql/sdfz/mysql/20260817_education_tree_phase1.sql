-- 巴蜀云校：教育组织树、学年、授权范围与升级批次第一阶段（MySQL 8.0+）
--
-- 范围：仅增加教育基础数据承载结构与必要索引；不迁移旧数据、不删除字段、不改变既有业务主键。
-- 前置：在 `han` 逻辑库执行；执行前备份 edu_school、edu_class、edu_room、edu_person_class、edu_semester。
-- 回滚：本阶段上线前若需撤回，按信息架构逐项删除本脚本新增的空表、索引和列；
--       已录入树、学年或升级数据后不得直接回滚，应先导出并确认引用关系。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS edu_region (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    ancestors VARCHAR(1024) NOT NULL DEFAULT '0',
    node_level INT NOT NULL DEFAULT 0,
    region_code VARCHAR(64) NOT NULL,
    region_name VARCHAR(128) NOT NULL,
    region_level VARCHAR(32) NOT NULL DEFAULT 'PROJECT',
    sort INT NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_region_code VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, region_code, NULL)) STORED,
    UNIQUE KEY uq_edu_region_active_code (tenant_id, active_region_code),
    KEY idx_edu_region_parent (tenant_id, parent_id, status, del_flag)
) COMMENT='教育行政区与项目区域树；不承载学校、班级、场所业务数据';

CREATE TABLE IF NOT EXISTS edu_academic_year (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    year_code VARCHAR(32) NOT NULL,
    year_name VARCHAR(64) NOT NULL,
    begin_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_year_code VARCHAR(32) GENERATED ALWAYS AS (IF(del_flag = 0, year_code, NULL)) STORED,
    UNIQUE KEY uq_edu_academic_year_active_code (tenant_id, active_year_code),
    KEY idx_edu_academic_year_status (tenant_id, status, begin_date, del_flag),
    CONSTRAINT chk_edu_academic_year_dates CHECK (end_date >= begin_date)
) COMMENT='租户统一学年；学校是否已完成升级由升级批次管理';

CREATE TABLE IF NOT EXISTS edu_user_scope (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_id BIGINT NOT NULL,
    include_children SMALLINT NOT NULL DEFAULT 1,
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_scope_key VARCHAR(128) GENERATED ALWAYS AS (
        IF(del_flag = 0, CONCAT(scope_type, ':', scope_id), NULL)
    ) STORED,
    UNIQUE KEY uq_edu_user_scope_active (tenant_id, user_id, active_scope_key),
    KEY idx_edu_user_scope_user (tenant_id, user_id, status, del_flag)
) COMMENT='教育管理端用户可操作的教育局或学校范围；不替代 sys_dept';

CREATE TABLE IF NOT EXISTS edu_grade_promotion_batch (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    source_academic_year_id BIGINT NOT NULL,
    target_academic_year_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    idempotency_key VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    confirmed_by BIGINT,
    confirmed_at TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_idempotency_key VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, idempotency_key, NULL)) STORED,
    UNIQUE KEY uq_edu_promotion_batch_idempotency (tenant_id, active_idempotency_key),
    KEY idx_edu_promotion_batch_school (tenant_id, school_id, source_academic_year_id, target_academic_year_id, status, del_flag)
) COMMENT='按学校执行的学年升级批次；草稿、确认、执行和更正均留痕';

CREATE TABLE IF NOT EXISTS edu_grade_promotion_item (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    source_class_id BIGINT,
    target_class_id BIGINT,
    action VARCHAR(16) NOT NULL,
    result_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(500),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    active_person_id BIGINT GENERATED ALWAYS AS (IF(del_flag = 0, person_id, NULL)) STORED,
    UNIQUE KEY uq_edu_promotion_item_person (tenant_id, batch_id, active_person_id),
    KEY idx_edu_promotion_item_result (tenant_id, batch_id, result_status, del_flag)
) COMMENT='升级批次逐人明细；记录升学、留级、跳级、转班、转出和毕业结果';

DROP PROCEDURE IF EXISTS sdfz_education_add_column_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_education_add_column_if_absent(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @education_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE education_stmt FROM @education_ddl;
        EXECUTE education_stmt;
        DEALLOCATE PREPARE education_stmt;
    END IF;
END$$
DELIMITER ;

-- 组织树：既有 parent_id 继续有效；ancestors/node_level 由服务端在写入时统一维护。
CALL sdfz_education_add_column_if_absent('edu_school', 'ancestors', "VARCHAR(1024) NOT NULL DEFAULT '0' COMMENT '祖先 ID 路径，不含自身'");
CALL sdfz_education_add_column_if_absent('edu_school', 'node_level', 'INT NOT NULL DEFAULT 0 COMMENT ''树层级，由服务端计算''');
CALL sdfz_education_add_column_if_absent('edu_school', 'org_type', "VARCHAR(32) NOT NULL DEFAULT 'SCHOOL' COMMENT 'EDU_BUREAU 或 SCHOOL'");
CALL sdfz_education_add_column_if_absent('edu_school', 'school_manage_type', 'VARCHAR(32) NULL COMMENT ''CENTER、CAMPUS 或 INDEPENDENT''');
CALL sdfz_education_add_column_if_absent('edu_school', 'school_property', 'VARCHAR(32) NULL COMMENT ''学校学制字典编码''');
CALL sdfz_education_add_column_if_absent('edu_school', 'region_id', 'BIGINT NULL COMMENT ''所属 edu_region''');
CALL sdfz_education_add_column_if_absent('edu_school', 'auto_upgrade_enabled', 'SMALLINT NOT NULL DEFAULT 1 COMMENT ''是否生成升级候选，非自动确认''');

-- 教学树：存量扁平班级先作为 CLASS 根节点；迁移确认后再补 parent_id/academic_year_id。
CALL sdfz_education_add_column_if_absent('edu_class', 'parent_id', 'BIGINT NULL COMMENT ''教学树父节点''');
CALL sdfz_education_add_column_if_absent('edu_class', 'ancestors', "VARCHAR(1024) NOT NULL DEFAULT '0' COMMENT '祖先 ID 路径，不含自身'");
CALL sdfz_education_add_column_if_absent('edu_class', 'node_level', 'INT NOT NULL DEFAULT 0 COMMENT ''树层级，由服务端计算''');
CALL sdfz_education_add_column_if_absent('edu_class', 'node_type', "VARCHAR(16) NOT NULL DEFAULT 'CLASS' COMMENT 'GRADE、MAJOR 或 CLASS'");
CALL sdfz_education_add_column_if_absent('edu_class', 'academic_year_id', 'BIGINT NULL COMMENT ''所属 edu_academic_year''');
CALL sdfz_education_add_column_if_absent('edu_class', 'cohort_year', 'INT NULL COMMENT ''入学届别，例如 2026''');
CALL sdfz_education_add_column_if_absent('edu_class', 'branch_code', 'VARCHAR(32) NULL COMMENT ''年级稳定编码，例如 G010''');

-- 场所树：存量教室先作为 PLACE 根节点；不自动推断建筑和楼层。
CALL sdfz_education_add_column_if_absent('edu_room', 'parent_id', 'BIGINT NULL COMMENT ''场所树父节点''');
CALL sdfz_education_add_column_if_absent('edu_room', 'ancestors', "VARCHAR(1024) NOT NULL DEFAULT '0' COMMENT '祖先 ID 路径，不含自身'");
CALL sdfz_education_add_column_if_absent('edu_room', 'node_level', 'INT NOT NULL DEFAULT 0 COMMENT ''树层级，由服务端计算''');
CALL sdfz_education_add_column_if_absent('edu_room', 'node_type', "VARCHAR(16) NOT NULL DEFAULT 'PLACE' COMMENT 'BUILDING、FLOOR 或 PLACE'");
CALL sdfz_education_add_column_if_absent('edu_room', 'alias_name', 'VARCHAR(128) NULL COMMENT ''场所别名，例如音乐教室''');
CALL sdfz_education_add_column_if_absent('edu_room', 'capacity', 'INT NULL COMMENT ''容纳人数''');
CALL sdfz_education_add_column_if_absent('edu_room', 'longitude', 'DECIMAL(10,6) NULL COMMENT ''经度''');
CALL sdfz_education_add_column_if_absent('edu_room', 'latitude', 'DECIMAL(10,6) NULL COMMENT ''纬度''');
CALL sdfz_education_add_column_if_absent('edu_room', 'data_source', 'VARCHAR(32) NULL COMMENT ''数据来源补充字段''');

-- 历史人员归属：不触碰旧关系，先补学年、状态和有效期承载能力。
CALL sdfz_education_add_column_if_absent('edu_person_class', 'academic_year_id', 'BIGINT NULL COMMENT ''所属 edu_academic_year''');
CALL sdfz_education_add_column_if_absent('edu_person_class', 'membership_status', "VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、COMPLETED、TRANSFERRED 或 GRADUATED'");
CALL sdfz_education_add_column_if_absent('edu_person_class', 'effective_start_at', 'TIMESTAMP NULL COMMENT ''实际生效时间''');
CALL sdfz_education_add_column_if_absent('edu_person_class', 'effective_end_at', 'TIMESTAMP NULL COMMENT ''实际结束时间''');
CALL sdfz_education_add_column_if_absent('edu_person_class', 'promotion_batch_id', 'BIGINT NULL COMMENT ''来源 edu_grade_promotion_batch''');

-- 现有学期继续作为订单主键，补学年关联，不新建第二套校历。
CALL sdfz_education_add_column_if_absent('edu_semester', 'academic_year_id', 'BIGINT NULL COMMENT ''所属 edu_academic_year''');

DROP PROCEDURE IF EXISTS sdfz_education_add_column_if_absent;

DROP PROCEDURE IF EXISTS sdfz_education_add_index_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_education_add_index_if_absent(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) THEN
        SET @education_ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE education_stmt FROM @education_ddl;
        EXECUTE education_stmt;
        DEALLOCATE PREPARE education_stmt;
    END IF;
END$$
DELIMITER ;

CALL sdfz_education_add_index_if_absent('edu_school', 'idx_edu_school_region',
    'KEY `idx_edu_school_region` (`tenant_id`, `region_id`, `status`, `del_flag`)');
CALL sdfz_education_add_index_if_absent('edu_class', 'idx_edu_class_tree',
    'KEY `idx_edu_class_tree` (`tenant_id`, `school_id`, `academic_year_id`, `parent_id`, `status`, `del_flag`)');
CALL sdfz_education_add_index_if_absent('edu_room', 'idx_edu_room_tree',
    'KEY `idx_edu_room_tree` (`tenant_id`, `school_id`, `parent_id`, `status`, `del_flag`)');
CALL sdfz_education_add_index_if_absent('edu_person_class', 'idx_edu_person_class_year',
    'KEY `idx_edu_person_class_year` (`tenant_id`, `person_id`, `academic_year_id`, `membership_status`, `del_flag`)');
CALL sdfz_education_add_index_if_absent('edu_semester', 'idx_edu_semester_year',
    'KEY `idx_edu_semester_year` (`tenant_id`, `academic_year_id`, `current_flag`, `status`, `del_flag`)');

DROP PROCEDURE IF EXISTS sdfz_education_add_index_if_absent;

-- 学年管理菜单与按钮权限。沿用教育模块既有权限模型；重复执行按 perms 跳过。
SET @education_root_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:manage');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608170001, @education_root_id, CONCAT('0,', @education_root_id),
       '学年管理', 'C', 'academic-year', 'education/academic-year/index',
       'education:academic-year:list', 'Calendar', 6, 0, 0
WHERE @education_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:academic-year:list');

SET @education_academic_year_menu_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:academic-year:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @education_academic_year_menu_id,
       CONCAT('0,', @education_root_id, ',', @education_academic_year_menu_id),
       source.menu_name, 'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608170011 AS id, '学年新增' AS menu_name, 'education:academic-year:add' AS perms, 1 AS sort
    UNION ALL SELECT 202608170012, '学年修改', 'education:academic-year:edit', 2
    UNION ALL SELECT 202608170013, '学年删除', 'education:academic-year:remove', 3
) source
WHERE @education_academic_year_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms LIKE 'education:academic-year:%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608170014, @education_root_id, CONCAT('0,', @education_root_id),
       '数据范围授权', 'C', 'scope', 'education/scope/index', 'education:scope:manage', 'Lock', 8, 0, 0
WHERE @education_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:scope:manage');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms = 'education:scope:manage'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608170015, @education_root_id, CONCAT('0,', @education_root_id),
       '学年升级', 'C', 'promotion', 'education/promotion/index', 'education:promotion:list', 'Top', 9, 0, 0
WHERE @education_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:promotion:list');

SET @education_promotion_menu_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:promotion:list');

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT source.id, @education_promotion_menu_id,
       CONCAT('0,', @education_root_id, ',', @education_promotion_menu_id),
       source.menu_name, 'F', '', NULL, source.perms, '#', source.sort, 0, 0
FROM (
    SELECT 202608170016 AS id, '创建升级预览' AS menu_name, 'education:promotion:preview' AS perms, 1 AS sort
    UNION ALL SELECT 202608170017, '确认升级执行', 'education:promotion:confirm', 2
) source
WHERE @education_promotion_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu menu WHERE menu.perms = source.perms);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms LIKE 'education:promotion:%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608170018, @education_root_id, CONCAT('0,', @education_root_id),
       '区域管理', 'C', 'region', 'education/region/index', 'education:region:manage', 'Location', 7, 0, 0
WHERE @education_root_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:region:manage');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id
FROM sys_menu menu
WHERE menu.perms = 'education:region:manage'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu role_menu
      WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id
  );

-- 教育组织下拉选项：保留为系统级字典，租户可在管理端按需停用或扩展。
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, del_flag, remark)
SELECT source.id, NULL, source.dict_name, source.dict_type, 0, 0, '教育基础数据一期'
FROM (
    SELECT 202608170101 AS id, '教育机构类型' AS dict_name, 'edu_org_type' AS dict_type
    UNION ALL SELECT 202608170102, '学校管理类型', 'edu_school_manage_type'
    UNION ALL SELECT 202608170103, '学校办学学制', 'edu_school_property'
    UNION ALL SELECT 202608170104, '教育年级', 'edu_grade'
) source
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type item
    WHERE item.dict_type = source.dict_type AND item.tenant_id IS NULL AND item.del_flag = 0
);

INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, is_default, status, del_flag, remark)
SELECT source.id, NULL, source.dict_type, source.dict_label, source.dict_value, source.dict_sort, 0, 0, 0, '教育基础数据一期'
FROM (
    SELECT 202608170201 AS id, 'edu_org_type' AS dict_type, '教育局' AS dict_label, 'EDU_BUREAU' AS dict_value, 1 AS dict_sort
    UNION ALL SELECT 202608170202, 'edu_org_type', '学校', 'SCHOOL', 2
    UNION ALL SELECT 202608170211, 'edu_school_manage_type', '中心校', 'CENTER', 1
    UNION ALL SELECT 202608170212, 'edu_school_manage_type', '校区', 'CAMPUS', 2
    UNION ALL SELECT 202608170213, 'edu_school_manage_type', '独立学校', 'INDEPENDENT', 3
    UNION ALL SELECT 202608170221, 'edu_school_property', '幼儿园', '1', 1
    UNION ALL SELECT 202608170222, 'edu_school_property', '小学', '2', 2
    UNION ALL SELECT 202608170223, 'edu_school_property', '初中', '3', 3
    UNION ALL SELECT 202608170224, 'edu_school_property', '高中', '4', 4
    UNION ALL SELECT 202608170225, 'edu_school_property', '九年制', '5', 5
    UNION ALL SELECT 202608170226, 'edu_school_property', '小学附属幼儿园', '6', 6
    UNION ALL SELECT 202608170227, 'edu_school_property', '完全中学', '7', 7
    UNION ALL SELECT 202608170228, 'edu_school_property', '十二年制学校', '8', 8
    UNION ALL SELECT 202608170229, 'edu_school_property', '完全学校（幼到高）', '9', 9
    UNION ALL SELECT 202608170230, 'edu_school_property', '幼小初', '10', 10
    UNION ALL SELECT 202608170231, 'edu_grade', '小班', 'G001', 1
    UNION ALL SELECT 202608170232, 'edu_grade', '中班', 'G002', 2
    UNION ALL SELECT 202608170233, 'edu_grade', '大班', 'G003', 3
    UNION ALL SELECT 202608170234, 'edu_grade', '一年级', 'G004', 4
    UNION ALL SELECT 202608170235, 'edu_grade', '二年级', 'G005', 5
    UNION ALL SELECT 202608170236, 'edu_grade', '三年级', 'G006', 6
    UNION ALL SELECT 202608170237, 'edu_grade', '四年级', 'G007', 7
    UNION ALL SELECT 202608170238, 'edu_grade', '五年级', 'G008', 8
    UNION ALL SELECT 202608170239, 'edu_grade', '六年级', 'G009', 9
    UNION ALL SELECT 202608170240, 'edu_grade', '七年级', 'G010', 10
    UNION ALL SELECT 202608170241, 'edu_grade', '八年级', 'G011', 11
    UNION ALL SELECT 202608170242, 'edu_grade', '九年级', 'G012', 12
    UNION ALL SELECT 202608170243, 'edu_grade', '高一年级', 'G013', 13
    UNION ALL SELECT 202608170244, 'edu_grade', '高二年级', 'G014', 14
    UNION ALL SELECT 202608170245, 'edu_grade', '高三年级', 'G015', 15
    UNION ALL SELECT 202608170246, 'edu_grade', '学前班', 'G920', 16
    UNION ALL SELECT 202608170247, 'edu_grade', '毕业年级', 'G930', 17
    UNION ALL SELECT 202608170248, 'edu_grade', '其他年级', 'G940', 18
) source
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data item
    WHERE item.dict_type = source.dict_type AND item.dict_value = source.dict_value
      AND item.tenant_id IS NULL AND item.del_flag = 0
);

-- 本脚本只新增结构。以下动作必须在数据确认和回归通过后由后续脚本完成：
-- 1. 根据真实父子关系回填 ancestors/node_level，禁止按名称猜树；
-- 2. 为存量班级、人员关系和学期确认 academic_year_id；
-- 3. 将旧扁平唯一索引调整为“学校 + 学年 + 父节点 + 编码”的有效行唯一索引；
-- 4. 启用外部校端兼容接口的当前学年过滤与学校范围校验。
