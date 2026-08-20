-- 巴蜀云校：学校级学年、学期、科目与全国区域基准承载（MySQL 8.0+）
--
-- 前置：已执行 20260811_education_master.sql、20260812b_education_active_unique_index.sql、
--       20260817_education_tree_phase1.sql。
-- 备份：执行前备份 edu_region、edu_academic_year、edu_semester、edu_subject、sys_menu、sys_role_menu。
-- 回滚：新录入学校级数据前可删除本脚本新增列与索引；已有引用后禁止直接回滚。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdfz_edu_add_column_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_edu_add_column_if_absent(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sdfz_edu_add_column_if_absent('edu_region', 'source_system', "VARCHAR(32) NOT NULL DEFAULT 'HAN' COMMENT 'NATIONAL=全国基准，HAN=本地补充'");
CALL sdfz_edu_add_column_if_absent('edu_subject', 'school_id', 'BIGINT NULL COMMENT ''学校归属；历史租户级数据迁移前为空''');
CALL sdfz_edu_add_column_if_absent('edu_academic_year', 'school_id', 'BIGINT NULL COMMENT ''学校归属；历史租户级数据迁移前为空''');
CALL sdfz_edu_add_column_if_absent('edu_semester', 'school_id', 'BIGINT NULL COMMENT ''学校归属；历史租户级数据迁移前为空''');

DROP PROCEDURE IF EXISTS sdfz_edu_add_column_if_absent;

DROP PROCEDURE IF EXISTS sdfz_edu_swap_active_unique;
DELIMITER $$
CREATE PROCEDURE sdfz_edu_swap_active_unique(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_column_def TEXT,
    IN p_old_index VARCHAR(64), IN p_new_index VARCHAR(64), IN p_new_columns TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = p_table) THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_column_def);
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_new_index) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_new_index, '` (', p_new_columns, ')');
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_old_index) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_old_index, '`');
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$$
DELIMITER ;

-- school_id 为空时按 0 参与键，保证存量“租户统一”记录不重复；新数据按学校隔离。
CALL sdfz_edu_swap_active_unique('edu_subject', 'active_subject_school_code',
    'VARCHAR(96) GENERATED ALWAYS AS (IF(del_flag = 0, CONCAT(IFNULL(school_id, 0), '':'', subject_code), NULL)) STORED',
    'uq_edu_subject_active_code', 'uq_edu_subject_active_school_code', '`tenant_id`, `active_subject_school_code`');

CALL sdfz_edu_swap_active_unique('edu_academic_year', 'active_year_school_code',
    'VARCHAR(96) GENERATED ALWAYS AS (IF(del_flag = 0, CONCAT(IFNULL(school_id, 0), '':'', year_code), NULL)) STORED',
    'uq_edu_academic_year_active_code', 'uq_edu_academic_year_active_school_code', '`tenant_id`, `active_year_school_code`');

CALL sdfz_edu_swap_active_unique('edu_semester', 'active_semester_school_code',
    'VARCHAR(128) GENERATED ALWAYS AS (IF(del_flag = 0, CONCAT(IFNULL(school_id, 0), '':'', semester_code), NULL)) STORED',
    'uq_edu_semester_active_code', 'uq_edu_semester_active_school_code', '`tenant_id`, `active_semester_school_code`');

DROP PROCEDURE IF EXISTS sdfz_edu_swap_active_unique;

DROP PROCEDURE IF EXISTS sdfz_edu_add_index_if_absent;
DELIMITER $$
CREATE PROCEDURE sdfz_edu_add_index_if_absent(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sdfz_edu_add_index_if_absent('edu_region', 'idx_edu_region_source', 'KEY `idx_edu_region_source` (`tenant_id`, `source_system`, `status`, `del_flag`)');
CALL sdfz_edu_add_index_if_absent('edu_subject', 'idx_edu_subject_school', 'KEY `idx_edu_subject_school` (`tenant_id`, `school_id`, `status`, `del_flag`)');
CALL sdfz_edu_add_index_if_absent('edu_academic_year', 'idx_edu_academic_year_school', 'KEY `idx_edu_academic_year_school` (`tenant_id`, `school_id`, `status`, `begin_date`, `del_flag`)');
CALL sdfz_edu_add_index_if_absent('edu_semester', 'idx_edu_semester_school', 'KEY `idx_edu_semester_school` (`tenant_id`, `school_id`, `academic_year_id`, `status`, `del_flag`)');

DROP PROCEDURE IF EXISTS sdfz_edu_add_index_if_absent;

-- 区域基准导入前，既有人工区域保留为 HAN，不能被全国基准覆盖。
UPDATE edu_region SET source_system = 'HAN' WHERE source_system IS NULL OR source_system = '';

-- 给区域管理补删除按钮权限。学校、班级、人员、科目、设备、教室、学期删除权限已由 20260812 脚本创建。
SET @edu_region_menu_id := (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:region:manage');
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
SELECT 202608180001, @edu_region_menu_id,
       CONCAT('0,', (SELECT MIN(id) FROM sys_menu WHERE perms = 'education:manage'), ',', @edu_region_menu_id),
       '区域删除', 'F', '', NULL, 'education:region:remove', '#', 3, 0, 0
WHERE @edu_region_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'education:region:remove');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu.id FROM sys_menu menu
WHERE menu.perms = 'education:region:remove'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu role_menu WHERE role_menu.role_id = 1 AND role_menu.menu_id = menu.id);

-- 全国四级区域由 scripts/generate-national-region-seed.mjs 单独生成并导入。
-- 基线：2024 全国四级 CSV（省31、市343、区县3255、街道/乡镇41351，MD5=81794067ccf4660d555f725c23bfec0f）。
-- 生成前必须检查目标 tenant_id 与全局唯一 ID 基数；不能把 area_info.sql 或 t_area.sql 原样导入业务库。
