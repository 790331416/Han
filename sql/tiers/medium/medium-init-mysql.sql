-- Han medium 档 MySQL 8.4 全新初始化脚本
-- PostgreSQL 仍为默认数据库；本文件是 medium 档的正式 MySQL 入口。

-- 用途：medium 档 MySQL 8.4 全新初始化脚本，覆盖 system、job、tenant、workflow、
-- open、file 六个模块，是 sql/tiers/medium/medium-init.sql（PostgreSQL）的等价版本。
-- 适用范围：只用于空库首次初始化，不承担存量库升级；MySQL 增量升级目录尚未建立。
-- 转换约定沿用 sql/tiers/small/small-init-mysql.sql：
--   1. 只改 MySQL 不接受的写法，其余类型、字段顺序、默认值、注释与 PostgreSQL 版逐行对齐；
--   2. PostgreSQL 自增主键列改写为 BIGINT AUTO_INCREMENT；
--   3. 表达式唯一索引改为 STORED 生成列 + 普通唯一索引；
--   4. PostgreSQL 的 (VALUES ...) 派生表种子改写为普通 VALUES 批量插入，租户列取值保持一致。
-- 与 PostgreSQL 版不能等价转换的点，均在对应位置就地标注。

SET NAMES utf8mb4;

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
    remark          VARCHAR(500)
);

-- =============================================
-- 4.1 社交及外部身份绑定表
-- =============================================
-- PostgreSQL 版把本表建了两次（第 4.1 节和第 21 节），第二次在 PostgreSQL 里只是报错跳过；
-- MySQL 遇到重复建表会直接中断导入，所以这里合并成唯一一份定义，
-- 并把第 21 节额外补充的 idx_user_social_provider_openid 一并纳入。
-- tenant_scope 是为唯一索引准备的生成列：MySQL 无法直接对 COALESCE(tenant_id, 0)
-- 建唯一索引，用 STORED 生成列把空租户归一到 0，等价于 PostgreSQL 的表达式唯一索引。
CREATE TABLE sys_user_social (
    id              BIGINT          NOT NULL PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    tenant_id       BIGINT,
    tenant_scope    BIGINT          GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    provider        VARCHAR(32)     NOT NULL,
    open_id         VARCHAR(128)    NOT NULL,
    access_token    VARCHAR(512),
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    extra           TEXT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_social_user_id (user_id),
    INDEX idx_user_social_provider_openid (provider, open_id),
    UNIQUE KEY uq_user_social_tenant_provider_openid (tenant_scope, provider, open_id),
    UNIQUE KEY uq_user_social_user_provider (user_id, provider)
);


-- =============================================
-- 5. 岗位表
-- =============================================
CREATE TABLE sys_post (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    post_code       VARCHAR(50)     NOT NULL,
    post_name       VARCHAR(100)    NOT NULL,
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
-- tenant_scope 同 sys_user_social：为第 22 节的租户维度唯一索引提供归一后的租户列。
CREATE TABLE sys_dict_type (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT,
    tenant_scope    BIGINT          GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
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
    tenant_scope    BIGINT          GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
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
    tenant_scope    BIGINT          GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
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
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    module          VARCHAR(100)    DEFAULT '',
    oper_type       SMALLINT        DEFAULT 0,
    request_method  VARCHAR(10)     DEFAULT '',
    oper_name       VARCHAR(50)     DEFAULT '',
    oper_user_id    BIGINT,
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
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
    client_key      VARCHAR(50)     NOT NULL,
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

-- =============================================
-- 21. 社交登录绑定表
-- =============================================
-- 本节在 PostgreSQL 版是 sys_user_social 的第二份重复定义，结构与第 4.1 节完全相同，
-- 只多出 idx_user_social_provider_openid 一个索引。MySQL 不允许重复建表，
-- 该表与四个索引已在第 4.1 节一次性建好，这里不再重复声明。

-- =============================================
-- 22. 唯一约束（与逻辑删除兼容的部分唯一索引）
-- =============================================
-- PostgreSQL 版这八个唯一索引都带 WHERE del_flag = 0。MySQL 8.4 确实没有部分索引，
-- 但有函数式键部件（8.0.13 起支持）：追加一列 (IF(del_flag = 0, 0, NULL))，
-- 未删除行取 0 参与唯一性判定，已删除行取 NULL —— NULL 在唯一索引里互不冲突，
-- 于是「软删后可以重建同名对象」这个语义被完整复现。写法与 small-init-mysql.sql 一致。
-- 与 PostgreSQL 的差异只剩索引体积：MySQL 仍然收录已删除行，只是键值判为 NULL。
-- 索引名与列顺序保持与 PostgreSQL 版一致，避免两边结构对照时被认成不同索引。
-- COALESCE(tenant_id, 0) 一律改走各表的 tenant_scope 生成列。
CREATE UNIQUE INDEX sys_user_username_tenant_uniq
    ON sys_user (username, tenant_id, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_role_key_tenant
    ON sys_role (tenant_id, role_key, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_role_name_tenant
    ON sys_role (tenant_id, role_name, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_post_code_tenant
    ON sys_post (tenant_id, post_code, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_dict_type_tenant
    ON sys_dict_type (tenant_scope, dict_type, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_dict_data_tenant
    ON sys_dict_data (tenant_scope, dict_type, dict_value, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_config_key_tenant
    ON sys_config (tenant_scope, config_key, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_client_key
    ON sys_client (client_key, (IF(del_flag = 0, 0, NULL)));


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
INSERT INTO sys_post (id, tenant_id, post_code, post_name, post_sort, status) VALUES
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
(4, 0, '0', '租户管理', 'M', 'tenant', NULL, NULL, 'peoples', 4, 0, 0),
(5, 0, '0', '任务调度', 'M', 'job', NULL, NULL, 'timer', 5, 0, 0),
(6, 0, '0', '工作流', 'M', 'workflow', NULL, NULL, 'connection', 6, 0, 0),
(7, 0, '0', '开放平台', 'M', 'open', NULL, NULL, 'platform', 7, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(100, 1, '0,1', '用户管理', 'C', 'user', 'system/user/index', 'system:user:list', 'user', 1, 0, 0),
(101, 1, '0,1', '角色管理', 'C', 'role', 'system/role/index', 'system:role:list', 'peoples', 2, 0, 0),
(102, 1, '0,1', '菜单管理', 'C', 'menu', 'system/menu/index', 'system:menu:list', 'tree-table', 3, 0, 0),
(103, 1, '0,1', '部门管理', 'C', 'dept', 'system/dept/index', 'system:dept:list', 'tree', 4, 0, 0),
(104, 1, '0,1', '岗位管理', 'C', 'post', 'system/post/index', 'system:post:list', 'post', 5, 0, 0),
(105, 1, '0,1', '字典管理', 'C', 'dict', 'system/dict/index', 'system:dict:list', 'dict', 6, 0, 0),
(106, 1, '0,1', '参数设置', 'C', 'config', 'system/config/index', 'system:config:list', 'edit', 7, 0, 0),
(107, 1, '0,1', '通知公告', 'C', 'notice', 'system/notice/index', 'system:notice:list', 'message', 8, 0, 0),
(109, 1, '0,1', '文件管理', 'C', 'file', 'system/file/index', 'file:list', 'upload', 10, 0, 0),
(110, 1, '0,1', 'OSS配置', 'C', 'oss-config', 'system/oss-config/index', 'system:oss:list', 'upload', 11, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(200, 2, '0,2', '在线用户', 'C', 'online', 'monitor/online/index', 'monitor:online:list', 'online', 1, 0, 0),
(201, 2, '0,2', '操作日志', 'C', 'operlog', 'monitor/operlog/index', 'monitor:operlog:list', 'form', 2, 0, 0),
(202, 2, '0,2', '登录日志', 'C', 'loginlog', 'monitor/loginlog/index', 'monitor:loginlog:list', 'logininfor', 3, 0, 0),
(203, 2, '0,2', '缓存监控', 'C', 'cache', 'monitor/cache/index', 'monitor:cache:list', 'redis', 4, 0, 0),
(204, 2, '0,2', '服务监控', 'C', 'server', 'monitor/server/index', 'monitor:server:list', 'server', 5, 0, 0);


INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(400, 4, '0,4', '租户列表', 'C', 'list', 'tenant/list/index', 'tenant:list', 'list', 1, 0, 0),
(401, 4, '0,4', '套餐管理', 'C', 'package', 'tenant/package/index', 'tenant:package:list', 'component', 2, 0, 0),
(402, 4, '0,4', '资源配额', 'C', 'quota', 'tenant/quota/index', 'tenant:quota:query', 'pie-chart', 3, 0, 0);

-- 7.1 任务调度 / 工作流 / 开放平台菜单
-- 菜单 ID 与 sql/upgrades/postgres/phase9_base_menu_backfill.sql 保持同一套编号，
-- 避免旧库回放 phase9 时重复插入或错挂父节点。
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(210, 5, '0,5', '定时任务', 'C', 'list', 'job/index', 'job:list', 'clock', 1, 0, 0),
(211, 5, '0,5', '调度日志', 'C', 'log', 'job/log', 'job:log:list', 'document', 2, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(310, 6, '0,6', '流程定义', 'C', 'definition', 'workflow/definition/index', 'workflow:definition:list', 'document', 1, 0, 0),
(311, 6, '0,6', '流程实例', 'C', 'instance', 'workflow/instance/index', 'workflow:instance:list', 'histogram', 2, 0, 0),
(312, 6, '0,6', '待办任务', 'C', 'todo', 'workflow/task/index', 'workflow:task:todo', 'bell', 3, 0, 0),
(313, 6, '0,6', '已办任务', 'C', 'done', 'workflow/task/done', 'workflow:task:done', 'finished', 4, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(410, 7, '0,7', '应用管理', 'C', 'app', 'open/app/index', 'open:app:list', 'grid', 1, 0, 0);

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
(1074, 107, '0,1,107', '公告删除', 'F', '', NULL, 'system:notice:remove', '#', 4, 0, 0),
(1091, 109, '0,1,109', '文件查询', 'F', '', NULL, 'file:query', '#', 1, 0, 0),
(1092, 109, '0,1,109', '文件删除', 'F', '', NULL, 'file:remove', '#', 2, 0, 0);

-- 按钮权限（1100 段）：与后端 @PreAuthorize 声明的权限串一一对应
-- 权限串以后端注解为准；1100 段之前的历史按钮权限保持原有编号不动。
INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(1101, 100, '0,1,100', '社交解绑', 'F', '', NULL, 'system:user:unbind', '#', 8, 0, 0),
(1111, 201, '0,2,201', '操作日志导出', 'F', '', NULL, 'monitor:operlog:export', '#', 1, 0, 0),
(1112, 201, '0,2,201', '操作日志删除', 'F', '', NULL, 'monitor:operlog:remove', '#', 2, 0, 0),
(1113, 202, '0,2,202', '登录日志导出', 'F', '', NULL, 'monitor:loginlog:export', '#', 1, 0, 0),
(1114, 202, '0,2,202', '登录日志删除', 'F', '', NULL, 'monitor:loginlog:remove', '#', 2, 0, 0),
(1115, 200, '0,2,200', '强制下线', 'F', '', NULL, 'monitor:online:forceLogout', '#', 1, 0, 0),
(1121, 110, '0,1,110', 'OSS配置查询', 'F', '', NULL, 'system:oss:query', '#', 1, 0, 0),
(1122, 110, '0,1,110', 'OSS配置新增', 'F', '', NULL, 'system:oss:add', '#', 2, 0, 0),
(1123, 110, '0,1,110', 'OSS配置修改', 'F', '', NULL, 'system:oss:edit', '#', 3, 0, 0),
(1124, 110, '0,1,110', 'OSS配置删除', 'F', '', NULL, 'system:oss:remove', '#', 4, 0, 0),
(1131, 210, '0,5,210', '任务新增', 'F', '', NULL, 'job:add', '#', 1, 0, 0),
(1132, 210, '0,5,210', '任务修改', 'F', '', NULL, 'job:edit', '#', 2, 0, 0),
(1133, 210, '0,5,210', '任务删除', 'F', '', NULL, 'job:remove', '#', 3, 0, 0),
(1134, 211, '0,5,211', '调度日志删除', 'F', '', NULL, 'job:log:remove', '#', 1, 0, 0),
(1141, 400, '0,4,400', '租户查询', 'F', '', NULL, 'tenant:query', '#', 1, 0, 0),
(1142, 400, '0,4,400', '租户新增', 'F', '', NULL, 'tenant:add', '#', 2, 0, 0),
(1143, 400, '0,4,400', '租户修改', 'F', '', NULL, 'tenant:edit', '#', 3, 0, 0),
(1144, 400, '0,4,400', '租户删除', 'F', '', NULL, 'tenant:remove', '#', 4, 0, 0),
(1145, 400, '0,4,400', '租户计费查询', 'F', '', NULL, 'system:tenant:list', '#', 5, 0, 0),
(1146, 400, '0,4,400', '租户计费变更', 'F', '', NULL, 'system:tenant:edit', '#', 6, 0, 0),
(1151, 401, '0,4,401', '套餐查询', 'F', '', NULL, 'tenant:package:query', '#', 1, 0, 0),
(1152, 401, '0,4,401', '套餐新增', 'F', '', NULL, 'tenant:package:add', '#', 2, 0, 0),
(1153, 401, '0,4,401', '套餐修改', 'F', '', NULL, 'tenant:package:edit', '#', 3, 0, 0),
(1154, 401, '0,4,401', '套餐删除', 'F', '', NULL, 'tenant:package:remove', '#', 4, 0, 0),
(1155, 402, '0,4,402', '配额修改', 'F', '', NULL, 'tenant:quota:edit', '#', 1, 0, 0),
(1161, 410, '0,7,410', '应用查询', 'F', '', NULL, 'open:app:query', '#', 1, 0, 0),
(1162, 410, '0,7,410', '应用新增', 'F', '', NULL, 'open:app:add', '#', 2, 0, 0),
(1163, 410, '0,7,410', '应用修改', 'F', '', NULL, 'open:app:edit', '#', 3, 0, 0),
(1164, 410, '0,7,410', '应用删除', 'F', '', NULL, 'open:app:remove', '#', 4, 0, 0),
(1165, 410, '0,7,410', '重置密钥', 'F', '', NULL, 'open:app:resetSecret', '#', 5, 0, 0);

-- 8. 用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2);

-- 9. 用户岗位关联
INSERT INTO sys_user_post (user_id, post_id) VALUES (1, 1), (2, 4);

-- 10. 角色菜单关联(超管拥有全部菜单)
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE del_flag = 0;

-- 11. 字典类型
-- 种子必须显式写 tenant_id：HanTenantLineHandler 会给 sys_dict_type / sys_dict_data /
-- sys_config 注入 tenant_id = 当前租户，落库为 NULL 时 NULL = 1 恒为 UNKNOWN，
-- 默认管理员（tenant_id = 1）一条内置字典和参数都读不到。
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark) VALUES
(1, 1, '用户性别', 'sys_user_sex', 0, '用户性别列表'),
(2, 1, '系统开关', 'sys_normal_disable', 0, '系统开关列表'),
(3, 1, '菜单状态', 'sys_show_hide', 0, '菜单状态列表'),
(4, 1, '系统是否', 'sys_yes_no', 0, '系统是否列表'),
(5, 1, '通知类型', 'sys_notice_type', 0, '通知类型列表'),
(6, 1, '通知状态', 'sys_notice_status', 0, '通知状态列表'),
(7, 1, '操作类型', 'sys_oper_type', 0, '操作类型列表'),
(8, 1, '系统状态', 'sys_common_status', 0, '登录状态列表'),
(9, 1, '客户端类型', 'sys_client_type', 0, '客户端类型列表'),
(10, 1, '数据范围', 'sys_data_scope', 0, '数据范围列表'),
(11, 1, '租户隔离类型', 'sys_isolation_type', 0, '租户隔离类型列表');

-- 12. 字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) VALUES
(1, 1, 'sys_user_sex', '未知', '0', 1, '', '', 0, 0),
(2, 1, 'sys_user_sex', '男', '1', 2, '', '', 0, 0),
(3, 1, 'sys_user_sex', '女', '2', 3, '', '', 0, 0),
(4, 1, 'sys_normal_disable', '正常', '0', 1, '', 'primary', 1, 0),
(5, 1, 'sys_normal_disable', '停用', '1', 2, '', 'danger', 0, 0),
(6, 1, 'sys_show_hide', '显示', '0', 1, '', 'primary', 1, 0),
(7, 1, 'sys_show_hide', '隐藏', '1', 2, '', 'danger', 0, 0),
(8, 1, 'sys_yes_no', '是', 'Y', 1, '', 'primary', 1, 0),
(9, 1, 'sys_yes_no', '否', 'N', 2, '', 'danger', 0, 0),
(10, 1, 'sys_notice_type', '通知', '1', 1, '', 'warning', 0, 0),
(11, 1, 'sys_notice_type', '公告', '2', 2, '', 'success', 0, 0),
(12, 1, 'sys_notice_status', '正常', '0', 1, '', 'primary', 1, 0),
(13, 1, 'sys_notice_status', '关闭', '1', 2, '', 'danger', 0, 0),
(14, 1, 'sys_oper_type', '其它', '0', 0, '', 'info', 0, 0),
(15, 1, 'sys_oper_type', '新增', '1', 1, '', 'info', 0, 0),
(16, 1, 'sys_oper_type', '修改', '2', 2, '', 'info', 0, 0),
(17, 1, 'sys_oper_type', '删除', '3', 3, '', 'danger', 0, 0),
(18, 1, 'sys_oper_type', '查询', '4', 4, '', 'primary', 0, 0),
(19, 1, 'sys_oper_type', '导出', '5', 5, '', 'warning', 0, 0),
(20, 1, 'sys_oper_type', '导入', '6', 6, '', 'warning', 0, 0),
(21, 1, 'sys_oper_type', '授权', '7', 7, '', 'primary', 0, 0),
(22, 1, 'sys_oper_type', '强退', '8', 8, '', 'danger', 0, 0),
(23, 1, 'sys_oper_type', '清空', '9', 9, '', 'danger', 0, 0),
(24, 1, 'sys_common_status', '成功', '0', 1, '', 'success', 0, 0),
(25, 1, 'sys_common_status', '失败', '1', 2, '', 'danger', 0, 0),
(26, 1, 'sys_client_type', 'PC端', 'pc', 1, '', 'primary', 1, 0),
(27, 1, 'sys_client_type', 'App端', 'app', 2, '', 'success', 0, 0),
(28, 1, 'sys_client_type', 'H5端', 'h5', 3, '', 'info', 0, 0),
(29, 1, 'sys_client_type', '微信小程序', 'wechat_mp', 4, '', 'success', 0, 0),
(30, 1, 'sys_client_type', '微信公众号', 'wechat_oa', 5, '', 'success', 0, 0),
(31, 1, 'sys_client_type', '开放API', 'api', 6, '', 'warning', 0, 0),
(32, 1, 'sys_data_scope', '全部数据', '1', 1, '', 'primary', 0, 0),
(33, 1, 'sys_data_scope', '自定义数据', '2', 2, '', 'info', 0, 0),
(34, 1, 'sys_data_scope', '本部门数据', '3', 3, '', 'info', 0, 0),
(35, 1, 'sys_data_scope', '本部门及以下', '4', 4, '', 'info', 0, 0),
(36, 1, 'sys_data_scope', '仅本人数据', '5', 5, '', 'info', 0, 0),
(37, 1, 'sys_isolation_type', '逻辑隔离', 'logical', 1, '', 'primary', 1, 0),
(38, 1, 'sys_isolation_type', '物理隔离', 'physical', 2, '', 'warning', 0, 0),
(39, 1, 'sys_isolation_type', '混合隔离', 'hybrid', 3, '', 'info', 0, 0);

-- 13. 参数配置
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, config_type, remark) VALUES
(1, 1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
(2, 1, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', '初始化密码 123456'),
(3, 1, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', '深色主题theme-dark，浅色主题theme-light'),
(4, 1, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', '是否开启验证码功能（true开启，false关闭）'),
(5, 1, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', '是否开启注册用户功能（true开启，false关闭）'),
(6, 1, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),
(7, 1, '用户登录-微信扫码登录开关', 'sys.login.wechatEnabled', 'false', 'Y', '是否开启微信扫码登录（true开启，false关闭）；开启前需在服务端配置 WECHAT_OPEN_APP_ID/WECHAT_OPEN_APP_SECRET');

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
-- service_name / handler 的列说明在 PostgreSQL 版是独立的列注释语句，
-- MySQL 用建表内联 COMMENT 承载，信息不丢。
CREATE TABLE sys_job (
    job_id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT,
    job_name        VARCHAR(100)    NOT NULL,
    job_group       VARCHAR(64)     DEFAULT 'DEFAULT',
    invoke_target   VARCHAR(500)    NOT NULL,
    service_name    VARCHAR(100)    COMMENT '执行器服务名（Nacos 中的服务名，远程调用时使用）',
    handler         VARCHAR(200)    COMMENT '执行器处理方法（远程调用时的 handler 路径）',
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
    job_log_id      BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
-- 执行环境：MySQL 8
-- ============================================================

-- 1. JobFlow 字段与列说明已包含在上方 sys_job 建表语句中。

-- 2. 清理 Quartz 相关表（按依赖顺序删除）
DROP TABLE IF EXISTS qrtz_fired_triggers;
DROP TABLE IF EXISTS qrtz_paused_trigger_grps;
DROP TABLE IF EXISTS qrtz_scheduler_state;
DROP TABLE IF EXISTS qrtz_locks;
DROP TABLE IF EXISTS qrtz_simprop_triggers;
DROP TABLE IF EXISTS qrtz_simple_triggers;
DROP TABLE IF EXISTS qrtz_cron_triggers;
DROP TABLE IF EXISTS qrtz_blob_triggers;
DROP TABLE IF EXISTS qrtz_triggers;
DROP TABLE IF EXISTS qrtz_job_details;
DROP TABLE IF EXISTS qrtz_calendars;

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
    quota_id        BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
) COMMENT='租户资源配额';
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
-- MySQL 版租户计费扩展表。
-- PostgreSQL 版用独立的表注释与列注释语句，MySQL 改为建表内联 COMMENT，注释内容原样保留。
-- PostgreSQL 版的 CREATE INDEX IF NOT EXISTS 在 MySQL 没有对应写法，
-- 索引改为在建表语句内声明，配合 CREATE TABLE IF NOT EXISTS 达到同样的可重复执行效果。

CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
    package_id BIGINT NOT NULL COMMENT 'Package ID',
    start_time TIMESTAMP NOT NULL COMMENT 'Subscription start time',
    end_time TIMESTAMP NOT NULL COMMENT 'Subscription end time',
    status SMALLINT DEFAULT 0 COMMENT '0 active, 1 expired, 2 canceled',
    amount NUMERIC(10,2) DEFAULT 0 COMMENT 'Subscription amount',
    payment_method VARCHAR(32) DEFAULT NULL COMMENT 'Payment method',
    payment_no VARCHAR(128) DEFAULT NULL COMMENT 'Payment order number',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL,
    INDEX idx_tenant_sub_tenant_id (tenant_id)
) COMMENT='Tenant subscription record';

CREATE TABLE IF NOT EXISTS sys_tenant_bill (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
    subscription_id BIGINT DEFAULT NULL COMMENT 'Related subscription ID',
    bill_type VARCHAR(32) NOT NULL COMMENT 'subscribe, renew, upgrade',
    amount NUMERIC(10,2) NOT NULL COMMENT 'Bill amount',
    status SMALLINT DEFAULT 0 COMMENT '0 pending, 1 paid, 2 canceled',
    remark VARCHAR(500) DEFAULT NULL COMMENT 'Billing note',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    pay_time TIMESTAMP DEFAULT NULL COMMENT 'Payment time',
    INDEX idx_tenant_bill_tenant_id (tenant_id)
) COMMENT='Tenant billing record';


-- ============================================================
-- source: postgres\workflow\00-schema.sql
-- ============================================================
-- MySQL 版工作流扩展表。
-- Flowable 引擎运行时表由引擎自身维护，本文件只负责 Han 业务扩展表。
-- 同上：PostgreSQL 版的 CREATE INDEX IF NOT EXISTS 改为建表内联索引。

CREATE TABLE IF NOT EXISTS wf_category (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT uk_wf_category_code UNIQUE (category_code, tenant_id),
    INDEX idx_wf_category_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS wf_form (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT uk_wf_form_key UNIQUE (form_key, tenant_id),
    INDEX idx_wf_form_tenant (tenant_id)
);

CREATE TABLE IF NOT EXISTS wf_deploy_extend (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    remark          VARCHAR(500),
    INDEX idx_wf_deploy_extend_deployment (deployment_id),
    INDEX idx_wf_deploy_extend_tenant (tenant_id),
    INDEX idx_wf_deploy_extend_process_key (process_key)
);

CREATE TABLE IF NOT EXISTS wf_instance_extend (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    CONSTRAINT uk_wf_instance_extend_pi UNIQUE (process_instance_id),
    INDEX idx_wf_instance_extend_tenant (tenant_id),
    INDEX idx_wf_instance_extend_business (business_key),
    INDEX idx_wf_instance_extend_start_user (start_user_id),
    INDEX idx_wf_instance_extend_status (status)
);

CREATE TABLE IF NOT EXISTS wf_copy (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    del_flag            SMALLINT        DEFAULT 0,
    INDEX idx_wf_copy_process_instance (process_instance_id),
    INDEX idx_wf_copy_user (user_id),
    INDEX idx_wf_copy_tenant (tenant_id)
);


-- ============================================================
-- source: postgres\workflow\10-seed.sql
-- ============================================================
-- PostgreSQL 版用冲突即跳过的插入写法保证可重复执行，MySQL 用
-- ON DUPLICATE KEY UPDATE id = id 达到同样效果：命中 uk_wf_category_code 时不改任何数据。
-- 这里不用 INSERT IGNORE，是为了避免把字符集截断之类的真实错误一起降级成告警。
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
ON DUPLICATE KEY UPDATE id = id;


-- ============================================================
-- source: postgres\open\00-schema.sql
-- ============================================================
-- MySQL 版开放平台核心表。

CREATE TABLE IF NOT EXISTS open_app (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
) COMMENT='Open platform application';

CREATE TABLE IF NOT EXISTS open_user_authorization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
) COMMENT='User authorization record';


-- ============================================================
-- source: postgres\open\10-seed.sql
-- ============================================================
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


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
    oss_config_id  BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
) COMMENT='OSS存储配置';


-- ============================================================
-- source: postgres\file\10-seed.sql
-- ============================================================
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。
