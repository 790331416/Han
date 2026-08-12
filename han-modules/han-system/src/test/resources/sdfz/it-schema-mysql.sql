-- 教育人员统一入口的事务/并发集成测试专用库结构（MySQL 8.4）
--
-- 只包含被测路径真正用到的表，字段与生产 DDL 保持一致：
--   sys_user / sys_role / sys_user_role  取自 sql/tiers/small/small-init-mysql.sql
--   edu_*                                取自 sql/sdfz/mysql/20260811_education_master.sql
--   active_* 生成列与唯一索引            取自 sql/sdfz/mysql/20260812b_education_active_unique_index.sql
--
-- 每次运行前整体重建，保证用例之间互不影响。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS edu_person_subject;
DROP TABLE IF EXISTS edu_person_class;
DROP TABLE IF EXISTS edu_person;
DROP TABLE IF EXISTS edu_subject;
DROP TABLE IF EXISTS edu_class;
DROP TABLE IF EXISTS edu_school;

CREATE TABLE sys_user (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    dept_id         BIGINT,
    username        VARCHAR(50)  NOT NULL,
    nickname        VARCHAR(50)  DEFAULT '',
    user_type       VARCHAR(10)  DEFAULT 'sys',
    email           VARCHAR(100) DEFAULT '',
    phone           VARCHAR(20)  DEFAULT '',
    sex             SMALLINT     DEFAULT 0,
    avatar          VARCHAR(500) DEFAULT '',
    password        VARCHAR(200) NOT NULL,
    status          SMALLINT     DEFAULT 0,
    login_ip        VARCHAR(128) DEFAULT '',
    login_time      TIMESTAMP NULL,
    pwd_update_time TIMESTAMP NULL,
    pwd_reset_flag  SMALLINT     DEFAULT 0,
    totp_secret     VARCHAR(64),
    totp_enabled    SMALLINT     DEFAULT 0,
    create_by       BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag        SMALLINT     DEFAULT 0,
    remark          VARCHAR(500),
    active_phone    VARCHAR(20) GENERATED ALWAYS AS (IF(del_flag = 0 AND phone IS NOT NULL AND phone <> '', phone, NULL)) STORED,
    active_username VARCHAR(50) GENERATED ALWAYS AS (IF(del_flag = 0, username, NULL)) STORED,
    UNIQUE KEY uq_sys_user_active_username (tenant_id, active_username),
    UNIQUE KEY uq_sys_user_active_phone (tenant_id, active_phone)
);

CREATE TABLE sys_role (
    id          BIGINT      NOT NULL PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL,
    role_name   VARCHAR(50) NOT NULL,
    role_key    VARCHAR(50) NOT NULL,
    role_sort   INT         DEFAULT 0,
    data_scope  CHAR(1)     DEFAULT '1',
    menu_check_strictly SMALLINT DEFAULT 1,
    dept_check_strictly SMALLINT DEFAULT 1,
    status      SMALLINT    DEFAULT 0,
    create_by   BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_by   BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag    SMALLINT    DEFAULT 0,
    remark      VARCHAR(500)
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE edu_school (
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
    active_school_code VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, school_code, NULL)) STORED,
    UNIQUE KEY uq_edu_school_active_code (tenant_id, active_school_code)
);

CREATE TABLE edu_class (
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
    active_class_code VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, class_code, NULL)) STORED,
    UNIQUE KEY uq_edu_class_active_code (tenant_id, school_id, active_class_code)
);

CREATE TABLE edu_subject (
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
    active_subject_code VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, subject_code, NULL)) STORED,
    UNIQUE KEY uq_edu_subject_active_code (tenant_id, active_subject_code)
);

CREATE TABLE edu_person (
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
    leave_flag SMALLINT NOT NULL DEFAULT 0,
    leave_time TIMESTAMP NULL,
    sync_hash VARCHAR(64),
    last_sync_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_person_no VARCHAR(64) GENERATED ALWAYS AS (IF(del_flag = 0, person_no, NULL)) STORED,
    UNIQUE KEY uq_edu_person_active_no (tenant_id, school_id, active_person_no)
);

CREATE TABLE edu_person_class (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    membership_role VARCHAR(32) NOT NULL,
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    active_flag TINYINT GENERATED ALWAYS AS (IF(del_flag = 0, 1, NULL)) STORED,
    UNIQUE KEY uq_edu_person_class_active (tenant_id, person_id, class_id, membership_role, active_flag)
);

CREATE TABLE edu_person_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    class_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    active_class_key BIGINT GENERATED ALWAYS AS (IF(del_flag = 0, IFNULL(class_id, 0), NULL)) STORED,
    UNIQUE KEY uq_edu_person_subject_active (tenant_id, person_id, subject_id, active_class_key)
);

INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status)
VALUES (202608120101, 1, '普通教师', 'teacher', 11, '5', 0),
       (202608120102, 1, '学生', 'student', 12, '5', 0);

INSERT INTO edu_school (id, tenant_id, school_code, school_name, school_role, source_system, status)
VALUES (900001, 1, 'IT-SCH-A', '集成测试学校', 'MAIN', 'HAN', 0);

INSERT INTO edu_class (id, tenant_id, school_id, class_code, class_name, class_role, source_system, status)
VALUES (900011, 1, 900001, 'IT-CLS-1', '集成测试一班', 'MAIN', 'HAN', 0),
       (900012, 1, 900001, 'IT-CLS-2', '集成测试二班', 'MAIN', 'HAN', 0);

INSERT INTO edu_subject (id, tenant_id, subject_code, subject_name, sort, source_system, status)
VALUES (900021, 1, 'IT-SUB-1', '集成测试语文', 1, 'HAN', 0);
