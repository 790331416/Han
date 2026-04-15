-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- =============================================
-- 3. 部门表
-- =============================================
CREATE TABLE sys_dept (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    parent_id       BIGINT          DEFAULT 0,
    ancestors       VARCHAR(500)    DEFAULT '',
    dept_name       VARCHAR(100)    NOT NULL,
    dept_code       VARCHAR(50),
    leader_id       BIGINT,
    phone           VARCHAR(20),
    email           VARCHAR(100),
    post_sort       INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 4. 用户表
-- =============================================
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    dept_id         BIGINT,
    username        VARCHAR(50)     NOT NULL,
    nickname        VARCHAR(50)     DEFAULT '',
    user_type       VARCHAR(10)     DEFAULT 'sys',
    email           VARCHAR(100)    DEFAULT '',
    phone           VARCHAR(20)     DEFAULT '',
    sex             SMALLINT        DEFAULT 0,
    avatar          VARCHAR(500)    DEFAULT '',
    password        VARCHAR(200)    NOT NULL,
    status          SMALLINT        DEFAULT 0,
    login_ip        VARCHAR(128)    DEFAULT '',
    login_time      TIMESTAMP,
    pwd_update_time TIMESTAMP,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    UNIQUE (username, tenant_id)
);

-- =============================================
-- 5. 岗位表
-- =============================================
CREATE TABLE sys_post (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    post_code       VARCHAR(50)     NOT NULL,
    post_name       VARCHAR(100)    NOT NULL,
    sort            INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 6. 角色表
-- =============================================
CREATE TABLE sys_role (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    role_name       VARCHAR(50)     NOT NULL,
    role_key        VARCHAR(50)     NOT NULL,
    role_sort       INT             DEFAULT 0,
    data_scope      CHAR(1)         DEFAULT '1',
    menu_check_strictly     SMALLINT DEFAULT 1,
    dept_check_strictly     SMALLINT DEFAULT 1,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 7. 菜单权限表
-- =============================================
CREATE TABLE sys_menu (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    parent_id       BIGINT          DEFAULT 0,
    ancestors       VARCHAR(500)    DEFAULT '',
    menu_name       VARCHAR(100)    NOT NULL,
    menu_type       CHAR(1)         NOT NULL,
    path            VARCHAR(200)    DEFAULT '',
    component       VARCHAR(255),
    query           VARCHAR(255),
    perms           VARCHAR(200),
    icon            VARCHAR(100)    DEFAULT '#',
    sort            INT             DEFAULT 0,
    visible         SMALLINT        DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    is_frame        SMALLINT        DEFAULT 1,
    is_cache        SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 8. 用户和角色关联表
-- =============================================
CREATE TABLE sys_user_role (
    user_id         BIGINT          NOT NULL,
    role_id         BIGINT          NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

-- =============================================
-- 9. 用户和岗位关联表
-- =============================================
CREATE TABLE sys_user_post (
    user_id         BIGINT          NOT NULL,
    post_id         BIGINT          NOT NULL,
    PRIMARY KEY (user_id, post_id)
);

-- =============================================
-- 10. 角色和菜单关联表
-- =============================================
CREATE TABLE sys_role_menu (
    role_id         BIGINT          NOT NULL,
    menu_id         BIGINT          NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

-- =============================================
-- 11. 角色和部门关联表
-- =============================================
CREATE TABLE sys_role_dept (
    role_id         BIGINT          NOT NULL,
    dept_id         BIGINT          NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);

-- =============================================
-- 12. 字典类型表
-- =============================================
CREATE TABLE sys_dict_type (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    dict_name       VARCHAR(100)    NOT NULL,
    dict_type       VARCHAR(100)    NOT NULL,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 13. 字典数据表
-- =============================================
CREATE TABLE sys_dict_data (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    dict_type       VARCHAR(100)    NOT NULL,
    dict_label      VARCHAR(100)    NOT NULL,
    dict_value      VARCHAR(100)    NOT NULL,
    dict_sort       INT             DEFAULT 0,
    css_class       VARCHAR(100),
    list_class      VARCHAR(100),
    is_default      SMALLINT        DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 14. 参数配置表
-- =============================================
CREATE TABLE sys_config (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    config_name     VARCHAR(100)    NOT NULL,
    config_key      VARCHAR(100)    NOT NULL,
    config_value    VARCHAR(2000)   DEFAULT '',
    config_type     CHAR(1)         DEFAULT 'N',
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 15. 通知公告表
-- =============================================
CREATE TABLE sys_notice (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    notice_title    VARCHAR(100)    NOT NULL,
    notice_type     CHAR(1)         NOT NULL,
    notice_content  TEXT,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

-- =============================================
-- 16. 用户通知已读状态表
-- =============================================
CREATE TABLE sys_notice_read (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    notice_id       BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    read_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0
);

CREATE UNIQUE INDEX uk_sys_notice_read_notice_user
    ON sys_notice_read (tenant_id, notice_id, user_id);

CREATE INDEX idx_sys_notice_read_user
    ON sys_notice_read (tenant_id, user_id, del_flag);

CREATE INDEX idx_sys_notice_read_notice
    ON sys_notice_read (tenant_id, notice_id, del_flag);

-- =============================================
-- 17. 操作日志表
-- =============================================
CREATE TABLE sys_oper_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    title           VARCHAR(100)    DEFAULT '',
    business_type   SMALLINT        DEFAULT 0,
    method          VARCHAR(200)    DEFAULT '',
    request_method  VARCHAR(10)     DEFAULT '',
    operator_type   SMALLINT        DEFAULT 0,
    oper_name       VARCHAR(50)     DEFAULT '',
    dept_name       VARCHAR(100)    DEFAULT '',
    oper_url        VARCHAR(500)    DEFAULT '',
    oper_ip         VARCHAR(128)    DEFAULT '',
    oper_location   VARCHAR(255)    DEFAULT '',
    oper_param      TEXT,
    json_result     TEXT,
    status          SMALLINT        DEFAULT 0,
    error_msg       TEXT,
    oper_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    cost_time       BIGINT          DEFAULT 0
);

-- =============================================
-- 18. 登录日志表
-- =============================================
CREATE TABLE sys_login_log (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    user_id         BIGINT,
    username        VARCHAR(50)     DEFAULT '',
    client_type     VARCHAR(20)     DEFAULT '',
    device_id       VARCHAR(100)    DEFAULT '',
    ip_addr         VARCHAR(128)    DEFAULT '',
    login_location  VARCHAR(255)    DEFAULT '',
    browser         VARCHAR(100)    DEFAULT '',
    os              VARCHAR(100)    DEFAULT '',
    status          SMALLINT        DEFAULT 0,
    message         VARCHAR(255)    DEFAULT '',
    login_time      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 18. 在线用户表
-- =============================================
CREATE TABLE sys_user_online (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    user_id         BIGINT          NOT NULL,
    username        VARCHAR(50)     DEFAULT '',
    client_type     VARCHAR(20)     DEFAULT '',
    device_id       VARCHAR(100)    DEFAULT '',
    ip_addr         VARCHAR(128)    DEFAULT '',
    login_location  VARCHAR(255)    DEFAULT '',
    browser         VARCHAR(100)    DEFAULT '',
    os              VARCHAR(100)    DEFAULT '',
    login_time      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    expire_time     TIMESTAMP
);

-- =============================================
-- 20. 客户端配置表
-- =============================================
CREATE TABLE sys_client (
    id              BIGINT          NOT NULL PRIMARY KEY,
    client_key      VARCHAR(50)     NOT NULL UNIQUE,
    client_secret   VARCHAR(200)    NOT NULL,
    client_type     VARCHAR(20)     NOT NULL,
    token_expire    INT             DEFAULT 1800,
    refresh_expire  INT             DEFAULT 604800,
    max_online      INT             DEFAULT 1,
    kick_strategy   VARCHAR(20)     DEFAULT 'kick_old',
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);
