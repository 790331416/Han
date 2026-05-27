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
(11, '租户隔离类型', 'sys_isolation_type', 0, '租户隔离类型列表');

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
(39, 'sys_isolation_type', '混合隔离', 'hybrid', 3, '', 'info', 0, 0);

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
    action_desc TEXT,
    dialogue TEXT,
    voice_over TEXT,
    emotion VARCHAR(200),
    prompt_text TEXT,
    reference_media_ids TEXT,
    keyframe_media_id BIGINT,
    video_media_id BIGINT,
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
    variables     VARCHAR(500),
    description   VARCHAR(500),
    built_in      INT             NOT NULL DEFAULT 0,
    status        CHAR(1)         NOT NULL DEFAULT '0',
    create_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
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
('Ollama 本地模型', 'LLM', 'ollama', 'llama3', 'http://localhost:11434/v1', '', 4096, 0.70, '1', 'Ollama本地部署模型');

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
1. 按场次组织，包含人物、场景、动作、对白、旁白和情绪提示。
2. 每个场次都要具备可拍摄的空间、行为和冲突推进。
3. 镜头描述要能继续拆分为分镜，避免空泛形容。
4. 输出短剧剧本正文，不要输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
润色文本：
{{polishedText}}$$,
'["projectName","targetPlatform","ratio","polishedText"]', 'AI短剧剧本生成默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧剧本生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧资产提取', 'aivideo_text',
$$请从短剧剧本中提取人物、场景、分镜，必须只输出 JSON 对象，不要输出解释。
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
'["projectName","scriptText"]', 'AI短剧人物场景分镜提取默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧资产提取');

-- 17.2 AI短剧 Prompt 模板长提示词与全局默认配置
UPDATE ai_prompt_template
SET content = $aivideo_polish$
# 顶级小说推文改文指令（纯文案版）

你是一个顶级的小说推文改文专家。你的唯一任务是将小说原文改写为适合短视频配音的“第一人称解说文案”。要求全程紧扣完播率、转粉率两大核心，生成的文案需自带情绪、自带画面、自带悬念，可直接用于抖音/快手等短视频平台的小说推文配音与制作。

## 爆款开头重构
1. 严禁套用原文第一句，必须从原文核心冲突中重构开头。
2. 开头单段 30-50 字，严禁超过 55 字。
3. 1 秒抓住注意力，3 秒内抛出核心冲突或悬念。
4. 爽文/反套路文优先使用经典反转钩、极致反差钩、身份错位钩；悬疑/灵异文优先使用猎奇悬疑钩、夸张反常钩、结局先行钩；虐恋/情感文优先使用人性抉择钩、荒诞现实钩。

## 转换密度与节奏
1. 每 1000 字原文重构 38-40 段，误差不超过 5%。
2. 单段字数不超过 50 字，严禁出现 60 字以上长句。
3. 高潮、反转、情绪爆发段可以拆成 2 段；过渡段压缩至 15-20 字。

## 角色、台词与视角
1. 主角优先改为第一人称“我”，配角使用全名或昵称，避免他/她混淆。
2. 台词格式必须为：角色名说（情绪提示）：“台词内容”。
3. 情绪提示必须具体、具象，例如轻蔑地嗤笑、颤抖着嘶吼、温柔低吟、冷漠冰冷。

## 五感与画面落地
1. 先感知，后行动：人物动作、对话、内心活动前，优先加入视觉、听觉、触觉或嗅觉触发。
2. 每 3 段至少出现 1 种五感细节。
3. 每段至少包含 1 个可具象化的画面或动作细节，适配视频剪辑。

## 禁忌
1. 不要输出分析过程、解释、BGM、音效、剪辑说明或视频制作备注。
2. 不得输出“以下是改写”之类的开场说明，直接输出正文。
3. 不得混用视角，不得重复关联词，不得堆砌无意义语气词。

## 输出格式
1. 首段必须是重构后的爆款开头，单独成行。
2. 段与段之间空一行。
3. 台词按“角色名说（情绪提示）：“台词内容””格式单独成行。
4. 最后一段必须抛出追剧悬念，引导继续观看，但不要写生硬营销口号。

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
# AI短剧人物 / 场景 / 分镜提取指令

请严格依据角色构建、电影级纯净场景、剧本分镜三组参考提示词规则，从短剧剧本中提取人物、场景、分镜。
必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。
JSON key 必须保持英文，所有字段值必须使用中文。

## 角色构建规则
1. 你是电影级角色概念设计师，需要先解析角色心理画像、社会身份、人格标签和故事功能。
2. 每个角色必须输出鲜明、可区分的视觉方案，包含年龄、发型、眼神、服装材质、主色辅色和配饰。
3. promptText 要可直接用于角色图生成，包含横向 16:9、纯白极简背景、面部特写、全身正侧背三视图、固定自然站姿等关键信息。

## 电影级纯净场景规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，并覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
3. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。

## 剧本分镜规则
1. 面向 Seedance 2.0 / 即梦 2.0 的视频生成逻辑拆解镜头。
2. 全局禁止出现其他人；画面必须通过单人特写、主观视角或环境遮挡，把视觉重心锁定在当前核心主角。
3. 严格区分 dialogue 和 voiceOver；角色直接说的话写入 dialogue，旁白、心理活动、环境氛围写入 voiceOver。
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
    description = 'AI短剧人物场景分镜提取默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取';

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧场景图生成', 'aivideo_image',
$$# AI短剧电影级纯净场景图生成

请基于以下场景信息生成一张短剧可用的电影级纯净场景图。

## 强制规则
1. 纯场景、无人、无人物、无人物剪影、无脸、无身体部位，不出现任何角色。
2. 画面必须可作为后续分镜视频背景，空间关系清晰，主体环境明确。
3. 保留场景气氛、时间、天气、色调、道具和视觉特征。
4. 必须包含 no humans, empty scene, landscape only。
5. 不输出解释，只输出可直接用于图片模型的中文提示词。

项目：{{projectName}}
目标平台：{{targetPlatform}}
风格：{{style}}
画幅：{{ratio}}
清晰度：{{resolution}}

场景名称：{{sceneName}}
场景类型：{{sceneType}}
时间：{{timeDesc}}
天气：{{weather}}
氛围：{{atmosphere}}
视觉特征：{{visualFeatures}}
色调：{{colorTone}}
道具：{{props}}
禁用元素：{{negativeElements}}
原始场景提示词：{{scenePromptText}}$$,
'["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
'AI短剧场景图生成默认模板，强制纯场景无人，服务于 MVP2 单场景候选图', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景图生成');

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧资产提取') AS asset_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景图生成') AS scene_image_id
    FROM ai_prompt_template
    WHERE template_name IN ('AI短剧原文润色', 'AI短剧剧本生成', 'AI短剧资产提取', 'AI短剧场景图生成')
)
INSERT INTO ai_video_project_setting (
    project_id, tenant_id, polish_prompt_template_id, script_prompt_template_id,
    character_prompt_template_id, scene_prompt_template_id, scene_image_prompt_template_id, shot_prompt_template_id,
    default_ratio, default_resolution, default_shot_duration,
    image_candidate_count, video_candidate_count, preview_mode, content_audit_enabled, media_access_policy,
    create_by, create_time, update_by, update_time
)
SELECT NULL, 0, tpl.polish_id, tpl.script_id,
       tpl.asset_id, tpl.asset_id, tpl.scene_image_id, tpl.asset_id,
       '9:16', '720p', 5, 2, 1, '1', '1', 'PRIVATE',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.polish_id IS NOT NULL
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
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


