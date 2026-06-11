-- Han full tier PostgreSQL init
-- generated from legacy modular SQL layout on 2026-04-15

-- ============================================================
-- source: postgres\system\00-schema.sql
-- ============================================================
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
    pwd_reset_flag  SMALLINT        DEFAULT 0,
    totp_secret     VARCHAR(64),
    totp_enabled    SMALLINT        DEFAULT 0,
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


-- ============================================================
-- source: postgres\system\10-seed.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- 3. 部门
INSERT INTO sys_dept (id, tenant_id, parent_id, ancestors, dept_name, dept_code, post_sort, status) VALUES
(100, 1, 0, '0', 'han科技', 'HQ', 0, 0),
(101, 1, 100, '0,100', '研发部门', 'RD', 1, 0),
(102, 1, 100, '0,100', '产品部门', 'PD', 2, 0),
(103, 1, 100, '0,100', '运营部门', 'OP', 3, 0),
(104, 1, 101, '0,100,101', '研发一组', 'RD1', 1, 0),
(105, 1, 101, '0,100,101', '研发二组', 'RD2', 2, 0);

-- 4. 用户 (密码: admin123)
INSERT INTO sys_user (id, tenant_id, dept_id, username, nickname, password, phone, status, remark) VALUES
(1, 1, 100, 'admin', '超级管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '13800000000', 0, '系统超级管理员'),
(2, 1, 101, 'han', '徐漫', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '13800000001', 0, '普通管理员');

-- 5. 岗位
INSERT INTO sys_post (id, tenant_id, post_code, post_name, sort, status) VALUES
(1, 1, 'ceo', '董事长', 1, 0),
(2, 1, 'cto', '技术总监', 2, 0),
(3, 1, 'manager', '项目经理', 3, 0),
(4, 1, 'developer', '开发工程师', 4, 0);

-- 6. 角色
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_sort, data_scope, status, remark) VALUES
(1, 1, '超级管理员', 'admin', 1, '1', 0, '拥有全部权限'),
(2, 1, '普通管理员', 'common', 2, '2', 0, '普通管理员角色'),
(3, 1, '部门管理员', 'dept_admin', 3, '3', 0, '本部门数据权限'),
(4, 1, '普通用户', 'user', 4, '5', 0, '仅本人数据权限');

-- 7. 菜单
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(1, 0, '0', '系统管理', 'M', 'system', NULL, NULL, 'system', 1, 0, 0),
(2, 0, '0', '系统监控', 'M', 'monitor', NULL, NULL, 'monitor', 2, 0, 0),
(3, 0, '0', '系统工具', 'M', 'tool', NULL, NULL, 'tool', 3, 0, 0),
(4, 0, '0', '租户管理', 'M', 'tenant', NULL, NULL, 'peoples', 4, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(100, 1, '0,1', '用户管理', 'C', 'user', 'system/user/index', 'system:user:list', 'user', 1, 0, 0),
(101, 1, '0,1', '角色管理', 'C', 'role', 'system/role/index', 'system:role:list', 'peoples', 2, 0, 0),
(102, 1, '0,1', '菜单管理', 'C', 'menu', 'system/menu/index', 'system:menu:list', 'tree-table', 3, 0, 0),
(103, 1, '0,1', '部门管理', 'C', 'dept', 'system/dept/index', 'system:dept:list', 'tree', 4, 0, 0),
(104, 1, '0,1', '岗位管理', 'C', 'post', 'system/post/index', 'system:post:list', 'post', 5, 0, 0),
(105, 1, '0,1', '字典管理', 'C', 'dict', 'system/dict/index', 'system:dict:list', 'dict', 6, 0, 0),
(106, 1, '0,1', '参数设置', 'C', 'config', 'system/config/index', 'system:config:list', 'edit', 7, 0, 0),
(107, 1, '0,1', '通知公告', 'C', 'notice', 'system/notice/index', 'system:notice:list', 'message', 8, 0, 0),
(108, 1, '0,1', '客户端管理', 'C', 'client', 'system/client/index', 'system:client:list', 'client', 9, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(200, 2, '0,2', '在线用户', 'C', 'online', 'monitor/online/index', 'monitor:online:list', 'online', 1, 0, 0),
(201, 2, '0,2', '操作日志', 'C', 'operlog', 'monitor/operlog/index', 'monitor:operlog:list', 'form', 2, 0, 0),
(202, 2, '0,2', '登录日志', 'C', 'loginlog', 'monitor/loginlog/index', 'monitor:loginlog:list', 'logininfor', 3, 0, 0),
(203, 2, '0,2', '缓存监控', 'C', 'cache', 'monitor/cache/index', 'monitor:cache:list', 'redis', 4, 0, 0),
(204, 2, '0,2', '服务监控', 'C', 'server', 'monitor/server/index', 'monitor:server:list', 'server', 5, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(300, 3, '0,3', '代码生成', 'C', 'gen', 'tool/gen/index', 'tool:gen:list', 'code', 1, 0, 0),
(301, 3, '0,3', '系统接口', 'C', 'swagger', 'tool/swagger/index', 'tool:swagger:list', 'swagger', 2, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(400, 4, '0,4', '租户列表', 'C', 'list', 'tenant/list/index', 'tenant:list', 'list', 1, 0, 0),
(401, 4, '0,4', '套餐管理', 'C', 'package', 'tenant/package/index', 'tenant:package:list', 'component', 2, 0, 0);

-- 按钮权限
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(1001, 100, '0,1,100', '用户查询', 'F', '', NULL, 'system:user:query', '#', 1, 0, 0),
(1002, 100, '0,1,100', '用户新增', 'F', '', NULL, 'system:user:add', '#', 2, 0, 0),
(1003, 100, '0,1,100', '用户修改', 'F', '', NULL, 'system:user:edit', '#', 3, 0, 0),
(1004, 100, '0,1,100', '用户删除', 'F', '', NULL, 'system:user:remove', '#', 4, 0, 0),
(1005, 100, '0,1,100', '用户导出', 'F', '', NULL, 'system:user:export', '#', 5, 0, 0),
(1006, 100, '0,1,100', '用户导入', 'F', '', NULL, 'system:user:import', '#', 6, 0, 0),
(1007, 100, '0,1,100', '重置密码', 'F', '', NULL, 'system:user:resetPwd', '#', 7, 0, 0),
(1011, 101, '0,1,101', '角色查询', 'F', '', NULL, 'system:role:query', '#', 1, 0, 0),
(1012, 101, '0,1,101', '角色新增', 'F', '', NULL, 'system:role:add', '#', 2, 0, 0),
(1013, 101, '0,1,101', '角色修改', 'F', '', NULL, 'system:role:edit', '#', 3, 0, 0),
(1014, 101, '0,1,101', '角色删除', 'F', '', NULL, 'system:role:remove', '#', 4, 0, 0),
(1015, 101, '0,1,101', '角色导出', 'F', '', NULL, 'system:role:export', '#', 5, 0, 0),
(1021, 102, '0,1,102', '菜单查询', 'F', '', NULL, 'system:menu:query', '#', 1, 0, 0),
(1022, 102, '0,1,102', '菜单新增', 'F', '', NULL, 'system:menu:add', '#', 2, 0, 0),
(1023, 102, '0,1,102', '菜单修改', 'F', '', NULL, 'system:menu:edit', '#', 3, 0, 0),
(1024, 102, '0,1,102', '菜单删除', 'F', '', NULL, 'system:menu:remove', '#', 4, 0, 0),
(1031, 103, '0,1,103', '部门查询', 'F', '', NULL, 'system:dept:query', '#', 1, 0, 0),
(1032, 103, '0,1,103', '部门新增', 'F', '', NULL, 'system:dept:add', '#', 2, 0, 0),
(1033, 103, '0,1,103', '部门修改', 'F', '', NULL, 'system:dept:edit', '#', 3, 0, 0),
(1034, 103, '0,1,103', '部门删除', 'F', '', NULL, 'system:dept:remove', '#', 4, 0, 0),
(1041, 104, '0,1,104', '岗位查询', 'F', '', NULL, 'system:post:query', '#', 1, 0, 0),
(1042, 104, '0,1,104', '岗位新增', 'F', '', NULL, 'system:post:add', '#', 2, 0, 0),
(1043, 104, '0,1,104', '岗位修改', 'F', '', NULL, 'system:post:edit', '#', 3, 0, 0),
(1044, 104, '0,1,104', '岗位删除', 'F', '', NULL, 'system:post:remove', '#', 4, 0, 0),
(1045, 104, '0,1,104', '岗位导出', 'F', '', NULL, 'system:post:export', '#', 5, 0, 0),
(1051, 105, '0,1,105', '字典查询', 'F', '', NULL, 'system:dict:query', '#', 1, 0, 0),
(1052, 105, '0,1,105', '字典新增', 'F', '', NULL, 'system:dict:add', '#', 2, 0, 0),
(1053, 105, '0,1,105', '字典修改', 'F', '', NULL, 'system:dict:edit', '#', 3, 0, 0),
(1054, 105, '0,1,105', '字典删除', 'F', '', NULL, 'system:dict:remove', '#', 4, 0, 0),
(1055, 105, '0,1,105', '字典导出', 'F', '', NULL, 'system:dict:export', '#', 5, 0, 0),
(1061, 106, '0,1,106', '参数查询', 'F', '', NULL, 'system:config:query', '#', 1, 0, 0),
(1062, 106, '0,1,106', '参数新增', 'F', '', NULL, 'system:config:add', '#', 2, 0, 0),
(1063, 106, '0,1,106', '参数修改', 'F', '', NULL, 'system:config:edit', '#', 3, 0, 0),
(1064, 106, '0,1,106', '参数删除', 'F', '', NULL, 'system:config:remove', '#', 4, 0, 0),
(1065, 106, '0,1,106', '参数导出', 'F', '', NULL, 'system:config:export', '#', 5, 0, 0),
(1071, 107, '0,1,107', '公告查询', 'F', '', NULL, 'system:notice:query', '#', 1, 0, 0),
(1072, 107, '0,1,107', '公告新增', 'F', '', NULL, 'system:notice:add', '#', 2, 0, 0),
(1073, 107, '0,1,107', '公告修改', 'F', '', NULL, 'system:notice:edit', '#', 3, 0, 0),
(1074, 107, '0,1,107', '公告删除', 'F', '', NULL, 'system:notice:remove', '#', 4, 0, 0);

-- 8. 用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2);

-- 9. 用户岗位关联
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1), (2, 4);

-- 10. 角色菜单关联(超管拥有全部菜单)
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE del_flag = 0;

-- 11. 字典类型
INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark) VALUES
(1, '用户性别', 'sys_user_sex', 0, '用户性别列表'),
(2, '系统开关', 'sys_normal_disable', 0, '系统开关列表'),
(3, '菜单状态', 'sys_show_hide', 0, '菜单状态列表'),
(4, '系统是否', 'sys_yes_no', 0, '系统是否列表'),
(5, '通知类型', 'sys_notice_type', 0, '通知类型列表'),
(6, '通知状态', 'sys_notice_status', 0, '通知状态列表'),
(7, '操作类型', 'sys_oper_type', 0, '操作类型列表'),
(8, '系统状态', 'sys_common_status', 0, '登录状态列表'),
(9, '客户端类型', 'sys_client_type', 0, '客户端类型列表'),
(10, '数据范围', 'sys_data_scope', 0, '数据范围列表'),
(11, '租户隔离类型', 'sys_isolation_type', 0, '租户隔离类型列表'),
(12, 'AI模型类型', 'ai_model_type', 0, 'AI模型管理模型类型列表'),
(13, 'AI模型供应商', 'ai_model_provider', 0, 'AI模型管理供应商列表'),
(14, 'AI Prompt模板分类', 'ai_prompt_category', 0, 'AI Prompt模板分类列表');

-- 12. 字典数据
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) VALUES
(1, 'sys_user_sex', '未知', '0', 1, '', '', 0, 0),
(2, 'sys_user_sex', '男', '1', 2, '', '', 0, 0),
(3, 'sys_user_sex', '女', '2', 3, '', '', 0, 0),
(4, 'sys_normal_disable', '正常', '0', 1, '', 'primary', 1, 0),
(5, 'sys_normal_disable', '停用', '1', 2, '', 'danger', 0, 0),
(6, 'sys_show_hide', '显示', '0', 1, '', 'primary', 1, 0),
(7, 'sys_show_hide', '隐藏', '1', 2, '', 'danger', 0, 0),
(8, 'sys_yes_no', '是', 'Y', 1, '', 'primary', 1, 0),
(9, 'sys_yes_no', '否', 'N', 2, '', 'danger', 0, 0),
(10, 'sys_notice_type', '通知', '1', 1, '', 'warning', 0, 0),
(11, 'sys_notice_type', '公告', '2', 2, '', 'success', 0, 0),
(12, 'sys_notice_status', '正常', '0', 1, '', 'primary', 1, 0),
(13, 'sys_notice_status', '关闭', '1', 2, '', 'danger', 0, 0),
(14, 'sys_oper_type', '其它', '0', 0, '', 'info', 0, 0),
(15, 'sys_oper_type', '新增', '1', 1, '', 'info', 0, 0),
(16, 'sys_oper_type', '修改', '2', 2, '', 'info', 0, 0),
(17, 'sys_oper_type', '删除', '3', 3, '', 'danger', 0, 0),
(18, 'sys_oper_type', '查询', '4', 4, '', 'primary', 0, 0),
(19, 'sys_oper_type', '导出', '5', 5, '', 'warning', 0, 0),
(20, 'sys_oper_type', '导入', '6', 6, '', 'warning', 0, 0),
(21, 'sys_oper_type', '授权', '7', 7, '', 'primary', 0, 0),
(22, 'sys_oper_type', '强退', '8', 8, '', 'danger', 0, 0),
(23, 'sys_oper_type', '清空', '9', 9, '', 'danger', 0, 0),
(24, 'sys_common_status', '成功', '0', 1, '', 'success', 0, 0),
(25, 'sys_common_status', '失败', '1', 2, '', 'danger', 0, 0),
(26, 'sys_client_type', 'PC端', 'pc', 1, '', 'primary', 1, 0),
(27, 'sys_client_type', 'App端', 'app', 2, '', 'success', 0, 0),
(28, 'sys_client_type', 'H5端', 'h5', 3, '', 'info', 0, 0),
(29, 'sys_client_type', '微信小程序', 'wechat_mp', 4, '', 'success', 0, 0),
(30, 'sys_client_type', '微信公众号', 'wechat_oa', 5, '', 'success', 0, 0),
(31, 'sys_client_type', '开放API', 'api', 6, '', 'warning', 0, 0),
(32, 'sys_data_scope', '全部数据', '1', 1, '', 'primary', 0, 0),
(33, 'sys_data_scope', '自定义数据', '2', 2, '', 'info', 0, 0),
(34, 'sys_data_scope', '本部门数据', '3', 3, '', 'info', 0, 0),
(35, 'sys_data_scope', '本部门及以下', '4', 4, '', 'info', 0, 0),
(36, 'sys_data_scope', '仅本人数据', '5', 5, '', 'info', 0, 0),
(37, 'sys_isolation_type', '逻辑隔离', 'logical', 1, '', 'primary', 1, 0),
(38, 'sys_isolation_type', '物理隔离', 'physical', 2, '', 'warning', 0, 0),
(39, 'sys_isolation_type', '混合隔离', 'hybrid', 3, '', 'info', 0, 0),
(40, 'ai_model_type', '大语言模型', 'LLM', 10, '', 'primary', 1, 0),
(41, 'ai_model_type', '图片生成模型', 'IMAGE', 20, '', 'success', 0, 0),
(42, 'ai_model_type', '视频生成模型', 'VIDEO', 30, '', 'warning', 0, 0),
(43, 'ai_model_type', '视频剪辑合成', 'VIDEO_EDIT', 40, '', 'warning', 0, 0),
(44, 'ai_model_type', '向量模型', 'EMBEDDING', 50, '', 'info', 0, 0),
(45, 'ai_model_type', '重排模型', 'RERANK', 60, '', 'info', 0, 0),
(46, 'ai_model_type', '语音合成', 'TTS', 70, '', 'success', 0, 0),
(47, 'ai_model_type', '语音识别', 'STT', 80, '', 'info', 0, 0),
(48, 'ai_model_provider', 'OpenAI', 'openai', 10, '', 'primary', 0, 0),
(49, 'ai_model_provider', '火山引擎/方舟', 'volcengine', 20, '', 'warning', 1, 0),
(50, 'ai_model_provider', 'DeepSeek', 'deepseek', 30, '', 'success', 0, 0),
(51, 'ai_model_provider', '通义千问', 'qwen', 40, '', 'success', 0, 0),
(52, 'ai_model_provider', '智谱AI', 'zhipu', 50, '', 'primary', 0, 0),
(53, 'ai_model_provider', '百度千帆', 'baidu', 60, '', 'primary', 0, 0),
(54, 'ai_model_provider', 'Ollama', 'ollama', 70, '', 'info', 0, 0),
(55, 'ai_model_provider', 'Azure OpenAI', 'azure', 80, '', 'primary', 0, 0),
(56, 'ai_model_provider', 'Anthropic', 'anthropic', 90, '', 'info', 0, 0),
(57, 'ai_model_provider', 'SiliconFlow', 'siliconflow', 100, '', 'success', 0, 0),
(58, 'ai_model_provider', 'Coze(扣子)', 'coze', 110, '', 'warning', 0, 0),
(59, 'ai_model_provider', 'DIFY', 'dify', 120, '', 'info', 0, 0),
(60, 'ai_model_provider', 'FastGPT', 'fastgpt', 130, '', 'info', 0, 0),
(61, 'ai_prompt_category', '系统提示词', 'system', 10, '', 'primary', 1, 0),
(62, 'ai_prompt_category', '用户模板', 'user', 20, '', 'success', 0, 0),
(63, 'ai_prompt_category', '助手模板', 'assistant', 30, '', 'warning', 0, 0),
(64, 'ai_prompt_category', 'AIVideo 文本润色', 'aivideo_text', 40, '', 'primary', 0, 0),
(65, 'ai_prompt_category', 'AIVideo 剧本生成', 'aivideo_script', 50, '', 'primary', 0, 0),
(66, 'ai_prompt_category', 'AIVideo 资产提取', 'aivideo_asset', 60, '', 'success', 0, 0),
(67, 'ai_prompt_category', 'AIVideo 分镜提取', 'aivideo_storyboard', 70, '', 'warning', 0, 0),
(68, 'ai_prompt_category', 'AIVideo 图片生成', 'aivideo_image', 80, '', 'success', 0, 0),
(69, 'ai_prompt_category', 'AIVideo 视频生成', 'aivideo_video', 90, '', 'warning', 0, 0),
(70, 'ai_prompt_category', 'AIVideo 语音合成', 'aivideo_tts', 100, '', 'info', 0, 0);

-- 13. 参数配置
INSERT INTO sys_config (id, config_name, config_key, config_value, config_type, remark) VALUES
(1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
(2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', '初始化密码 123456'),
(3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', '深色主题theme-dark，浅色主题theme-light'),
(4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', '是否开启验证码功能（true开启，false关闭）'),
(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', '是否开启注册用户功能（true开启，false关闭）'),
(6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');

-- 14. 客户端配置
INSERT INTO sys_client (id, client_key, client_secret, client_type, token_expire, refresh_expire, max_online, kick_strategy, status, remark) VALUES
(1, 'pc_admin', 'han_pc_secret_2024', 'pc', 1800, 604800, 1, 'kick_old', 0, 'PC后台管理端'),
(2, 'app_client', 'han_app_secret_2024', 'app', 604800, 2592000, 3, 'kick_old', 0, 'App移动端'),
(3, 'h5_client', 'han_h5_secret_2024', 'h5', 86400, 604800, 0, 'kick_old', 0, 'H5移动端'),
(4, 'wechat_mp', 'han_mp_secret_2024', 'wechat_mp', 2592000, 7776000, 0, 'kick_old', 0, '微信小程序'),
(5, 'wechat_oa', 'han_oa_secret_2024', 'wechat_oa', 604800, 2592000, 0, 'kick_old', 0, '微信公众号'),
(6, 'open_api', 'han_api_secret_2024', 'api', 3600, 86400, 0, 'reject_new', 0, '开放API接口');


-- ============================================================
-- source: postgres\job\00-schema.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- =============================================
-- 定时任务表
-- =============================================
CREATE TABLE sys_job (
    job_id          BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100)    NOT NULL,
    job_group       VARCHAR(64)     DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500)    NOT NULL,
    service_name    VARCHAR(100),
    handler         VARCHAR(200),
    cron_expression VARCHAR(255)    NOT NULL,
    misfire_policy  CHAR(1)         DEFAULT '3',
    concurrent      CHAR(1)         DEFAULT '1',
    status          CHAR(1)         DEFAULT '0',
    create_by       VARCHAR(50),
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(50),
    update_time     TIMESTAMP,
    remark          VARCHAR(500)
);

CREATE TABLE sys_job_log (
    job_log_id      BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100)    NOT NULL,
    job_group       VARCHAR(64),
    invoke_target   VARCHAR(500),
    trace_id        VARCHAR(64),
    job_message     VARCHAR(500),
    status          CHAR(1)         DEFAULT '0',
    exception_info  TEXT,
    start_time      TIMESTAMP,
    stop_time       TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);


-- ============================================================
-- source: postgres\job\10-seed.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- 15. 示例任务
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, status, remark) VALUES
('系统监控', 'SYSTEM', 'sampleTask.execute', '0 0/5 * * * ?', '0', '每5分钟执行一次'),
('数据同步', 'SYSTEM', 'sampleShardTask.syncData(100000,5)', '0 0 2 * * ?', '1', '每天凌晨2点执行');


-- ============================================================
-- source: postgres\job\90-fixup.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- ============================================================
-- JobFlow 迁移 SQL 脚本
-- 说明：将 Quartz 调度替换为 JobFlow 自研调度框架
-- 执行环境：PostgreSQL
-- ============================================================

-- 1. 为 sys_job 表新增 JobFlow 所需字段
ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS service_name VARCHAR(100);
ALTER TABLE sys_job ADD COLUMN IF NOT EXISTS handler VARCHAR(100);

COMMENT ON COLUMN sys_job.service_name IS '执行器服务名（Nacos 中的服务名，远程调用时使用）';
COMMENT ON COLUMN sys_job.handler IS '执行器处理方法（远程调用时的 handler 路径）';

-- 2. 清理 Quartz 相关表（按依赖顺序删除）
DROP TABLE IF EXISTS qrtz_fired_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_paused_trigger_grps CASCADE;
DROP TABLE IF EXISTS qrtz_scheduler_state CASCADE;
DROP TABLE IF EXISTS qrtz_locks CASCADE;
DROP TABLE IF EXISTS qrtz_simprop_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_simple_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_cron_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_blob_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_triggers CASCADE;
DROP TABLE IF EXISTS qrtz_job_details CASCADE;
DROP TABLE IF EXISTS qrtz_calendars CASCADE;

-- 3. 验证变更
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_name = 'sys_job' AND column_name IN ('service_name', 'handler');


-- ============================================================
-- source: postgres\tenant\00-schema.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

CREATE TABLE sys_tenant (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    tenant_name     VARCHAR(100)    NOT NULL,
    contact_name    VARCHAR(50),
    contact_phone   VARCHAR(20),
    contact_email   VARCHAR(100),
    package_id      BIGINT,
    user_limit      INT             DEFAULT -1,
    account_limit   INT             DEFAULT -1,
    expire_time     TIMESTAMP,
    isolation_type  VARCHAR(20)     DEFAULT 'logical',
    domain          VARCHAR(200),
    status          SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0
);

-- =============================================
-- 2. 租户套餐表
-- =============================================
CREATE TABLE sys_tenant_package (
    id              BIGINT          NOT NULL PRIMARY KEY,
    package_name    VARCHAR(100)    NOT NULL,
    menu_ids        TEXT,
    status          SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0
);

-- =============================================
-- 22. 租户资源配额表
-- =============================================
CREATE TABLE sys_tenant_quota (
    quota_id        BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    user_limit      INT             DEFAULT -1,
    storage_limit   BIGINT          DEFAULT -1,
    api_limit       BIGINT          DEFAULT -1,
    user_used       INT             DEFAULT 0,
    storage_used    BIGINT          DEFAULT 0,
    api_used        BIGINT          DEFAULT 0,
    reset_cycle     VARCHAR(20)     DEFAULT 'monthly',
    last_reset_time TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_tenant_quota IS '租户资源配额';
CREATE UNIQUE INDEX idx_tenant_quota_tenant ON sys_tenant_quota(tenant_id);


-- ============================================================
-- source: postgres\tenant\10-seed.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

-- 1. 租户
INSERT INTO sys_tenant (id, tenant_name, contact_name, contact_phone, package_id, user_limit, status, remark) VALUES
(1, '超级管理租户', '管理员', '13800000000', 1, -1, 0, '系统默认租户，拥有全部权限');

-- 2. 租户套餐
INSERT INTO sys_tenant_package (id, package_name, menu_ids, status, remark) VALUES
(1, '企业标准版', '[]', 0, '默认套餐，包含全部功能');


-- ============================================================
-- source: postgres\tenant\90-fixup.sql
-- ============================================================
-- PostgreSQL tenant billing extension tables.

CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status SMALLINT DEFAULT 0,
    amount NUMERIC(10,2) DEFAULT 0,
    payment_method VARCHAR(32) DEFAULT NULL,
    payment_no VARCHAR(128) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_subscription IS 'Tenant subscription record';
COMMENT ON COLUMN sys_tenant_subscription.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN sys_tenant_subscription.package_id IS 'Package ID';
COMMENT ON COLUMN sys_tenant_subscription.start_time IS 'Subscription start time';
COMMENT ON COLUMN sys_tenant_subscription.end_time IS 'Subscription end time';
COMMENT ON COLUMN sys_tenant_subscription.status IS '0 active, 1 expired, 2 canceled';
COMMENT ON COLUMN sys_tenant_subscription.amount IS 'Subscription amount';
COMMENT ON COLUMN sys_tenant_subscription.payment_method IS 'Payment method';
COMMENT ON COLUMN sys_tenant_subscription.payment_no IS 'Payment order number';
CREATE INDEX IF NOT EXISTS idx_tenant_sub_tenant_id ON sys_tenant_subscription (tenant_id);

CREATE TABLE IF NOT EXISTS sys_tenant_bill (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subscription_id BIGINT DEFAULT NULL,
    bill_type VARCHAR(32) NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    status SMALLINT DEFAULT 0,
    remark VARCHAR(500) DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE sys_tenant_bill IS 'Tenant billing record';
COMMENT ON COLUMN sys_tenant_bill.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN sys_tenant_bill.subscription_id IS 'Related subscription ID';
COMMENT ON COLUMN sys_tenant_bill.bill_type IS 'subscribe, renew, upgrade';
COMMENT ON COLUMN sys_tenant_bill.amount IS 'Bill amount';
COMMENT ON COLUMN sys_tenant_bill.status IS '0 pending, 1 paid, 2 canceled';
COMMENT ON COLUMN sys_tenant_bill.remark IS 'Billing note';
COMMENT ON COLUMN sys_tenant_bill.pay_time IS 'Payment time';
CREATE INDEX IF NOT EXISTS idx_tenant_bill_tenant_id ON sys_tenant_bill (tenant_id);


-- ============================================================
-- source: postgres\workflow\00-schema.sql
-- ============================================================
-- PostgreSQL 版工作流扩展表。
-- Flowable 引擎运行时表由引擎自身维护，本文件只负责 Han 业务扩展表。

CREATE TABLE IF NOT EXISTS wf_category (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    category_code   VARCHAR(50)     NOT NULL,
    category_name   VARCHAR(100)    NOT NULL,
    parent_id       BIGINT          DEFAULT 0,
    ancestors       VARCHAR(500)    DEFAULT '',
    sort            INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    CONSTRAINT uk_wf_category_code UNIQUE (category_code, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_category_tenant ON wf_category(tenant_id);

CREATE TABLE IF NOT EXISTS wf_form (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    form_name       VARCHAR(100)    NOT NULL,
    form_key        VARCHAR(64)     NOT NULL,
    form_type       VARCHAR(20)     DEFAULT 'custom',
    form_content    TEXT,
    external_url    VARCHAR(500),
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_name     VARCHAR(50),
    create_dept     BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_name     VARCHAR(50),
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500),
    CONSTRAINT uk_wf_form_key UNIQUE (form_key, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_form_tenant ON wf_form(tenant_id);

CREATE TABLE IF NOT EXISTS wf_deploy_extend (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    deployment_id   VARCHAR(64)     NOT NULL,
    process_key     VARCHAR(64)     NOT NULL,
    process_name    VARCHAR(200),
    category_id     BIGINT,
    form_id         BIGINT,
    icon            VARCHAR(500),
    sort            INT             DEFAULT 0,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_time     TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_deployment ON wf_deploy_extend(deployment_id);
CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_tenant ON wf_deploy_extend(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_deploy_extend_process_key ON wf_deploy_extend(process_key);

CREATE TABLE IF NOT EXISTS wf_instance_extend (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL,
    process_instance_id VARCHAR(64)     NOT NULL,
    process_key         VARCHAR(64)     NOT NULL,
    process_name        VARCHAR(200),
    title               VARCHAR(200),
    business_key        VARCHAR(64),
    business_table      VARCHAR(100),
    category_id         BIGINT,
    start_user_id       BIGINT,
    start_user_name     VARCHAR(50),
    start_dept_id       BIGINT,
    start_dept_name     VARCHAR(100),
    current_task_name   VARCHAR(200),
    current_assignee    VARCHAR(200),
    status              SMALLINT        DEFAULT 0,
    result              SMALLINT,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    duration            BIGINT,
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP,
    del_flag            SMALLINT        DEFAULT 0,
    CONSTRAINT uk_wf_instance_extend_pi UNIQUE (process_instance_id)
);

CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_tenant ON wf_instance_extend(tenant_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_business ON wf_instance_extend(business_key);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_start_user ON wf_instance_extend(start_user_id);
CREATE INDEX IF NOT EXISTS idx_wf_instance_extend_status ON wf_instance_extend(status);

CREATE TABLE IF NOT EXISTS wf_copy (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL,
    process_instance_id VARCHAR(64)     NOT NULL,
    task_id             VARCHAR(64),
    task_name           VARCHAR(200),
    title               VARCHAR(200),
    user_id             BIGINT          NOT NULL,
    user_name           VARCHAR(50),
    origin_user_id      BIGINT,
    origin_user_name    VARCHAR(50),
    is_read             SMALLINT        DEFAULT 0,
    read_time           TIMESTAMP,
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag            SMALLINT        DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wf_copy_process_instance ON wf_copy(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_copy_user ON wf_copy(user_id);
CREATE INDEX IF NOT EXISTS idx_wf_copy_tenant ON wf_copy(tenant_id);


-- ============================================================
-- source: postgres\workflow\10-seed.sql
-- ============================================================
INSERT INTO wf_category (tenant_id, category_code, category_name, parent_id, sort, status)
VALUES
(1, 'oa', 'OA办公', 0, 1, 0),
(1, 'hr', '人事管理', 0, 2, 0),
(1, 'finance', '财务管理', 0, 3, 0),
(1, 'leave', '请假申请', 1, 1, 0),
(1, 'expense', '报销申请', 1, 2, 0),
(1, 'business_trip', '出差申请', 1, 3, 0),
(1, 'entry', '入职申请', 2, 1, 0),
(1, 'resign', '离职申请', 2, 2, 0),
(1, 'payment', '付款申请', 3, 1, 0),
(1, 'invoice', '发票申请', 3, 2, 0)
ON CONFLICT DO NOTHING;


-- ============================================================
-- source: postgres\open\00-schema.sql
-- ============================================================
-- PostgreSQL open-platform core tables.

CREATE TABLE IF NOT EXISTS open_app (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    app_name VARCHAR(100) NOT NULL,
    app_key VARCHAR(100) NOT NULL UNIQUE,
    app_secret VARCHAR(200) NOT NULL,
    app_icon VARCHAR(500),
    app_desc VARCHAR(500),
    app_type VARCHAR(20),
    redirect_uris TEXT,
    logout_uri VARCHAR(500),
    scopes VARCHAR(500),
    grant_types VARCHAR(200),
    access_token_ttl INT,
    refresh_token_ttl INT,
    require_pkce INT DEFAULT 0,
    auto_approve INT DEFAULT 0,
    status INT DEFAULT 0,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(20),
    contact_email VARCHAR(100),
    create_by BIGINT,
    create_name VARCHAR(50),
    update_by BIGINT,
    update_name VARCHAR(50),
    create_dept BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP,
    del_flag INT DEFAULT 0,
    remark VARCHAR(500)
);

COMMENT ON TABLE open_app IS 'Open platform application';

CREATE TABLE IF NOT EXISTS open_user_authorization (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT,
    user_id BIGINT,
    app_id BIGINT,
    app_key VARCHAR(100),
    scopes VARCHAR(500),
    authorize_time TIMESTAMP,
    last_access_time TIMESTAMP,
    status INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE open_user_authorization IS 'User authorization record';


-- ============================================================
-- source: postgres\open\10-seed.sql
-- ============================================================
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


-- ============================================================
-- source: aivideo\01-schema.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_video_project (
    project_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    project_name VARCHAR(200) NOT NULL,
    owner_user_id BIGINT,
    topic_type VARCHAR(50),
    target_platform VARCHAR(50),
    default_ratio VARCHAR(20) DEFAULT '9:16',
    default_style VARCHAR(100),
    default_shot_duration INT DEFAULT 5,
    candidate_image_count INT DEFAULT 3,
    preview_mode CHAR(1) DEFAULT '1',
    current_stage VARCHAR(32) DEFAULT 'DRAFT',
    project_status VARCHAR(32) DEFAULT 'DRAFT',
    budget_limit NUMERIC(12,4),
    estimated_cost NUMERIC(12,4) DEFAULT 0,
    actual_cost NUMERIC(12,4) DEFAULT 0,
    summary TEXT,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0,
    remark VARCHAR(500)
);

COMMENT ON TABLE ai_video_project IS 'AI short-drama project';

CREATE INDEX IF NOT EXISTS idx_ai_video_project_tenant ON ai_video_project (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_owner ON ai_video_project (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_status ON ai_video_project (project_status, current_stage);
CREATE INDEX IF NOT EXISTS idx_ai_video_project_update_time ON ai_video_project (update_time);

CREATE TABLE IF NOT EXISTS ai_video_source_document (
    document_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    source_type VARCHAR(20) DEFAULT 'TEXT',
    file_id BIGINT,
    file_name VARCHAR(200),
    raw_text TEXT,
    parsed_text TEXT,
    chapter_json TEXT,
    char_count BIGINT DEFAULT 0,
    parse_status VARCHAR(32) DEFAULT 'PENDING',
    parse_error TEXT,
    confirmed CHAR(1) DEFAULT '0',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_source_document IS 'AI short-drama source document';

CREATE INDEX IF NOT EXISTS idx_ai_video_doc_project ON ai_video_source_document (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_doc_tenant ON ai_video_source_document (tenant_id);

CREATE TABLE IF NOT EXISTS ai_video_content_version (
    version_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    document_id BIGINT,
    content_type VARCHAR(32) NOT NULL,
    version_no INT DEFAULT 1,
    title VARCHAR(200),
    content_text TEXT,
    content_json TEXT,
    prompt_template_id BIGINT,
    custom_prompt TEXT,
    model_id BIGINT,
    task_id BIGINT,
    selected CHAR(1) DEFAULT '0',
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_content_version IS 'AI short-drama text content version';

CREATE INDEX IF NOT EXISTS idx_ai_video_content_project_type ON ai_video_content_version (project_id, content_type);
CREATE INDEX IF NOT EXISTS idx_ai_video_content_task ON ai_video_content_version (task_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_content_selected ON ai_video_content_version (selected);

CREATE TABLE IF NOT EXISTS ai_video_character (
    character_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    character_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    age_desc VARCHAR(50),
    identity_desc VARCHAR(200),
    personality_tags VARCHAR(500),
    story_role VARCHAR(100),
    relationship_desc TEXT,
    appearance TEXT,
    hair_style VARCHAR(200),
    costume TEXT,
    color_style VARCHAR(200),
    negative_traits TEXT,
    prompt_text TEXT,
    completeness VARCHAR(32),
    missing_fields TEXT,
    locked_media_id BIGINT,
    voice_mode VARCHAR(32),
    voice_type VARCHAR(128),
    voice_name VARCHAR(128),
    voice_desc VARCHAR(512),
    voice_reference_media_id BIGINT,
    voice_sample_text VARCHAR(512),
    voice_speed_ratio NUMERIC(6,3),
    voice_volume_ratio NUMERIC(6,3),
    voice_pitch_ratio NUMERIC(6,3),
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_character IS 'AI short-drama character asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_character_project ON ai_video_character (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_character_locked_media ON ai_video_character (locked_media_id);

CREATE TABLE IF NOT EXISTS ai_video_scene (
    scene_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    scene_name VARCHAR(200) NOT NULL,
    scene_type VARCHAR(100),
    episode_no INT,
    time_desc VARCHAR(100),
    weather VARCHAR(100),
    atmosphere VARCHAR(200),
    visual_features TEXT,
    color_tone VARCHAR(200),
    props TEXT,
    negative_elements TEXT,
    prompt_text TEXT,
    completeness VARCHAR(32),
    missing_fields TEXT,
    locked_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_scene IS 'AI short-drama scene asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_scene_project ON ai_video_scene (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_scene_locked_media ON ai_video_scene (locked_media_id);

CREATE TABLE IF NOT EXISTS ai_video_shot (
    shot_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    episode_no INT DEFAULT 1,
    shot_no INT DEFAULT 1,
    duration_sec INT DEFAULT 5,
    scene_id BIGINT,
    character_ids TEXT,
    shot_type VARCHAR(100),
    camera_position VARCHAR(100),
    camera_movement VARCHAR(100),
    transition_before_type VARCHAR(32),
    transition_before_desc TEXT,
    transition_effect VARCHAR(64),
    stitch_group_no INT,
    action_desc TEXT,
    dialogue TEXT,
    voice_over TEXT,
    emotion VARCHAR(200),
    bgm_cue TEXT,
    sfx_cues TEXT,
    tts_start_ms INT,
    tts_end_ms INT,
    tts_speaker VARCHAR(128),
    tts_voice_type VARCHAR(128),
    prompt_text TEXT,
    reference_media_ids TEXT,
    keyframe_media_id BIGINT,
    video_media_id BIGINT,
    tail_frame_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    generation_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_shot IS 'AI short-drama shot asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_shot_project_episode ON ai_video_shot (project_id, episode_no, shot_no);
CREATE INDEX IF NOT EXISTS idx_ai_video_shot_scene ON ai_video_shot (scene_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_shot_status ON ai_video_shot (generation_status);

CREATE TABLE IF NOT EXISTS ai_video_media_asset (
    media_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    asset_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(32),
    biz_id BIGINT,
    file_id BIGINT,
    file_url VARCHAR(1000),
    thumbnail_file_id BIGINT,
    prompt_text TEXT,
    negative_prompt TEXT,
    model_id BIGINT,
    task_id BIGINT,
    params_json TEXT,
    candidate_no INT DEFAULT 1,
    selected CHAR(1) DEFAULT '0',
    asset_status VARCHAR(32) DEFAULT 'READY',
    cost_amount NUMERIC(12,4) DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_media_asset IS 'AI short-drama media asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_media_project ON ai_video_media_asset (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_biz ON ai_video_media_asset (biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_file ON ai_video_media_asset (file_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_media_selected ON ai_video_media_asset (selected);

CREATE TABLE IF NOT EXISTS ai_video_generation_task (
    task_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    tenant_id BIGINT DEFAULT 0,
    task_type VARCHAR(32) NOT NULL,
    biz_type VARCHAR(32),
    biz_id BIGINT,
    model_id BIGINT,
    prompt_template_id BIGINT,
    prompt_text TEXT,
    custom_prompt TEXT,
    params_json TEXT,
    provider_task_id VARCHAR(200),
    job_id BIGINT,
    task_status VARCHAR(32) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    estimated_cost NUMERIC(12,4) DEFAULT 0,
    actual_cost NUMERIC(12,4) DEFAULT 0,
    token_count INT,
    error_code VARCHAR(100),
    error_message TEXT,
    started_time TIMESTAMP,
    finished_time TIMESTAMP,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_generation_task IS 'AI short-drama generation task';

CREATE INDEX IF NOT EXISTS idx_ai_video_task_project ON ai_video_generation_task (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_status ON ai_video_generation_task (task_status);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_type ON ai_video_generation_task (task_type);
CREATE INDEX IF NOT EXISTS idx_ai_video_task_provider ON ai_video_generation_task (provider_task_id);

CREATE TABLE IF NOT EXISTS ai_video_review_record (
    review_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT,
    action_type VARCHAR(32) NOT NULL,
    before_status VARCHAR(32),
    after_status VARCHAR(32),
    comment TEXT,
    extra_prompt TEXT,
    review_user_id BIGINT,
    review_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_video_review_record IS 'AI short-drama review record';

CREATE INDEX IF NOT EXISTS idx_ai_video_review_project ON ai_video_review_record (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_review_target ON ai_video_review_record (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_review_user ON ai_video_review_record (review_user_id);

CREATE TABLE IF NOT EXISTS ai_video_project_setting (
    setting_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT,
    tenant_id BIGINT DEFAULT 0,
    text_model_id BIGINT,
    image_model_id BIGINT,
    video_model_id BIGINT,
    polish_prompt_template_id BIGINT,
    script_prompt_template_id BIGINT,
    character_prompt_template_id BIGINT,
    scene_prompt_template_id BIGINT,
    character_image_prompt_template_id BIGINT,
    scene_image_prompt_template_id BIGINT,
    shot_prompt_template_id BIGINT,
    video_prompt_template_id BIGINT,
    default_ratio VARCHAR(20) DEFAULT '9:16',
    default_resolution VARCHAR(50) DEFAULT '720p',
    default_shot_duration INT DEFAULT 5,
    image_candidate_count INT DEFAULT 2,
    video_candidate_count INT DEFAULT 1,
    preview_mode CHAR(1) DEFAULT '1',
    content_audit_enabled CHAR(1) DEFAULT '1',
    media_access_policy VARCHAR(20) DEFAULT 'PRIVATE',
    params_json TEXT,
    remark VARCHAR(500),
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_video_project_setting IS 'AI short-drama project setting snapshot';

CREATE INDEX IF NOT EXISTS idx_ai_video_setting_project ON ai_video_project_setting (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_setting_tenant_global ON ai_video_project_setting (tenant_id, project_id);

-- ============================================================
-- source: postgres\file\00-schema.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

CREATE TABLE sys_file (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    file_name       VARCHAR(200)    NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_url        VARCHAR(500),
    file_size       BIGINT          DEFAULT 0,
    file_type       VARCHAR(50)     DEFAULT '',
    mime_type       VARCHAR(100)    DEFAULT '',
    storage_type    VARCHAR(20)     DEFAULT 'local',
    bucket          VARCHAR(100)    DEFAULT '',
    md5             VARCHAR(64)     DEFAULT '',
    create_by       BIGINT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       SMALLINT        DEFAULT 0
);

CREATE TABLE sys_oss_config (
    oss_config_id  BIGSERIAL       PRIMARY KEY,
    config_key     VARCHAR(100)    NOT NULL,
    access_key     VARCHAR(500),
    secret_key     VARCHAR(500),
    bucket_name    VARCHAR(200),
    prefix         VARCHAR(200)    DEFAULT '',
    endpoint       VARCHAR(500),
    region         VARCHAR(100),
    is_https       CHAR(1)         DEFAULT '0',
    status         CHAR(1)         DEFAULT '1',
    remark         VARCHAR(500),
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_oss_config IS 'OSS存储配置';


-- ============================================================
-- source: postgres\file\10-seed.sql
-- ============================================================
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


-- ============================================================
-- source: postgres\ai\00-schema.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

CREATE TABLE ai_model (
    model_id        BIGSERIAL       PRIMARY KEY,
    model_name      VARCHAR(100)    NOT NULL,
    model_type      VARCHAR(20)     NOT NULL DEFAULT 'LLM',
    provider        VARCHAR(50)     NOT NULL DEFAULT 'openai',
    model_code      VARCHAR(100)    NOT NULL,
    base_url        VARCHAR(500)    NOT NULL,
    api_key         VARCHAR(500)    DEFAULT '',
    max_tokens      INTEGER         DEFAULT 2048,
    temperature     NUMERIC(3,2)    DEFAULT 0.70,
    status          CHAR(1)         DEFAULT '0',
    remark          VARCHAR(500)    DEFAULT '',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_model IS 'AI模型配置表';

-- =============================================
-- 24. 知识库表
-- =============================================
CREATE TABLE ai_knowledge_base (
    kb_id               BIGSERIAL       PRIMARY KEY,
    kb_name             VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    kb_type             VARCHAR(20)     NOT NULL DEFAULT 'general',
    embedding_model_id  BIGINT,
    document_count      INTEGER         DEFAULT 0,
    paragraph_count     INTEGER         DEFAULT 0,
    char_count          BIGINT          DEFAULT 0,
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_knowledge_base IS '知识库表';

-- =============================================
-- 25. 知识库文档表
-- =============================================
CREATE TABLE ai_document (
    doc_id          BIGSERIAL       PRIMARY KEY,
    kb_id           BIGINT          NOT NULL,
    doc_name        VARCHAR(500)    NOT NULL,
    doc_type        VARCHAR(20)     DEFAULT 'txt',
    file_path       VARCHAR(1000)   DEFAULT '',
    file_size       BIGINT          DEFAULT 0,
    char_count      BIGINT          DEFAULT 0,
    paragraph_count INTEGER         DEFAULT 0,
    index_status    VARCHAR(20)     DEFAULT 'pending',
    index_error     TEXT            DEFAULT '',
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_document IS '知识库文档表';
CREATE INDEX idx_ai_document_kb_id ON ai_document(kb_id);

-- =============================================
-- 26. 知识库段落表
-- =============================================
CREATE TABLE ai_paragraph (
    paragraph_id    BIGSERIAL       PRIMARY KEY,
    doc_id          BIGINT          NOT NULL,
    kb_id           BIGINT          NOT NULL,
    title           VARCHAR(500)    DEFAULT '',
    content         TEXT            NOT NULL,
    char_count      INTEGER         DEFAULT 0,
    hit_count       INTEGER         DEFAULT 0,
    embedding       TEXT,
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       INTEGER         DEFAULT 0
);
COMMENT ON TABLE ai_paragraph IS '知识库段落表';
CREATE INDEX idx_ai_paragraph_doc ON ai_paragraph(doc_id);
CREATE INDEX idx_ai_paragraph_kb ON ai_paragraph(kb_id);

-- =============================================
-- 27. MCP服务器配置表
-- =============================================
CREATE TABLE ai_mcp_server (
    mcp_id          BIGSERIAL       PRIMARY KEY,
    server_name     VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000)   DEFAULT '',
    transport_type  VARCHAR(30)     NOT NULL DEFAULT 'sse',
    command         VARCHAR(500)    DEFAULT '',
    args            TEXT            DEFAULT '[]',
    env_vars        TEXT            DEFAULT '{}',
    url             VARCHAR(500)    DEFAULT '',
    tools           TEXT            DEFAULT '[]',
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_mcp_server IS 'MCP服务器配置表';

-- =============================================
-- 28. AI工作流表
-- =============================================
CREATE TABLE ai_workflow (
    workflow_id         BIGSERIAL       PRIMARY KEY,
    workflow_name       VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    workflow_type       VARCHAR(20)     NOT NULL DEFAULT 'simple',
    model_id            BIGINT,
    knowledge_base_ids  TEXT            DEFAULT '[]',
    mcp_server_ids      TEXT            DEFAULT '[]',
    system_prompt       TEXT            DEFAULT '',
    flow_config         TEXT            DEFAULT '{}',
    prologue            VARCHAR(2000)   DEFAULT '',
    published           CHAR(1)         DEFAULT '0',
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_workflow IS 'AI工作流表';

-- =============================================
-- 29. AI对话会话表
-- =============================================
CREATE TABLE ai_conversation (
    conversation_id BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(500)    DEFAULT '新对话',
    workflow_id     BIGINT,
    model_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    message_count   INTEGER         DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_conversation IS 'AI对话会话表';
CREATE INDEX idx_ai_conversation_user ON ai_conversation(user_id);

-- =============================================
-- 30. AI对话消息表
-- =============================================
CREATE TABLE ai_chat_message (
    message_id      BIGSERIAL       PRIMARY KEY,
    conversation_id BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    content         TEXT            NOT NULL,
    token_count     INTEGER         DEFAULT 0,
    sort_order      INTEGER         DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_chat_message IS 'AI对话消息表';
CREATE INDEX idx_ai_chat_message_conversation ON ai_chat_message(conversation_id);
CREATE INDEX idx_ai_chat_message_tenant ON ai_chat_message(tenant_id);

-- =============================================
-- 31. AI智能体表
-- =============================================
CREATE TABLE ai_agent (
    agent_id       BIGSERIAL       PRIMARY KEY,
    agent_name     VARCHAR(100)    NOT NULL,
    description    TEXT,
    avatar         VARCHAR(500),
    system_prompt  TEXT,
    prologue       TEXT,
    model_id       BIGINT,
    knowledge_base_ids TEXT,
    mcp_server_ids TEXT,
    temperature    NUMERIC(3,2)    DEFAULT 0.7,
    max_tokens     INT             DEFAULT 2048,
    published      CHAR(1)         DEFAULT '0',
    status         CHAR(1)         DEFAULT '0',
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       INT             DEFAULT 0
);
COMMENT ON TABLE ai_agent IS 'AI智能体';

-- =============================================
-- 32. Prompt模板表
-- =============================================
CREATE TABLE ai_prompt_template (
    template_id   BIGSERIAL       PRIMARY KEY,
    tenant_id     BIGINT,
    template_name VARCHAR(100)    NOT NULL,
    category      VARCHAR(20)     NOT NULL DEFAULT 'system',
    content       TEXT            NOT NULL,
    variables     TEXT,
    description   VARCHAR(500),
    built_in      INT             NOT NULL DEFAULT 0,
    status        CHAR(1)         NOT NULL DEFAULT '0',
    create_by     VARCHAR(64)     DEFAULT '',
    create_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by     VARCHAR(64)     DEFAULT '',
    update_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_prompt_template IS 'Prompt模板表';
CREATE INDEX idx_prompt_tpl_tenant ON ai_prompt_template(tenant_id);

-- =============================================
-- 33. Token用量记录表
-- =============================================
CREATE TABLE ai_token_usage (
    usage_id          BIGSERIAL       PRIMARY KEY,
    tenant_id         BIGINT,
    user_id           BIGINT,
    conversation_id   BIGINT,
    model_id          BIGINT,
    model_name        VARCHAR(100),
    prompt_tokens     INT             DEFAULT 0,
    completion_tokens INT             DEFAULT 0,
    total_tokens      INT             DEFAULT 0,
    create_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_token_usage IS 'AI Token用量记录表';
CREATE INDEX idx_token_usage_tenant ON ai_token_usage(tenant_id);
CREATE INDEX idx_token_usage_user ON ai_token_usage(user_id);
CREATE INDEX idx_token_usage_time ON ai_token_usage(create_time);

-- =============================================
-- 34. 知识图谱节点表
-- =============================================
CREATE TABLE ai_graph_node (
    node_id        BIGSERIAL       PRIMARY KEY,
    kb_id          BIGINT,
    node_name      VARCHAR(200)    NOT NULL,
    node_type      VARCHAR(50)     NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_graph_node IS '知识图谱节点';
CREATE INDEX idx_graph_node_kb ON ai_graph_node(kb_id);
CREATE INDEX idx_graph_node_type ON ai_graph_node(node_type);

-- =============================================
-- 35. 知识图谱关系表
-- =============================================
CREATE TABLE ai_graph_edge (
    edge_id        BIGSERIAL       PRIMARY KEY,
    kb_id          BIGINT,
    source_node_id BIGINT          NOT NULL,
    target_node_id BIGINT          NOT NULL,
    relation_type  VARCHAR(100)    NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_graph_edge IS '知识图谱关系';
CREATE INDEX idx_graph_edge_kb ON ai_graph_edge(kb_id);
CREATE INDEX idx_graph_edge_source ON ai_graph_edge(source_node_id);
CREATE INDEX idx_graph_edge_target ON ai_graph_edge(target_node_id);


-- ============================================================
-- source: postgres\ai\10-seed.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

INSERT INTO ai_model (model_name, model_type, provider, model_code, base_url, api_key, max_tokens, temperature, status, remark) VALUES
('DeepSeek Chat', 'LLM', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', '', 4096, 0.70, '1', 'DeepSeek对话模型，需配置API Key'),
('DeepSeek Reasoner', 'LLM', 'deepseek', 'deepseek-reasoner', 'https://api.deepseek.com/v1', '', 8192, 0.00, '1', 'DeepSeek推理模型，需配置API Key'),
('通义千问 Plus', 'LLM', 'qwen', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 4096, 0.70, '1', '阿里通义千问Plus，需配置API Key'),
('智谱 GLM-4', 'LLM', 'zhipu', 'glm-4', 'https://open.bigmodel.cn/api/paas/v4', '', 4096, 0.70, '1', '智谱AI GLM-4，需配置API Key'),
('OpenAI GPT-4o', 'LLM', 'openai', 'gpt-4o', 'https://api.openai.com/v1', '', 4096, 0.70, '1', 'OpenAI GPT-4o，需配置API Key'),
('Ollama 本地模型', 'LLM', 'ollama', 'llama3', 'http://localhost:11434/v1', '', 4096, 0.70, '1', 'Ollama本地部署模型'),
('火山语音合成', 'TTS', 'volcengine', 'volc-tts', 'https://openspeech.bytedance.com/api/v1/tts', '', 256, 0.70, '1', 'API Key填JSON: appId/accessToken/cluster/defaultVoiceType'),
('火山 VOD 视频剪辑合成', 'VIDEO_EDIT', 'volcengine', 'vod-direct-edit', 'https://vod.volcengineapi.com', '', 256, 0.70, '1', 'API Key填JSON: accessKey/secretKey/space/application/region');

-- 17. Prompt模板预置
INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status) VALUES
(NULL, '通用助手', 'system', '你是一个智能助手，请用专业、简洁的方式回答用户的问题。', NULL, '通用对话场景的系统提示词', 1, '0'),
(NULL, '翻译助手', 'system', '你是一位专业的翻译专家。请将用户输入的内容翻译为{{targetLang}}，保持原文语义和风格。', '["targetLang"]', '多语言翻译场景', 1, '0'),
(NULL, '代码审查', 'system', '你是一位资深的{{language}}开发工程师，请对用户提供的代码进行审查，指出潜在问题并给出改进建议。', '["language"]', '代码审查场景', 1, '0'),
(NULL, '文档总结', 'system', '请对以下内容进行总结，提取关键要点，用简洁的条目形式输出，不超过{{maxPoints}}条。', '["maxPoints"]', '长文档摘要场景', 1, '0'),
(NULL, 'SQL生成', 'system', '你是一位数据库专家，请根据用户的自然语言描述生成对应的{{dbType}} SQL语句。请确保SQL语法正确且高效。', '["dbType"]', 'SQL语句生成场景', 1, '0');

-- 17.1 AI短剧 Prompt 模板预置
INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧原文润色', 'aivideo_text',
$$请将以下原文润色为适合 AI 短剧改编的文本。
要求：
1. 保留主线、人物关系和核心冲突。
2. 强化人物动机、情绪转折、戏剧张力和画面感。
3. 语言清晰可拍，避免过度文学化。
4. 输出完整润色稿，不要输出解释。

项目：{{projectName}}
风格：{{style}}
原文：
{{rawText}}$$,
'["projectName","style","rawText"]', 'AI短剧原文润色默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧原文润色');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧剧本生成', 'aivideo_text',
$$请将以下润色文本改写为短剧剧本。
要求：
1. 按场次组织，包含人物、场景、动作、对白、旁白/画外音、心声/心理活动和情绪提示。
2. 对白、旁白/画外音、心声/心理活动必须三轨分清；低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，禁止写成旁白、画外音或心声。
3. 每个场次都要具备可拍摄的空间、行为和冲突推进。
4. 镜头描述要能继续拆分为分镜，避免空泛形容。
5. 输出短剧剧本正文，不要输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
润色文本：
{{polishedText}}$$,
'["projectName","targetPlatform","ratio","polishedText"]', 'AI短剧剧本生成默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧剧本生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧资产提取', 'aivideo_text',
$$请从短剧剧本中提取角色、场景、分镜，必须只输出 JSON 对象，不要输出解释。
JSON 结构：
{
  "characters": [
    {
      "characterName": "",
      "gender": "",
      "ageDesc": "",
      "identityDesc": "",
      "personalityTags": [""],
      "storyRole": "",
      "relationshipDesc": "",
      "appearance": "",
      "hairStyle": "",
      "costume": "",
      "colorStyle": "",
      "negativeTraits": "",
      "promptText": "",
      "completeness": "",
      "missingFields": [""]
    }
  ],
  "scenes": [
    {
      "sceneName": "",
      "sceneType": "",
      "episodeNo": 1,
      "timeDesc": "",
      "weather": "",
      "atmosphere": "",
      "visualFeatures": "",
      "colorTone": "",
      "props": "",
      "negativeElements": "",
      "promptText": "",
      "completeness": "",
      "missingFields": [""]
    }
  ],
  "shots": [
    {
      "episodeNo": 1,
      "shotNo": 1,
      "durationSec": 5,
      "sceneName": "",
      "characterNames": [""],
      "shotType": "",
      "cameraPosition": "",
      "cameraMovement": "",
      "actionDesc": "",
      "dialogue": "",
      "voiceOver": "",
      "emotion": "",
      "promptText": ""
    }
  ]
}

项目：{{projectName}}
剧本：
{{scriptText}}$$,
'["projectName","scriptText"]', 'AI短剧角色场景分镜提取默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧资产提取');

-- 17.2 AI短剧 Prompt 模板长提示词与全局默认配置
UPDATE ai_prompt_template
SET content = $aivideo_polish$
# AI短剧原文润色默认模板

【参考提示词原文】
没问题，已按要求删除了所有关于BGM、音效的建议，仅保留纯文案的修改指令。这是**纯文案版**的最终优化指令：

# 顶级小说推文改文指令（纯文案版）
## 任务说明
你是一个顶级的小说推文改文专家。你的唯一任务是将小说原文改写为适合短视频配音的**“第一人称解说文案”**。要求全程紧扣**完播率、转粉率**两大核心，生成的文案需自带情绪、自带画面、自带悬念，可直接用于抖音/快手等短视频平台的小说推文配音与制作。

## 第一维度：爆款开头重构（最高优先级/严禁套用原文第一句）
1.  **核心要求**：从【八大金刚公式库】中**精准匹配1种**，必须1秒抓住观众注意力，3秒内抛出核心冲突/悬念。
2.  **字数限制**：开头单段**30-50字**，严禁超过55字。
3.  **公式适配规则**（按网文爆款概率优先级排序）：
    - 爽文/反套路文：优先用**经典反转钩、极致反差钩、身份错位钩**
    - 悬疑/灵异文：优先用**猎奇悬疑钩、夸张反常钩、结局先行钩**
    - 虐恋/情感文：优先用**人性抉择钩、荒诞现实钩**
4.  **示例参考**：
    - 极致反差钩：他是京城人人唾弃的废柴赘婿，却在家族覆灭那日，当众唤醒了沉睡千年的上古血脉。
    - 猎奇悬疑钩：墙角的玩偶突然朝我眨了眼，漆黑的眼珠里，竟映出我死后第七天的模样。

## 第二维度：转换密度与节奏（硬性执行/适配配音与剪辑）
1.  **分段标准**
    - 基础密度：每 1000 字原文重构**38-40段**（误差 ±5%），适配短视频15-60秒的配音节奏。
    - 单段限制：**单段字数≤50字**，严禁出现60字以上长句；每段独立成意，不堆砌信息。
    - 特殊调整：高潮/反转段可拆分为2段（每段≥20字），强化情绪爆发点；过渡段可压缩至15-20字，保证节奏不拖沓。
2.  **角色名称规范**
    - 自动识别原文角色，统一称呼格式（如：主角统一用“我”，配角用全名/昵称，避免“他/她”混淆）。
    - 多角色同时出现时，优先标注核心角色名称，次要角色可在后续段落中补全称呼。
3.  **台词格式升级（适配配音/字幕）**
    - 标准格式：**角色名说（情绪提示）：“台词内容”**
    - 情绪提示要求：具体、具象，禁止模糊表述（例：轻蔑地嗤笑、颤抖着嘶吼、温柔低吟、冷漠冰冷）
    - 特殊处理：多句连续台词合并为1段，标注整体情绪；简短对话拆分为独立段，增强节奏感。

## 第三维度：核心衔接与五感+画面技巧（必执行/强化视听体验）
### 1. 关联词使用规范
- 词库限定：仅使用以下指定关联词，**同一复句/相邻段落严禁重复使用相同衔接词**，避免语言单调。
  基础词库：然而、却、不过、殊不知、岂料、果然、果不其然、谁知、哪料到、竟然、偏偏、不料、没想到
  进阶词库：即便……也、不但不……反而、之所以……是因为、由于……因此、由此可见
- 用法要求：衔接词需放在句首（独立成小句），或句中分隔前后逻辑，增强语句层次感。

### 2. 五感+画面落地技巧（适配短视频画面剪辑）
- 核心规则：**先感知，后行动**——人物所有动作、对话、内心活动，必须先通过视觉、听觉、触觉、嗅觉触发，再执行后续行为。
- 五感描写强制要求（每3段至少出现1种五感细节）：
  - **视觉**：具体颜色、动作幅度、神态细节（如：猩红的血渍、攥紧的青筋、嘴角裂到耳根）
  - **听觉**：拟声词、环境音、语气音（如：脆响、闷哼、死寂、轻笑）
  - **触觉**：触感、温度（如：刺骨的寒意、滚烫的血液、粗糙的布料）
  - **嗅觉**：气味（如：刺鼻的血腥味、淡淡的檀香）
- 起手式备选：见此情形、目睹、听到这话、感受着、指尖触到、鼻尖萦绕、映入眼帘、入耳皆是

## 第四维度：写作原则与核心禁忌（严禁违反/保证内容质量）
### 1. 详略得当（适配短视频信息接收习惯）
- 必详写：关键对话、情绪爆发点、核心反转、角色神态（需具象化描写，避免抽象表述）。
  示例：看着她跪在地上磕得头破血流，我只是冷眼看着，指尖轻轻摩挲着腰间的玉佩。
- 需略写：过渡性环境描写、重复动作、非核心角色行为（用1-2句话概括，不占用篇幅）。
  示例：秦朝颜爬起来后，注意到面前站着一名少年。

### 2. 核心禁忌（严格遵守，避免内容翻车）
- 行为闭环：所有动作描写后必须加**“后”**字，形成完整动作逻辑。
  示例：对：将红烧肉端上桌后，我便开始吃饭。
- 视角规范：同一视角全程用“我”；切换至其他角色视角时，**必须明确替换角色名称**，严禁混用“他/她/我”。
- 去冗余：删除所有无意义语气词（“的、了、呢、吧、啊”等），仅保留增强情绪的语气词（“啊、呀”仅用于情绪爆发段）。
- 网感植入：每篇文案自然融入**1-2个爆款网感词汇**（如：拿捏、YYDS、细思极恐、封神、炸裂、绝绝子），增强平台传播力。

## 第五维度：爆款钩子设计（强化转化/适配视频制作）
### 1. 钩子设计（每段必带小钩子/结尾必带大钩子）
- 段内钩子：每段结尾**预留1个悬念点**（如：“可我万万没想到，他竟藏着这样的秘密……”“就在这时，门外传来了熟悉的脚步声……”）。
- 结尾大钩子：文案最后1段必须抛出**核心追剧悬念**，引导观众评论/关注（如：“后续他能否成功复仇？关注我，下集揭晓真相！”“女主的真实身份到底是什么？点赞过万，立刻更新！”）。

## 第六维度：输出标准与验收要求
### 1. 输出格式
1.  首段：必须是重构后的**爆款开头**，单独成行。
2.  分段：段与段之间**空一行**，清晰区分，适配配音分段播放。
3.  台词：严格按照**角色名说（情绪提示）：“台词内容”**格式，单独成行。

### 2. 验收标准（生成后自查/人工审核用）
1.  开头3秒：能快速抓住注意力，无废话。
2.  分段节奏：每段≤50字，38-40段/1000字，适配配音语速。
3.  情绪浓度：每1000字至少包含**3个情绪爆发点**（爽点/虐点/悬念点）。
4.  画面适配：每段至少包含1个可具象化的画面/动作细节，适配视频剪辑。
5.  无违规：无原文第一句套用、无重复关联词、无冗余语气词、无视角混乱。

【系统自动化适配规则】
1. 完整遵守上方“顶级小说推文改文指令（纯文案版）”的改文逻辑。
2. 忽略参考提示词中任何寒暄、确认、等待用户继续提供材料的句子，直接根据下方原文输出润色稿。
3. 输出只保留可直接进入短剧改编的中文正文；不要输出执行说明、分析过程、Markdown 围栏。
4. 保留主线冲突与核心情绪，强化角色动机、画面感、悬念和短视频配音节奏。

【当前任务变量】
项目：{{projectName}}
风格：{{style}}
目标平台：{{targetPlatform}}
原文：
{{rawText}}
$aivideo_polish$,
    variables = '["projectName","style","targetPlatform","rawText"]',
    description = 'AI短剧原文润色默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧原文润色';

UPDATE ai_prompt_template
SET content = $aivideo_asset$
# AI短剧角色 / 场景 / 分镜提取指令

请严格依据角色构建、电影级纯净场景、剧本分镜三组参考提示词规则，从短剧剧本中提取角色、场景、分镜。
必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。
JSON key 必须保持英文，所有字段值必须使用中文。

## 角色构建规则
1. 你是电影级角色概念设计师，需要先解析角色心理画像、社会身份、人格标签和故事功能。
2. 每个角色必须输出鲜明、可区分的视觉方案，包含年龄、发型、眼神、服装材质、主色辅色和配饰。
3. promptText 要可直接用于 Seedance 视频角色锚定图生成，必须写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。
4. promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身。

## Seedance 视频场景锚点规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，并覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
3. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference。
4. 场景 promptText 必须写成单镜头视频首帧/环境锚点：前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。

## 剧本分镜规则
1. 面向 Seedance 2.0 / 即梦 2.0 的视频生成逻辑拆解镜头。
2. 全局禁止出现其他人；画面必须通过单人特写、主观视角或环境遮挡，把视觉重心锁定在当前核心主角。
3. 严格区分 dialogue、voiceOver 和心理画面：dialogue 只写角色说出口并可口型同步的话；voiceOver 只写可发声但角色不张嘴的旁白/画外音；心理活动默认不写入 voiceOver，优先写入 actionDesc/promptText/emotion 用画面表现。
3A. 低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，必须写入 dialogue；脑海里闪过、想到、意识到、想象、回忆、触感、心里一动等心理内容默认不朗读，禁止写成普通 voiceOver。
4. 每个分镜必须明确地点；延续场景时体现“延续上个分镜场景，机位微调”。
5. 动作要衔接，不能瞬移；镜头需包含微动作、眼神、呼吸、肢体、环境变化等可拍内容。
6. shotType、cameraPosition、cameraMovement 优先使用极焦特写、近景推轨、环绕摇镜、慢动作/延时、手持震动等专业运镜词。
7. durationSec 使用项目镜头秒数：{{defaultShotDuration}}；如果剧情确实需要短镜头，也不得低于 3 秒。

## 输出 JSON 结构
{
  "characters": [{"characterName":"","gender":"","ageDesc":"","identityDesc":"","personalityTags":[""],"storyRole":"","relationshipDesc":"","appearance":"","hairStyle":"","costume":"","colorStyle":"","negativeTraits":"","promptText":"","completeness":"","missingFields":[""]}],
  "scenes": [{"sceneName":"","sceneType":"","episodeNo":1,"timeDesc":"","weather":"","atmosphere":"","visualFeatures":"","colorTone":"","props":"","negativeElements":"","promptText":"","completeness":"","missingFields":[""]}],
  "shots": [{"episodeNo":1,"shotNo":1,"durationSec":{{defaultShotDuration}},"sceneName":"","characterNames":[""],"shotType":"","cameraPosition":"","cameraMovement":"","actionDesc":"","dialogue":"","voiceOver":"","emotion":"","promptText":""}]
}

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
风格：{{style}}
默认镜头秒数：{{defaultShotDuration}}

剧本：
{{scriptText}}
$aivideo_asset$,
    variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText"]',
    description = 'AI短剧角色场景分镜提取默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取';

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧场景图生成', 'aivideo_image',
$$不能出现其他人, 无人, 纯场景, no humans, empty scene, single shot reference。

Seedance 视频生成专用场景参考图默认提示词。
请生成一张可作为视频首帧/环境锚点的单镜头纯净场景图：绝对无人、无人物、无人物剪影、无脸、无身体部位，画面中不能出现任何角色名或角色痕迹。

## 核心执行逻辑
1. 绝对真空与匿名：画面中严禁出现任何人影，提示词中严禁出现任何角色人名。
2. 场景命名法则：场景必须具备辨识度，避免单一名词。
3. 四大核心要素：必须完整涵盖环境类型、具体时间、空间氛围、视觉主要特征。
4. 视频参考图硬规则：只允许单一镜头画面，禁止拼图、分栏、设定板、地图、俯视平面图、漫画格、文字、水印、logo 或说明标签。
5. Prompt 开头必须保留“不能出现其他人, 无人, 纯场景,”，并包含 no humans、empty scene、single shot reference。
6. 输出控制：不要生成解释、不要生成括号说明，直接生成图片画面。

## 场景设定
项目：{{projectName}}
目标平台：{{targetPlatform}}
视觉风格：{{style}}
画幅构图：{{ratio}} Seedance 视频场景参考图，单镜头画面，极高画质，纯净无人的空间
清晰度：{{resolution}}

场景名称：{{sceneName}}
环境类型：{{sceneType}}
时间时刻：{{timeDesc}}
天气光线：{{weather}}
空间氛围：{{atmosphere}}
主要特征：{{visualFeatures}}
建议色调：{{colorTone}}
核心道具：{{props}}
禁用元素：{{negativeElements}}
原始场景提示词：{{scenePromptText}}

## 画面要求
将以上环境细节融合成一段精简、极具冲击力的生图描述词；前景、中景、远景空间关系清晰，地面或可行动区域明确，主光源方向和色调稳定，可作为后续 Seedance 分镜视频首帧/背景锚点；严禁出现人、人物剪影、脸、身体部位、crowd、person、human；严禁拼图、分栏、设定板、漫画格和文字标签。$$,
'["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
'AI短剧场景图生成默认模板，强制纯场景无人和单镜头视频环境锚点', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景图生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧角色构建', 'aivideo_asset',
$aivideo_character$
# AI短剧角色构建默认模板

【系统自动化适配规则】
1. 你是电影级角色资产规划师，只负责从剧本中提取稳定角色锚点，并输出可入库 JSON。
2. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或表格外解释。
3. JSON key 必须保持英文，所有字段值必须使用中文。
4. 先解析角色画像：代号、年龄/生命阶段、性别或物种、身份、人格标签、故事功能。
5. 人类角色写清年龄、自然发色、具体发型、眼神神态、服装材质、主色辅色、鞋履配饰。
6. 动物、宠物、怪物、机器人、器物精灵等非人类角色必须保留物种本体，写清品种/体型/毛色/眼睛/鼻子/耳朵/尾巴/标志性特征，禁止改成人类演员。
7. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。
8. promptText 必须可直接用于 Seedance 视频角色锚定图生成，写成：单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。
9. promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身；动物保持自然四足站立，不拟人化。
10. 无法从剧本确认的信息写入 missingFields，不要编造关键事实。

【当前任务变量】
项目：{{projectName}}
世界观风格：{{style}}
目标平台：{{targetPlatform}}
角色文案/剧本：
{{scriptText}}
$aivideo_character$,
'["projectName","style","targetPlatform","scriptText"]',
'AI短剧角色构建默认模板，强制角色图锚点使用单主体视频参考图', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧角色构建');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧场景设计', 'aivideo_asset',
$aivideo_scene$
# AI短剧场景设计默认模板

【参考提示词原文】
Role: Seedance 视频生成专用场景参考图设计专家

核心执行逻辑（后台规则）：
1. 绝对真空与匿名：画面中严禁出现任何人影，场景描述文字中严禁出现任何角色人名。
2. 场景命名法则：每个场景名称必须在【四个字以上】，通过具体的修饰词增加辨识度（严禁使用单一名词）。
3. 四大核心要素：场景描述必须完整涵盖：【环境类型】、【具体时间】、【空间氛围】、【视觉主要特征】。
4. 视频参考图硬规则：只允许单镜头画面，前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、地图、俯视平面图、漫画格、文字、水印、logo 或说明标签。
5. Prompt开头：所有Prompt必须以“不能出现其他人, 无人, 纯场景,”开头，并包含 no humans、empty scene、single shot reference。
6. 输出控制：严禁输出任何括号内的说明文字，直接输出具体内容。

第一步：场景提取清单
（按顺序编号列出文案中的所有地点：场景全称 | 核心氛围 | 建议色调）

第二步：专业场景设定表（按此格式逐一输出）

- 场景名称：[四个字以上的独特命名]
- 画幅构图：横向 16:9 Seedance 视频场景参考图，单镜头画面，极高画质，纯净无人的空间。
- 视觉风格：【填入用户指定风格】，极致细节。
- 场景描述：
  - 【环境类型】：[具体的地理/建筑空间属性]
  - 【时间时刻】：[精确到时段的天气与光线状态]
  - 【空间氛围】：[如：压抑、神圣、破败、宁静等视觉情绪描述]
  - 【主要特征】：[具体的材质、核心物件、前中后景的标志性元素，严禁提及角色姓名]
- Prompt (直接复制)：不能出现其他人, 无人, 纯场景, [将上述所有环境细节融合成一段精简、极具冲击力的单镜头视频首帧/环境锚点描述词，包含：no humans, empty scene, single shot reference；前景、中景、远景和地面可行动区域清楚；禁止拼图、分栏、设定板、漫画格、文字标签]


---
指令已确认。请告知我您的【风格要求】与【文案】，我将为您生成纯净且具有唯一性的场景设定。

【系统自动化适配规则】
1. 完整遵守上方“Seedance 视频生成专用场景参考图设计专家”提示词，用于提取纯净场景资产。
2. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或额外解释。
3. JSON key 必须保持英文，所有字段值必须使用中文。
4. 场景必须纯净无人，场景字段、场景描述和 promptText 严禁出现角色姓名、人影、人物剪影、脸、身体部位或额外人物。
5. 场景名称必须四个字以上；promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference；必须写成单镜头视频首帧/环境锚点，前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。

【当前任务变量】
项目：{{projectName}}
世界观风格：{{style}}
画幅：{{ratio}}
剧本：
{{scriptText}}
$aivideo_scene$,
'["projectName","style","ratio","scriptText"]',
'AI短剧场景设计默认模板，来自AIVideo电影级纯净场景参考提示词优化版', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景设计');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧分镜提取', 'aivideo_asset',
$aivideo_shot$
# AI短剧分镜提取默认模板

【参考提示词原文】
# 角色设定 (System Role)
你是一个顶级影视剧导演与分镜规划专家。你的任务是将小说文案拆解为最适配【Seedance 2.0 / 即梦 2.0 (Jimeng 2.0)】视频生成模型底层逻辑的 15秒/镜头 电影级分镜脚本。

## ⚠️ 任务前置准备 (Pre-loading Logic)
1. **逻辑对齐**：在执行前，请深度调用你的知识储备，对齐【Seedance 2.0】在“长视频连贯性”、“中式审美建模”、“物理运动规律”以及“精准对口型 (Lip-sync)”方面的运行逻辑。
2. **全局禁令**：依据用户设定，所有场景中【不能出现其他人 (No other people)】。画面必须始终通过单人特写、主观视角或环境遮挡，将视觉重心唯一锁定在当前核心主角身上。

## 第一维度：音画三轨驱动协议 (Audio-Visual Protocol)
你必须严格区分对话与旁白，并匹配不同的画面表现：
1. **台词 (Dialogue)**：文中角色直接说的话。
   - 标注格式：**角色名说：“台词内容”**。
   - 画面：强制开启对口型模式，人物张嘴，表情与台词语气高度同步。
2. **画外音 (Voice-over/VO)**：文中的可发声旁白、角色画外音、环境氛围渲染；心理活动默认不进 VO，除非明确写成“角色名（内心独白）”。
   - 标注格式：**（画外音：内容）**。
   - 画面：人物不张嘴。通过眼神微动、长睫颤抖、呼吸起伏或环境空镜承接情感。

## 第二维度：时空连贯性协议 (Temporal Consistency)
1. **场景锚定**：每个分镜必须明确地点。若场景延续，必须标注“延续上个分镜场景，机位微调”。
2. **动作衔接**：前一镜头结尾的姿态必须是后一镜头起始的触发点。严禁文案外“瞬移”，若位移必须安排转场动作（如：转身、推门）。
3. **视觉一致**：主角的着装、发型、环境光影（如：侧逆光、残阳、幽冷）必须全局高度统一。

## 第三维度：单镜头硬性标准 (15s Shot Standards)
1. **时长密度**：每个分镜固定 15 秒，严禁画面静止。包含 5-8 组细微动作指令。
2. **专业运镜词库 (必须标注)**：
   - 【极焦特写】：聚焦瞳孔收缩、泪滴划过、指缝发白、喉结滚动。
   - 【近景推轨】：镜头匀速拉近，增强压迫感或情感递进。
   - 【环绕摇镜】：360度旋转，表现混乱、迷茫或被情感包围。
   - 【慢动作/延时】：用于表现情感爆发瞬间或细腻的神态余韵。
   - 【手持震动】：表现极度愤怒、恐惧或虚弱时的心理不稳。

## 第四维度：分镜脚本输出格式
【分镜序号】：[核心冲突点描述]
场景描述：[地点/光影/人物精细状态。若延续需注明：场景同上]
时间轴拆解 (Timeline)：
0-4 秒：【镜头语言】动作起始 + **（画外音/VO：内容）**。
4-8 秒：【镜头语言】细节反应 + **角色名说 (Dialogue)：“台词内容”**。
8-12 秒：【镜头语言】情绪转折 + **（心声/心理活动：不发声，只用眼神、动作或画面隐喻表现）**。
12-15 秒：【镜头语言】最终定格，预留衔接下个镜头的动态趋势。

## 第五维度：风险规避 (高级感转换)
- 严禁低俗。用指尖颤抖、面红耳赤、呼吸热气氤氲、指甲陷入掌心、眼神角力等高级感镜头表现张力。

## ⚠️ 确认执行逻辑 (Acknowledgement Required)
在开始执行之前，请不要直接生成分镜。请先：
1. 简述你对【Seedance 2.0 / 即梦 2.0】底层视频生成逻辑（运镜、连贯性、微表情）的理解。
2. 确认你已掌握【15秒/镜头】、【音画分离（台词vs画外音）】及【不能出现其他人】的硬性要求。

如果你已准备就绪，请回复：“导演，Seedance 2.0 脚本引擎已就绪，请发送文案，我将为您拆解 15 秒电影级分镜。”

【系统自动化适配规则】
1. 完整遵守上方“顶级影视剧导演与分镜规划专家”提示词，用于生成短剧分镜。
2. 参考提示词末尾的“确认执行逻辑”只作为规则来源；在系统自动流程里不要先回复确认，必须直接拆解分镜。
3. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或额外解释。
4. JSON key 必须保持英文，所有字段值必须使用中文。
5. characters.promptText 必须可直接用于 Seedance 视频角色锚定图生成，写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。
6. characters.promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身；动物保持自然四足站立，不拟人化。
7. durationSec 只能在 5、6、8 中动态选择；动作超过预算必须拆镜头，不能硬塞到一个镜头。

【当前任务变量】
项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
默认镜头秒数：{{defaultShotDuration}}
剧本：
{{scriptText}}
$aivideo_shot$,
'["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]',
'AI短剧分镜提取默认模板，来自AIVideo剧本分镜参考提示词优化版', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧分镜提取');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧角色图生成', 'aivideo_image',
$aivideo_character_image$
# AI短剧视频角色锚定图生成默认模板

【系统自动化适配规则】
1. 你是 Seedance 视频生成专用角色参考图设计专家，只输出适合图片模型执行的角色图提示词。
2. 只生成单一主体视频角色锚定图，不生成群像、不生成同款分身、不出现额外人物、文字、水印、logo。
3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保持物种本体，不要改成人类演员、真人脸或人类身体。
4. 构图硬规则：只输出单一镜头里的 3/4 正面或轻微侧正面自然站姿，主体居中，全身完整可见，主体占画面高度 60%-75%。
5. 视频参考硬规则：禁止四方向、三视图、多视图、转面表、分栏、拼图、同款分身、多个角度并排，避免视频模型误识别成多个主体。
6. 全身硬规则：必须完整露出头部/脸部、躯干、四肢/爪子/脚、尾巴或标志性部位；禁止只画头部、禁止半身、禁止身体裁切。
7. 一致性硬规则：突出 2-3 个稳定外观特征，保持同一体型、年龄阶段、物种/品种、毛色/发型、服饰/身体特征、斑纹、光照和比例。
8. 历史版式屏蔽规则：历史输入里的头像、半身、三视图、四方向、正侧背版式只用于识别无效构图，不进入最终构图；最终只允许单主体视频角色锚定图。
9. 直接输出图片提示词，不输出解释。

【当前任务变量】
项目：{{projectName}}
风格：{{style}}
画幅：{{ratio}}
清晰度：{{resolution}}
角色名称：{{characterName}}
性别/物种：{{gender}}
年龄/阶段：{{ageDesc}}
身份定位：{{identityDesc}}
剧情定位：{{storyRole}}
性格标签：{{personalityTags}}
形象描述：{{appearance}}
毛发/发型：{{hairStyle}}
服饰/身体特征：{{costume}}
色彩风格：{{colorStyle}}
净化后的角色外观提示词：{{characterPromptText}}
参考图 URL：{{referenceImageUrl}}
$aivideo_character_image$,
'["projectName","style","ratio","resolution","characterName","gender","ageDesc","identityDesc","storyRole","personalityTags","appearance","hairStyle","costume","colorStyle","characterPromptText","referenceImageUrl"]',
'AI短剧角色图生成默认模板，强制单主体视频角色锚定图和旧词屏蔽', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧角色图生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧分镜视频生成', 'aivideo_video',
$aivideo_shot_video$
# AI短剧单分镜视频生成默认模板

你是电影级短剧分镜视频导演。请基于已经确认的参考图和单条分镜信息，生成适合视频模型的图生视频提示词。

## 核心目标
1. 只生成当前单个镜头，不生成整剧，不跨镜头扩写。
2. 必须严格执行镜头连续性协议：上一分镜确认后，系统会留存尾帧参考图；下一分镜必须从上一分镜尾帧状态起步。
3. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定；若参考图是上一分镜尾帧，主体位置、姿态、朝向、光影要优先继承。
4. 严格执行音画三轨协议：视频阶段只负责画面，不新增、不改写、不替换配音、旁白声线、BGM 或音效；对白才允许口型同步，旁白/画外音可发声但角色不张嘴；心声/心理活动默认不朗读、不口型，只通过眼神、动作和画面隐喻表现。
5. 同一角色、动物或宠物必须保持同一身份与外观锚点，禁止跨镜头换物种、换毛色、换体型、换脸型、换年龄感或丢失项圈/斑纹等标志物。
6. 若角色参考图是纯白/浅灰棚拍的单主体锚定图，只提取角色身份与外观，不继承白底棚拍背景，不复制同款分身，不把单主体误识别成多个主体。
7. 同一场景必须保持背景空间、光线、天气、色调、道具和前中后景关系稳定；除非分镜明确切场，不得无故换地点或换背景。
8. 以“可拍摄、可剪辑、可复现”为优先，动作、运镜、情绪和光影要清晰。
9. 不生成字幕、水印、logo、花字、说明文字或与剧情无关的元素。
10. 遇到“悬浮、飞起、变身、倒地、站起”等强动作词，除非分镜明确高速飞行，否则默认只做缓慢、低幅度、原地附近变化。

## 视频生成要求
- 画幅：{{ratio}}
- 清晰度：{{resolution}}
- 时长：{{durationSec}} 秒
- 参考图：{{referenceImageUrl}}
- 参考图类型：{{referenceFrameType}}
- 项目：{{projectName}}
- 目标平台：{{targetPlatform}}
- 整体风格：{{style}}

## 角色与场景一致性协议（强制）
- 出场角色：{{characterNames}}
- 角色一致性锚点：{{characterContinuity}}
- 场景连续性锚点：{{sceneContinuity}}

执行要求：
1. 同一角色、同一只动物或宠物在分镜之间必须保持外观、物种、毛色、体型、脸型、眼睛、年龄感、服饰/身体特征和标志物一致。
2. 除非分镜明确写“换角色/换动物/换装/变身”，不得把角色替换成其他形象。
3. 角色图只作为外观锚定使用，不把白底/浅灰棚拍背景带入剧情场景，不把单主体锚定图复制成多只同款主体。
4. 同一场景下背景空间、前中后景、光线、天气、色调和道具必须延续；没有明确切场时，不得换地点、换建筑、换环境风格。

## 镜头连续性协议（强制）
- 上一分镜编号：{{previousShotNo}}
- 上一分镜摘要：{{previousShotSummary}}
- 上一分镜结束状态：{{previousEndState}}
- 上一分镜尾帧参考图：{{previousTailFrameUrl}}
- 当前分镜起始状态：{{currentStartState}}
- 当前分镜结束状态：{{currentEndState}}
- 动作边界：{{motionBoundary}}
- 连贯性负面约束：{{continuityNegativePrompt}}

执行要求：
1. 当前镜头第一帧必须贴合“当前分镜起始状态”，不能突然跳到动作中段。
2. 如果存在上一分镜尾帧参考图，必须把它视作当前镜头第一帧构图参考。
3. 如果没有尾帧参考图，也必须按上一分镜结束状态推断衔接，不能无故切换主体位置或姿态。
4. 当前镜头只推进本分镜动作，不提前演到后续镜头。

## 分镜信息
- 集数：{{episodeNo}}
- 镜头号：{{shotNo}}
- 场景名称：{{sceneName}}
- 场景类型：{{sceneType}}
- 时间：{{sceneTime}}
- 天气：{{weather}}
- 氛围：{{atmosphere}}
- 视觉特征：{{visualFeatures}}
- 出场角色：{{characterNames}}
- 景别/镜头类型：{{shotType}}
- 机位：{{cameraPosition}}
- 运镜：{{cameraMovement}}
- 动作：{{actionDesc}}
- 对白：{{dialogue}}
- 旁白：{{voiceOver}}
- 情绪：{{emotion}}
- 原始分镜提示词：{{shotPromptText}}

## 音画/配音协议（强制）
{{audioVisualProtocol}}

执行要求：
1. 视频生成阶段不负责新增或重配声音，不能改变已有配音声线、性别/年龄感、语速、口吻、BGM 或音效。
2. 对白字段存在时，才允许角色张嘴和口型同步；对白为空时，角色不得凭空说话。
3. 旁白/画外音可发声但角色不张嘴、不做口型；心声/心理活动默认不发声、不口型，必须用眼神、呼吸、姿态和环境变化承接情绪。
4. 分镜 1 和分镜 2 必须保持同一旁白/配音口吻连续，不允许声线突变。

## 输出格式
请直接输出一段视频模型可用的中文提示词。结构建议：
1. 参考图保持要求。
2. 镜头起始画面。
3. 角色或环境动作。
4. 运镜方式。
5. 光影、氛围和情绪。
6. 音画约束：不新增/替换/改变配音，旁白不让角色张嘴，对白才做口型。
7. 角色与场景约束：同一角色和同一场景不漂移。
8. 负面约束：无字幕、无水印、无 logo、无花字、无无关文字。
$aivideo_shot_video$,
'["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","characterContinuity","sceneContinuity","audioVisualProtocol","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl","referenceFrameType","previousShotNo","previousShotSummary","previousEndState","previousTailFrameUrl","currentStartState","currentEndState","motionBoundary","continuityNegativePrompt","candidateCount"]',
'AI短剧分镜视频生成默认模板，补充音画三轨、角色一致性和场景连续性强约束', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧分镜视频生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧后期语音合成', 'aivideo_tts',
$aivideo_tts$
# AI短剧后期语音合成默认模板

你是短剧后期配音导演。请基于单条分镜，只整理需要真正朗读的对白和旁白，不处理画面动作、心理活动和脑海闪回。

## 核心规则
1. 只合成说出口的 dialogue，以及明确需要播出的 voiceOver。
2. “心声、内心、脑海里闪过、想到、意识到、心里一动、画面说明、动作描述”默认不朗读。
3. 每句台词必须保留说话角色、情绪、语速、停顿建议和音色类型。
4. 单个分镜音频必须贴合分镜时长，不得超出 {{durationSec}} 秒。
5. 同一角色后续必须使用相同 voiceType 或角色声线参考，避免声线漂移。

## 分镜信息
- 项目：{{projectName}}
- 镜头号：{{shotNo}}
- 角色：{{characterName}}
- 对白：{{dialogue}}
- 旁白/画外音：{{voiceOver}}
- 情绪：{{emotion}}
- 推荐音色：{{voiceType}}
- 镜头时长：{{durationSec}} 秒

## 输出要求
请输出可直接送入 TTS 的文本，不要加入动作说明、括号舞台说明、镜头说明或心理活动。
$aivideo_tts$,
'["projectName","shotNo","characterName","dialogue","voiceOver","emotion","voiceType","durationSec"]',
'AI短剧后期语音合成默认模板，区分说出口台词与心理活动', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧后期语音合成');

-- Keep clean full initialization aligned with 20260601 action-budget upgrade.
UPDATE ai_prompt_template
SET content = $aivideo_script$
# 短剧剧本与镜头拆分规划指令

你是顶级影视剧导演与分镜规划专家。请把润色文案改写为适合 AI 视频生成的短剧剧本，并为后续分镜拆解保留清晰结构。

## 输出要求
1. 按场次组织，每场包含人物、地点、光影、动作、对白、旁白/画外音、心声/心理活动和情绪提示。
2. 对白、旁白/画外音、心声/心理活动必须三轨分清：角色直接说出口的话标注为“角色名说：“台词内容””；可发声但角色不张嘴的旁白/画外音标注为“旁白：内容”或“角色名（画外音）：内容”；心声/心理活动默认不朗读，写成眼神、动作、环境空镜或画面隐喻；低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，禁止写成旁白、画外音或心声。
3. 场景延续时必须注明“延续上个分镜场景，机位微调”。
4. 动作要有衔接，前一镜头结尾姿态必须能触发后一镜头起始动作。
5. 每个场次末尾必须增加【镜头拆分建议】，写清：建议镜头数、每个镜头主动作、是否包含强动作、建议时长 5/6/8 秒、结尾状态。
6. 动作预算：5 秒=1 个主动作 + 1 个反应/表情 + 1 个结尾状态；6 秒=2 个连续动作 + 结尾状态；8 秒=3 个连续动作 + 明确结尾状态。
7. 超过 3 个动作 beat 必须建议拆镜，不要硬塞进一个镜头。
8. 倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等强动作额外占预算，优先单独作为一个镜头核心。
9. 画面不得凭空出现文案外人物、地点或道具。
10. 输出短剧剧本正文，不输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}

润色文本：
{{polishedText}}
$aivideo_script$,
    variables = '["projectName","targetPlatform","ratio","polishedText"]',
    description = 'AI短剧剧本生成默认模板，增加镜头拆分建议和动作预算',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧剧本生成';

UPDATE ai_prompt_template
SET category = 'aivideo_asset',
    content = $aivideo_shot$
# AI短剧分镜提取默认模板

请从短剧剧本中提取【角色、场景、分镜】，必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。
JSON key 必须保持英文，所有字段值必须使用中文。

## 角色构建规则
1. 先解析角色画像：代号、年龄/生命阶段、性别或物种、身份、人格标签、故事功能。
2. 人类角色写清年龄、发色、发型、眼神、服装材质、主色辅色、鞋履配饰。
3. 动物、宠物、怪物、机器人、器物精灵等非人类角色必须保留物种本体，写清品种/体型/毛色/眼睛/标志性特征，禁止改成人类演员。
4. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。
5. promptText 要可直接用于 Seedance 视频角色锚定图生成，必须写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。
6. promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身；动物保持自然四足站立，不拟人化。

## Seedance 视频场景锚点规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。
3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference。
5. 场景 promptText 必须写成单镜头视频首帧/环境锚点：前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。

## 分镜动作预算
1. durationSec 只能输出 5、6、8，不再固定使用项目默认秒数；项目默认镜头秒数仅作为初始参考：{{defaultShotDuration}}。
2. 5 秒镜头：只允许 1 个主动作 + 1 个反应/表情 + 1 个结尾状态。
3. 6 秒镜头：允许 2 个连续动作 + 1 个结尾状态。
4. 8 秒镜头：允许 3 个连续动作 + 1 个明确结尾状态。
5. 超过 3 个动作 beat 必须自动拆成多个 shots，不允许硬塞。
6. 强动作要额外占预算：倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等，优先单独作为一个镜头核心。
7. actionDesc 必须写成视频模型能执行的动作节拍，包含起始状态、主动作、反应/表情和结尾状态。
8. promptText 必须补充构图、目标部位可见和部位发光限制。
9. 出现爪子、手、脚、翅膀、尾巴等部位时，必须要求半身/全身构图并露出目标部位；出现发光时必须写清具体发光部位，禁止用眼睛发光替代目标部位发光。
10. dialogue 只放角色直接说出口的话；voiceOver 只放可发声但角色不张嘴的旁白/画外音，不能把旁白改成对白。心理活动默认不写入 voiceOver；脑海里闪过、想到、意识到、想象、回忆、触感、心里一动等心理内容写入 actionDesc/promptText/emotion 用画面表现。低声报数、低声说、耳语、小声说、念出、读出都属于对白，必须写入 dialogue。
11. 剧情空间连续性是硬约束：后一分镜必须承接前一分镜的主体位置、危险目标、空间关系和结尾状态，不能只因情绪需要突然换地点。
12. 如果上一分镜建立了屋顶、广告牌、铁架、高处、水中、火场、车道等危险目标，下一分镜必须继续该目标、让主角观察/靠近/救援该目标，或在 actionDesc 开头写明过渡动作。
13. 未经剧本铺垫，禁止突然切到狗窝、室内、家里、床下、窝口等新地点；必须先用过渡镜头建立空间关系，或改写为“延续上一镜，镜头回到街边/同一条街道”。
14. 错误示例：上一镜“广告牌铁架上有小身影”，下一镜“狗狗蜷缩在窝的角落”。正确示例：下一镜“延续上一镜，狗狗在街边抬头望向广告牌铁架，身体绷紧准备冲向商铺雨棚”。

## 输出 JSON 结构
{
  "characters": [{"characterName":"","gender":"","ageDesc":"","identityDesc":"","personalityTags":[""],"storyRole":"","relationshipDesc":"","appearance":"","hairStyle":"","costume":"","colorStyle":"","negativeTraits":"","promptText":"","completeness":"","missingFields":[""]}],
  "scenes": [{"sceneName":"","sceneType":"","episodeNo":1,"timeDesc":"","weather":"","atmosphere":"","visualFeatures":"","colorTone":"","props":"","negativeElements":"","promptText":"","completeness":"","missingFields":[""]}],
  "shots": [{"episodeNo":1,"shotNo":1,"durationSec":5,"sceneName":"","characterNames":[""],"shotType":"","cameraPosition":"","cameraMovement":"","actionDesc":"","dialogue":"","voiceOver":"","emotion":"","promptText":""}]
}

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
剧本：
{{scriptText}}
$aivideo_shot$,
    variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]',
    description = 'AI短剧分镜提取默认模板，增加动作预算、动态时长和部位锁定',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

UPDATE ai_prompt_template
SET category = 'aivideo_video',
    content = $aivideo_shot_video$
# 单分镜视频模型执行版 Prompt

基于参考图生成 1 个连续短剧镜头，不要生成多镜头拼接。
参考图类型：{{referenceFrameType}}。参考图地址：{{referenceImageUrl}}。
输出规格：{{ratio}}，{{resolution}}，约 {{durationSec}} 秒。

## 第一帧和连续性
- 第一帧必须贴合参考图：主体位置、姿态、朝向、体型、毛色/服饰、光影、天气和背景空间保持一致。
- 上一镜头：{{previousShotNo}}，{{previousShotSummary}}。
- 上一镜头结束状态：{{previousEndState}}
- 本镜头起始状态：{{currentStartState}}
- 本镜头结尾状态：{{currentEndState}}
- 角色锚定图使用规则：角色图只用于锁定身份、体型、毛色/服饰和标志物；不得把白底/浅灰棚拍背景带入剧情场景，不得把单主体锚定图复制成多只同款主体。

## 主体、场景、构图
- 项目/风格：{{projectName}} / {{style}}
- 场景：{{sceneName}}，{{sceneTime}}，{{weather}}，{{atmosphere}}，视觉特征：{{visualFeatures}}
- 出场主体：{{characterNames}}
- 角色一致性：{{characterContinuity}}
- 场景一致性：{{sceneContinuity}}
- 构图要求：{{compositionRequirement}}
- 部位可见要求：{{bodyPartRequirement}}
- 发光部位要求：{{glowRequirement}}

## 动作节拍
{{actionBeats}}

## 执行顺序
{{timingPlan}}

## 镜头语言
- 景别/机位/运镜：{{shotType}} / {{cameraPosition}} / {{cameraMovement}}
- 同一镜头内只保留 1 种主要运镜，运动稳定、低幅度、可剪辑。
- 动作边界：{{motionBoundary}}

## 音画规则
{{audioVisualProtocol}}
视频生成阶段只负责画面，不生成、不替换、不改变配音、旁白声线、BGM 或音效；对白为空时主体不张嘴；心声/心理活动默认不朗读、不口型。

## 负面约束
{{continuityNegativePrompt}}
禁止字幕、水印、logo、花字、无关文字；禁止换角色、换物种、换毛色、换体型、换背景；禁止用眼睛发光替代指定部位发光。
$aivideo_shot_video$,
    variables = '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","characterContinuity","sceneContinuity","audioVisualProtocol","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl","referenceFrameType","previousShotNo","previousShotSummary","previousEndState","previousTailFrameUrl","currentStartState","currentEndState","motionBoundary","continuityNegativePrompt","actionBeats","timingPlan","compositionRequirement","bodyPartRequirement","glowRequirement","candidateCount"]',
    description = 'AI短剧分镜视频生成执行模板，增加动作节拍、构图部位锁定和禁用自动配音',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成';

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色构建') AS character_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景设计') AS scene_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧分镜提取') AS shot_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色图生成') AS character_image_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景图生成') AS scene_image_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧分镜视频生成') AS video_prompt_id
    FROM ai_prompt_template
    WHERE template_name IN ('AI短剧原文润色', 'AI短剧剧本生成', 'AI短剧角色构建', 'AI短剧场景设计',
                            'AI短剧分镜提取', 'AI短剧角色图生成', 'AI短剧场景图生成', 'AI短剧分镜视频生成')
)
INSERT INTO ai_video_project_setting (
    project_id, tenant_id, polish_prompt_template_id, script_prompt_template_id,
    character_prompt_template_id, scene_prompt_template_id, character_image_prompt_template_id,
    scene_image_prompt_template_id, shot_prompt_template_id, video_prompt_template_id,
    default_ratio, default_resolution, default_shot_duration,
    image_candidate_count, video_candidate_count, preview_mode, content_audit_enabled, media_access_policy,
    create_by, create_time, update_by, update_time
)
SELECT NULL, 0, tpl.polish_id, tpl.script_id,
       tpl.character_id, tpl.scene_id, tpl.character_image_id, tpl.scene_image_id, tpl.shot_id, tpl.video_prompt_id,
       '9:16', '720p', 5, 2, 1, '1', '1', 'PRIVATE',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.polish_id IS NOT NULL
  AND tpl.script_id IS NOT NULL
  AND tpl.character_id IS NOT NULL
  AND tpl.scene_id IS NOT NULL
  AND tpl.character_image_id IS NOT NULL
  AND tpl.scene_image_id IS NOT NULL
  AND tpl.shot_id IS NOT NULL
  AND tpl.video_prompt_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ai_video_project_setting s
      WHERE s.project_id IS NULL AND COALESCE(s.tenant_id, 0) = 0
  );


-- ============================================================
-- source: postgres\gen\00-schema.sql
-- ============================================================
-- PostgreSQL migration for code generator metadata tables.

CREATE TABLE IF NOT EXISTS gen_table (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    table_name VARCHAR(200) NOT NULL DEFAULT '',
    table_comment VARCHAR(500) DEFAULT '',
    package_name VARCHAR(200) DEFAULT 'com.han.system',
    module_name VARCHAR(50) DEFAULT '',
    business_name VARCHAR(50) DEFAULT '',
    function_name VARCHAR(50) DEFAULT '',
    author VARCHAR(50) DEFAULT 'HanCloud',
    parent_menu_id BIGINT DEFAULT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL
);

COMMENT ON TABLE gen_table IS 'Code generator table metadata';
COMMENT ON COLUMN gen_table.table_name IS 'Database table name';
COMMENT ON COLUMN gen_table.table_comment IS 'Database table comment';
COMMENT ON COLUMN gen_table.package_name IS 'Generated package name';
COMMENT ON COLUMN gen_table.module_name IS 'Generated module name';
COMMENT ON COLUMN gen_table.business_name IS 'Generated business name';
COMMENT ON COLUMN gen_table.function_name IS 'Generated function label';
COMMENT ON COLUMN gen_table.author IS 'Generated author';
COMMENT ON COLUMN gen_table.parent_menu_id IS 'Parent menu ID';

CREATE TABLE IF NOT EXISTS gen_table_column (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    table_id BIGINT NOT NULL,
    column_name VARCHAR(200) NOT NULL,
    column_comment VARCHAR(500) DEFAULT '',
    column_type VARCHAR(100) DEFAULT '',
    java_type VARCHAR(50) DEFAULT 'String',
    java_field VARCHAR(200) DEFAULT '',
    is_pk SMALLINT DEFAULT 0,
    is_increment SMALLINT DEFAULT 0,
    is_required SMALLINT DEFAULT 0,
    is_insert SMALLINT DEFAULT 1,
    is_edit SMALLINT DEFAULT 1,
    is_list SMALLINT DEFAULT 1,
    is_query SMALLINT DEFAULT 0,
    query_type VARCHAR(20) DEFAULT 'EQ',
    html_type VARCHAR(50) DEFAULT 'input',
    dict_type VARCHAR(200) DEFAULT '',
    sort INT DEFAULT 0
);

COMMENT ON TABLE gen_table_column IS 'Code generator column metadata';
COMMENT ON COLUMN gen_table_column.table_id IS 'Related table ID';
COMMENT ON COLUMN gen_table_column.column_name IS 'Database column name';
COMMENT ON COLUMN gen_table_column.column_comment IS 'Database column comment';
COMMENT ON COLUMN gen_table_column.column_type IS 'Database column type';
COMMENT ON COLUMN gen_table_column.java_type IS 'Generated Java type';
COMMENT ON COLUMN gen_table_column.java_field IS 'Generated Java field';
COMMENT ON COLUMN gen_table_column.is_pk IS 'Whether the column is a primary key';
COMMENT ON COLUMN gen_table_column.is_increment IS 'Whether the column is auto increment';
COMMENT ON COLUMN gen_table_column.is_required IS 'Whether the column is required';
COMMENT ON COLUMN gen_table_column.is_insert IS 'Whether the column is included on insert';
COMMENT ON COLUMN gen_table_column.is_edit IS 'Whether the column is included on edit';
COMMENT ON COLUMN gen_table_column.is_list IS 'Whether the column is included on list view';
COMMENT ON COLUMN gen_table_column.is_query IS 'Whether the column is queryable';
COMMENT ON COLUMN gen_table_column.query_type IS 'Query operator';
COMMENT ON COLUMN gen_table_column.html_type IS 'Rendered form control';
COMMENT ON COLUMN gen_table_column.dict_type IS 'Bound dictionary type';
COMMENT ON COLUMN gen_table_column.sort IS 'Display order';

CREATE INDEX IF NOT EXISTS idx_gen_table_column_table_id ON gen_table_column (table_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_gen_table_column_table_id'
    ) THEN
        ALTER TABLE gen_table_column
            ADD CONSTRAINT fk_gen_table_column_table_id
                FOREIGN KEY (table_id) REFERENCES gen_table(id) ON DELETE CASCADE;
    END IF;
END $$;


-- ============================================================
-- source: postgres\gen\10-seed.sql
-- ============================================================
-- ============================================================
-- source: postgres\aivideo\20260609-prompt-template-alignment.sql
-- ============================================================
-- AI短剧 Prompt 模板最终对齐，保持 clean full-init 与升级脚本一致。
UPDATE ai_prompt_template
SET category = CASE template_name
        WHEN 'AI短剧原文润色' THEN 'aivideo_text'
        WHEN 'AI短剧剧本生成' THEN 'aivideo_script'
        WHEN 'AI短剧资产提取' THEN 'aivideo_asset'
        WHEN 'AI短剧角色构建' THEN 'aivideo_asset'
        WHEN 'AI短剧场景设计' THEN 'aivideo_asset'
        WHEN 'AI短剧分镜提取' THEN 'aivideo_storyboard'
        WHEN 'AI短剧角色图生成' THEN 'aivideo_image'
        WHEN 'AI短剧场景图生成' THEN 'aivideo_image'
        WHEN 'AI短剧分镜视频生成' THEN 'aivideo_video'
        WHEN 'AI短剧后期语音合成' THEN 'aivideo_tts'
        ELSE category
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name IN (
    'AI短剧原文润色',
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧角色构建',
    'AI短剧场景设计',
    'AI短剧分镜提取',
    'AI短剧角色图生成',
    'AI短剧场景图生成',
    'AI短剧分镜视频生成',
    'AI短剧后期语音合成'
);

WITH hard_rules AS (
    SELECT $rules$

【20260609模板对齐硬规则】
1. 道具交接硬锁：出现“递给/接过/展示给/交给/传给/拿给/递来/滚入/飘来/飞来/滑来”时，必须写清谁递给谁、什么道具、从画面哪边来、最后谁拿着；禁止“试卷飘过来”“接过某物”“展示给画外”这类无来源动作。
2. 道具连续硬锁：关键道具必须写清道具颜色、材质、形状、尺寸、归属角色和镜头结束位置；下一镜继承同一颜色和归属。
3. 方位/站位硬锁：上一镜背对、侧身、左/右站位、画内人数和视线方向必须继承；若下一镜要正面对话，必须写明转身、反打、换轴、重新建立站位或切场，否则视为不合格。
4. 在场角色硬锁：同一场景/同一 stitchGroupNo 下，上一镜在画内的角色默认仍在当前镜，除非明确离场、画外、裁切、单人反应或插入镜头；不得无说明消失。
5. 镜头衔接硬锁：CONTINUE 强制继承上一尾帧；SCENE_CUT/TIME_JUMP/MONTAGE 可新建场景；INSERT 是插入镜头/交接镜头，不强制继承上一尾帧，但必须交代和上一镜的动作关系。
6. 素材策略硬锁：连续镜头如果使用上一尾帧，就不要混入角色图/场景图/参考音频；插入镜头/交接镜头可使用上一段视频、角色图、场景图、道具图和角色参考音频。
7. 多角色声音硬锁：长期角色声线应使用 referenceAudioUrls，最多 3 段，单段 2-15 秒，总时长不超过 15 秒；超过 3 个发声角色时必须拆镜或改后期 TTS。
8. 三轨声音硬锁：说出口的写 dialogue；旁白/画外音写 voiceOver；“脑海里闪过、想到、意识到、心里一动”等心理活动默认不朗读，只写 actionDesc/promptText/emotion。
9. 声音设计资产硬锁：剧本生成必须增加“声音设计”小节，资产提取必须输出顶层 soundDesign，包含 voiceProfiles、narrationProfile、bgmPlan、sfxPlan。
10. 音乐音效硬锁：每个分镜必须输出 bgmCue 和 sfxCues；bgmCue 写当前镜头继承/切换/静音的背景音乐意图，sfxCues 写与动作绑定的音效名称、触发点和音量倾向。
11. 后期轨道硬锁：剧本阶段只定义声音意图，后期语音只合成 dialogue 和明确需要播出的 voiceOver；BGM 与音效进入音乐音效/混音成片轨道，不得让分镜视频模型自行改写声音。
$rules$ AS block
)
UPDATE ai_prompt_template t
SET content = CASE
        WHEN t.content IS NULL OR btrim(t.content) = '' THEN hard_rules.block
        ELSE rtrim(t.content) || hard_rules.block
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM hard_rules
WHERE t.template_name IN (
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧角色构建',
    'AI短剧场景设计',
    'AI短剧分镜提取',
    'AI短剧角色图生成',
    'AI短剧场景图生成',
    'AI短剧分镜视频生成',
    'AI短剧后期语音合成'
)
AND COALESCE(t.content, '') NOT LIKE '%【20260609模板对齐硬规则】%';

UPDATE ai_prompt_template
SET variables = '["projectName","style","ratio","resolution","durationSec","shotNo","cameraMove","actionDesc","dialogue","voiceOver","innerThought","emotion","bgmCue","sfxCues","characterAnchors","sceneAnchor","propAnchors","continuityRequirement","referenceAudioUrls","referenceVideoUrl"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成';

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText","soundDesign","bgmPlan","sfxPlan"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText","soundDesign","bgmPlan","sfxPlan"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取';

UPDATE ai_prompt_template
SET variables = '["projectName","shotNo","characterName","dialogue","voiceOver","emotion","voiceType","durationSec"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧后期语音合成';

DO $$
DECLARE
    v_ai_root_id BIGINT;
    v_prompt_menu_id BIGINT;
    v_next_id BIGINT;
    v_action RECORD;
BEGIN
    SELECT id
    INTO v_ai_root_id
    FROM sys_menu
    WHERE (path = 'ai' AND menu_type = 'M') OR menu_name = 'AI智能'
    ORDER BY CASE WHEN id = 500 THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_ai_root_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 500) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_ai_root_id FROM sys_menu;
        ELSE
            v_ai_root_id := 500;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
            query, menu_type, visible, status, perms, icon, is_frame, is_cache
        )
        VALUES (
            v_ai_root_id, NULL, 'AI智能', 0, '0', 5, 'ai', NULL,
            NULL, 'M', 0, 0, NULL, 'magic-stick', 1, 0
        );
    END IF;

    SELECT id
    INTO v_prompt_menu_id
    FROM sys_menu
    WHERE perms = 'ai:prompt:list'
       OR (path = 'prompt' AND component = 'ai/prompt/index')
    ORDER BY CASE WHEN id = 515 THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_prompt_menu_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 515) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_prompt_menu_id FROM sys_menu;
        ELSE
            v_prompt_menu_id := 515;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
            query, menu_type, visible, status, perms, icon, is_frame, is_cache
        )
        VALUES (
            v_prompt_menu_id, NULL, 'Prompt模板', v_ai_root_id, '0,' || v_ai_root_id,
            6, 'prompt', 'ai/prompt/index', NULL, 'C', 0, 0,
            'ai:prompt:list', 'document', 1, 0
        );
    ELSE
        UPDATE sys_menu
        SET menu_name = 'Prompt模板',
            parent_id = v_ai_root_id,
            ancestors = '0,' || v_ai_root_id,
            sort = 6,
            path = 'prompt',
            component = 'ai/prompt/index',
            menu_type = 'C',
            visible = 0,
            status = 0,
            perms = 'ai:prompt:list',
            icon = 'document',
            is_frame = 1,
            is_cache = 0
        WHERE id = v_prompt_menu_id;
    END IF;

    FOR v_action IN
        SELECT * FROM (
            VALUES
                ('Prompt模板查询', 'ai:prompt:query', 1),
                ('Prompt模板新增', 'ai:prompt:add', 2),
                ('Prompt模板编辑', 'ai:prompt:edit', 3),
                ('Prompt模板删除', 'ai:prompt:remove', 4)
        ) AS action(menu_name, perms, sort_no)
    LOOP
        IF EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_action.perms) THEN
            UPDATE sys_menu
            SET menu_name = v_action.menu_name,
                parent_id = v_prompt_menu_id,
                ancestors = '0,' || v_ai_root_id || ',' || v_prompt_menu_id,
                sort = v_action.sort_no,
                path = '',
                component = NULL,
                query = NULL,
                menu_type = 'F',
                visible = 0,
                status = 0,
                icon = '#',
                is_frame = 1,
                is_cache = 0
            WHERE perms = v_action.perms;
        ELSE
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

            INSERT INTO sys_menu (
                id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
                query, menu_type, visible, status, perms, icon, is_frame, is_cache
            )
            VALUES (
                v_next_id, NULL, v_action.menu_name, v_prompt_menu_id,
                '0,' || v_ai_root_id || ',' || v_prompt_menu_id,
                v_action.sort_no, '', NULL, NULL, 'F', 0, 0,
                v_action.perms, '#', 1, 0
            );
        END IF;
    END LOOP;

    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT role.id, menu.id
    FROM sys_role role
    CROSS JOIN sys_menu menu
    WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
      AND (
          menu.id IN (v_ai_root_id, v_prompt_menu_id)
          OR menu.perms IN ('ai:prompt:list', 'ai:prompt:query', 'ai:prompt:add', 'ai:prompt:edit', 'ai:prompt:remove')
      )
    ON CONFLICT DO NOTHING;
END $$;

-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


