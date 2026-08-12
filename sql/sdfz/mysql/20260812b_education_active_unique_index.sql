-- 附中三课堂：唯一索引改为只约束有效行（MySQL 8.4）
--
-- 背景：原唯一索引建在 (tenant_id, 业务编码) 上且不含 del_flag，逻辑删除留下的墓碑行仍占着唯一键。
-- 后果一：删除后无法用同编码重建，可重复 CRUD 测试做不了。
-- 后果二：edu_person_class / edu_person_subject 没有业务编码，"转出再转回"必然撞唯一键（转班场景硬失败）。
--
-- 方案：MySQL 8.4 不支持带 WHERE 的部分唯一索引，用 STORED 生成列等价实现——
-- 墓碑行的生成列为 NULL，唯一索引忽略 NULL，活跃行照常受约束。
-- 业务编码本身不再被改写，工号/学号保持原值，导出与对账不受影响。
-- PostgreSQL 侧同等语义用部分唯一索引，本仓库 sys_user 已是该模式（sql/upgrades/postgres/phase5_unique_constraint.sql）。
--
-- 可重复执行：所有变更前先查 information_schema。
-- 回滚：删除新唯一索引与生成列，再按 20260811_education_master.sql 的定义重建原唯一索引。

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS sdfz_swap_active_unique;

DELIMITER $$

CREATE PROCEDURE sdfz_swap_active_unique(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_column_def TEXT,
    IN p_old_index VARCHAR(64),
    IN p_new_index VARCHAR(64),
    IN p_new_index_cols TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = p_table) THEN

        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = p_table
                         AND column_name = p_column) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_column_def);
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                       WHERE table_schema = DATABASE() AND table_name = p_table
                         AND index_name = p_new_index) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_new_index,
                              '` (', p_new_index_cols, ')');
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;

        IF EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = p_table
                     AND index_name = p_old_index) THEN
            SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_old_index, '`');
            PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END IF;

    END IF;
END$$

DELIMITER ;

-- 主数据：生成列覆盖业务编码部分，其余键列原样进新索引
CALL sdfz_swap_active_unique('edu_school', 'active_school_code',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, school_code, NULL)) STORED',
    'uq_edu_school_code', 'uq_edu_school_active_code', '`tenant_id`, `active_school_code`');

CALL sdfz_swap_active_unique('edu_class', 'active_class_code',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, class_code, NULL)) STORED',
    'uq_edu_class_code', 'uq_edu_class_active_code', '`tenant_id`, `school_id`, `active_class_code`');

CALL sdfz_swap_active_unique('edu_person', 'active_person_no',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, person_no, NULL)) STORED',
    'uq_edu_person_no', 'uq_edu_person_active_no', '`tenant_id`, `school_id`, `active_person_no`');

CALL sdfz_swap_active_unique('edu_subject', 'active_subject_code',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, subject_code, NULL)) STORED',
    'uq_edu_subject_code', 'uq_edu_subject_active_code', '`tenant_id`, `active_subject_code`');

CALL sdfz_swap_active_unique('edu_room', 'active_room_code',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, room_code, NULL)) STORED',
    'uq_edu_room_code', 'uq_edu_room_active_code', '`tenant_id`, `school_id`, `active_room_code`');

CALL sdfz_swap_active_unique('edu_device', 'active_device_code',
    'VARCHAR(128) GENERATED ALWAYS AS (IF(del_flag = 0, device_code, NULL)) STORED',
    'uq_edu_device_code', 'uq_edu_device_active_code', '`tenant_id`, `active_device_code`');

CALL sdfz_swap_active_unique('edu_semester', 'active_semester_code',
    'VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, semester_code, NULL)) STORED',
    'uq_edu_semester_code', 'uq_edu_semester_active_code', '`tenant_id`, `active_semester_code`');

-- 关系表：没有业务编码，用 del_flag 生成的哨兵列把墓碑行排除出唯一索引
CALL sdfz_swap_active_unique('edu_person_class', 'active_flag',
    'TINYINT GENERATED ALWAYS AS (IF(del_flag = 0, 1, NULL)) STORED',
    'uq_edu_person_class', 'uq_edu_person_class_active',
    '`tenant_id`, `person_id`, `class_id`, `membership_role`, `active_flag`');

-- 任教关系的 class_id 可空，原索引下 NULL 行互不冲突、去重失效；
-- 生成列用 IFNULL(class_id, 0) 把"不限班级"归一成 0，顺带修掉这个去重漏洞。
CALL sdfz_swap_active_unique('edu_person_subject', 'active_class_key',
    'BIGINT GENERATED ALWAYS AS (IF(del_flag = 0, IFNULL(class_id, 0), NULL)) STORED',
    'uq_edu_person_subject', 'uq_edu_person_subject_active',
    '`tenant_id`, `person_id`, `subject_id`, `active_class_key`');

-- sys_user 手机号：并发下 checkPhoneUnique 没有数据库兜底。
-- 用生成列同时排除墓碑行与空串，避免改动列默认值和存量数据。
CALL sdfz_swap_active_unique('sys_user', 'active_phone',
    'VARCHAR(20) GENERATED ALWAYS AS (IF(del_flag = 0 AND phone IS NOT NULL AND phone <> '''', phone, NULL)) STORED',
    'uq_sys_user_phone_placeholder', 'uq_sys_user_active_phone', '`tenant_id`, `active_phone`');

-- sys_user 登录名：原唯一索引 UNIQUE (username, tenant_id) 不含 del_flag，
-- 而 checkUsernameUnique 的判定带 del_flag = 0，两者口径不一致：
-- 逻辑删除过的登录名前置校验放行、插入时撞唯一索引，报"系统繁忙"。
-- 纳入同一套生成列方案后，登录名随账号逻辑删除一并释放，同名可重建。
CALL sdfz_swap_active_unique('sys_user', 'active_username',
    'VARCHAR(50) GENERATED ALWAYS AS (IF(del_flag = 0, username, NULL)) STORED',
    'username', 'uq_sys_user_active_username', '`tenant_id`, `active_username`');

DROP PROCEDURE IF EXISTS sdfz_swap_active_unique;

-- edu_person 增加离校落点。
-- 与账号停用区分：sys_user.status 管的是登录能力，leave_flag 管的是教育身份是否在校。
-- FLOW-09 里"仅人员停用"和"Han 账号禁用"是两行，必须能独立变化。
-- 离校后禁止参与新课程，历史课程、成绩与审计按 ID 关联保留。
DROP PROCEDURE IF EXISTS sdfz_add_column_if_absent;

DELIMITER $$
CREATE PROCEDURE sdfz_add_column_if_absent(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def TEXT)
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = DATABASE() AND table_name = p_table)
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sdfz_add_column_if_absent('edu_person', 'leave_flag',
    'SMALLINT NOT NULL DEFAULT 0 COMMENT ''0=在校 1=离校，独立于账号停用''');
CALL sdfz_add_column_if_absent('edu_person', 'leave_time',
    'TIMESTAMP NULL COMMENT ''离校时间，leave_flag 由 0 变 1 时写入''');

DROP PROCEDURE IF EXISTS sdfz_add_column_if_absent;

-- 剥离历史上被 releaseCode 改写过的墓碑编码，恢复工号/学号原值。
-- 墓碑行的生成列已是 NULL，不参与唯一约束，回填不会撞索引。
UPDATE edu_school   SET school_code   = LEFT(school_code,   CHAR_LENGTH(school_code)   - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND school_code   LIKE CONCAT('%#', id);
UPDATE edu_class    SET class_code    = LEFT(class_code,    CHAR_LENGTH(class_code)    - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND class_code    LIKE CONCAT('%#', id);
UPDATE edu_person   SET person_no     = LEFT(person_no,     CHAR_LENGTH(person_no)     - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND person_no     LIKE CONCAT('%#', id);
UPDATE edu_subject  SET subject_code  = LEFT(subject_code,  CHAR_LENGTH(subject_code)  - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND subject_code  LIKE CONCAT('%#', id);
UPDATE edu_room     SET room_code     = LEFT(room_code,     CHAR_LENGTH(room_code)     - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND room_code     LIKE CONCAT('%#', id);
UPDATE edu_device   SET device_code   = LEFT(device_code,   CHAR_LENGTH(device_code)   - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND device_code   LIKE CONCAT('%#', id);
UPDATE edu_semester SET semester_code = LEFT(semester_code, CHAR_LENGTH(semester_code) - CHAR_LENGTH(CONCAT('#', id))) WHERE del_flag = 1 AND semester_code LIKE CONCAT('%#', id);
