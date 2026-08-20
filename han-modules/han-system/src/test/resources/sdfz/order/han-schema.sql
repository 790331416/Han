-- 集成测试用的 Han 侧最小库结构（H2，MySQL 兼容模式）。
-- 与 sql/sdfz/mysql/20260811_education_master.sql、20260812_course_order.sql 的相关部分保持一致；
-- 只保留订购模块真正读写的表。

CREATE TABLE edu_school (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    ancestors VARCHAR(1024) NOT NULL DEFAULT '0',
    node_level INT NOT NULL DEFAULT 0,
    school_code VARCHAR(64) NOT NULL,
    school_name VARCHAR(128) NOT NULL,
    school_role VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    org_type VARCHAR(32),
    school_manage_type VARCHAR(32),
    school_property VARCHAR(32),
    region_id BIGINT,
    auto_upgrade_enabled SMALLINT,
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
    remark VARCHAR(500)
);

CREATE TABLE edu_class (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    parent_id BIGINT,
    ancestors VARCHAR(1024) NOT NULL DEFAULT '0',
    node_level INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    node_type VARCHAR(16),
    academic_year_id BIGINT,
    cohort_year INT,
    branch_code VARCHAR(32),
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
    remark VARCHAR(500)
);

CREATE TABLE edu_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT,
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
    remark VARCHAR(500)
);

CREATE TABLE edu_room (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    parent_id BIGINT,
    ancestors VARCHAR(1024) NOT NULL DEFAULT '0',
    node_level INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    node_type VARCHAR(16),
    room_code VARCHAR(64) NOT NULL,
    room_name VARCHAR(128) NOT NULL,
    alias_name VARCHAR(128),
    room_type VARCHAR(32),
    capacity INT,
    longitude DECIMAL(10, 6),
    latitude DECIMAL(10, 6),
    data_source VARCHAR(32),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500)
);

CREATE TABLE edu_device (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT NOT NULL,
    room_id BIGINT,
    device_code VARCHAR(128) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    application_types VARCHAR(500),
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
    remark VARCHAR(500)
);

CREATE TABLE edu_semester (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    school_id BIGINT,
    academic_year_id BIGINT,
    semester_code VARCHAR(64) NOT NULL,
    semester_name VARCHAR(128) NOT NULL,
    begin_date DATE NOT NULL,
    end_date DATE NOT NULL,
    current_flag SMALLINT NOT NULL DEFAULT 0,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    status SMALLINT NOT NULL DEFAULT 0,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uq_edu_semester_code UNIQUE (tenant_id, semester_code)
);

CREATE TABLE edu_course_order (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    listen_school_id BIGINT NOT NULL,
    listen_class_id BIGINT NOT NULL,
    listen_room_id BIGINT NULL,
    listen_device_id BIGINT NULL,
    lecture_school_id BIGINT NOT NULL,
    lecture_class_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    grant_scope VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_time TIMESTAMP NULL,
    expire_time TIMESTAMP NULL,
    freeze_reason VARCHAR(200),
    cancel_reason VARCHAR(200),
    source_system VARCHAR(32) NOT NULL DEFAULT 'HAN',
    external_id VARCHAR(128),
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    active_flag TINYINT GENERATED ALWAYS AS (
        CASE WHEN del_flag = 0 AND status IN ('PENDING', 'ACTIVE', 'FROZEN') THEN 1 ELSE NULL END
    ),
    CONSTRAINT uq_edu_course_order_no UNIQUE (tenant_id, order_no),
    CONSTRAINT uq_edu_course_order_active
        UNIQUE (tenant_id, listen_class_id, lecture_class_id, semester_id, active_flag),
    CONSTRAINT chk_edu_course_order_dates
        CHECK (expire_time IS NULL OR effective_time IS NULL OR expire_time >= effective_time)
);

CREATE TABLE edu_course_order_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_edu_course_order_subject UNIQUE (tenant_id, order_id, subject_id)
);

CREATE TABLE edu_course_order_grant (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    course_name VARCHAR(200) NULL,
    course_begin_time TIMESTAMP NULL,
    listen_class_id BIGINT NOT NULL,
    subject_id BIGINT NULL,
    attend_id VARCHAR(64) NULL,
    grant_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    suspended_flag TINYINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    last_attempt_time TIMESTAMP NULL,
    materialized_time TIMESTAMP NULL,
    revoked_time TIMESTAMP NULL,
    create_by BIGINT, create_name VARCHAR(50), create_dept BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT, update_name VARCHAR(50), update_time TIMESTAMP NULL,
    del_flag SMALLINT NOT NULL DEFAULT 0,
    remark VARCHAR(500),
    CONSTRAINT uq_edu_course_order_grant UNIQUE (tenant_id, order_id, course_id)
);
