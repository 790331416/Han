-- Han full 档 PostgreSQL 全新初始化脚本
-- 2026-04-15 由旧的分模块 SQL 目录结构合并生成

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
-- 结构与 sql/upgrades/postgres/20260415_social_login_migration.sql +
-- sql/upgrades/postgres/20260720_wechat_social_login.sql 的最终形态一致：
-- 不建全局 UNIQUE(provider, open_id)，改为两个租户隔离的唯一索引。
CREATE TABLE sys_user_social (
    id              BIGINT          NOT NULL PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    tenant_id       BIGINT,
    provider        VARCHAR(32)     NOT NULL,
    open_id         VARCHAR(128)    NOT NULL,
    access_token    VARCHAR(512),
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    extra           TEXT,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_social_user_id ON sys_user_social (user_id);
CREATE INDEX idx_user_social_provider_openid ON sys_user_social (provider, open_id);

-- 租户内一个第三方身份只绑一个账号（tenant_id 为空按 0 归一，避免 NULL 逃逸唯一约束）
CREATE UNIQUE INDEX uq_user_social_tenant_provider_openid
    ON sys_user_social (COALESCE(tenant_id, 0), provider, open_id);

-- 一个账号同 provider 只绑一个第三方身份
CREATE UNIQUE INDEX uq_user_social_user_provider
    ON sys_user_social (user_id, provider);


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
-- 22. 唯一约束（与逻辑删除兼容的部分唯一索引）
-- =============================================
-- del_flag 是逻辑删除标记，唯一约束若不带 WHERE del_flag = 0，
-- 软删除一条记录后就再也建不出同名记录。索引名与列顺序必须与
-- sql/upgrades/postgres/phase5_unique_constraint.sql 保持一致，
-- 否则升级链会在同一张表上建出两个等价索引。
CREATE UNIQUE INDEX sys_user_username_tenant_uniq
    ON sys_user (username, tenant_id) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_role_key_tenant
    ON sys_role (tenant_id, role_key) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_role_name_tenant
    ON sys_role (tenant_id, role_name) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_post_code_tenant
    ON sys_post (tenant_id, post_code) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_dict_type_tenant
    ON sys_dict_type (COALESCE(tenant_id, 0), dict_type) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_dict_data_tenant
    ON sys_dict_data (COALESCE(tenant_id, 0), dict_type, dict_value) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_config_key_tenant
    ON sys_config (COALESCE(tenant_id, 0), config_key) WHERE del_flag = 0;

CREATE UNIQUE INDEX uk_sys_client_key
    ON sys_client (client_key) WHERE del_flag = 0;


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
(3, 0, '0', '系统工具', 'M', 'tool', NULL, NULL, 'tool', 3, 0, 0),
(4, 0, '0', '租户管理', 'M', 'tenant', NULL, NULL, 'peoples', 4, 0, 0),
(5, 0, '0', '任务调度', 'M', 'job', NULL, NULL, 'timer', 5, 0, 0),
(6, 0, '0', '工作流', 'M', 'workflow', NULL, NULL, 'connection', 6, 0, 0),
(7, 0, '0', '开放平台', 'M', 'open', NULL, NULL, 'platform', 7, 0, 0),
(500, 0, '0', 'AI智能', 'M', 'ai', NULL, NULL, 'magic-stick', 8, 0, 0);

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
(300, 3, '0,3', '代码生成', 'C', 'gen', 'tool/gen/index', 'tool:gen:list', 'code', 1, 0, 0);

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(400, 4, '0,4', '租户列表', 'C', 'list', 'tenant/list/index', 'tenant:list', 'list', 1, 0, 0),
(401, 4, '0,4', '套餐管理', 'C', 'package', 'tenant/package/index', 'tenant:package:list', 'component', 2, 0, 0),
(402, 4, '0,4', '资源配额', 'C', 'quota', 'tenant/quota/index', 'tenant:quota:query', 'pie-chart', 3, 0, 0);

-- 7.1 任务调度 / 工作流 / 开放平台 / AI 智能菜单
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

INSERT INTO sys_menu (id, parent_id, ancestors, menu_name, menu_type, path, component, perms, icon, sort, visible, status) VALUES
(510, 500, '0,500', 'AI模型管理', 'C', 'model', 'ai/model/index', 'ai:model:list', 'cpu', 1, 0, 0),
(511, 500, '0,500', '知识库', 'C', 'knowledge', 'ai/knowledge/index', 'ai:kb:list', 'collection', 2, 0, 0),
(512, 500, '0,500', 'MCP管理', 'C', 'mcp', 'ai/mcp/index', 'ai:mcp:list', 'link', 3, 0, 0),
(513, 500, '0,500', '智能体', 'C', 'agent', 'ai/agent/index', 'ai:agent:list', 'user-filled', 4, 0, 0),
(514, 500, '0,500', 'AI工作流', 'C', 'workflow', 'ai/workflow/index', 'ai:workflow:list', 'chat-dot-round', 5, 0, 0),
(515, 500, '0,500', 'Prompt模板', 'C', 'prompt', 'ai/prompt/index', 'ai:prompt:list', 'document', 6, 0, 0),
(516, 500, '0,500', 'Token统计', 'C', 'token', 'ai/token/index', 'ai:token:stats', 'data-analysis', 7, 0, 0),
(517, 500, '0,500', 'AI对话', 'C', 'chat', 'ai/chat/index', 'ai:chat:list', 'chat-line-square', 8, 0, 0);

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
(1061, 106, '0,1,106', '参数查询', 'F', '', NULL, 'system:config:query', '#', 1, 0, 0),
(1062, 106, '0,1,106', '参数新增', 'F', '', NULL, 'system:config:add', '#', 2, 0, 0),
(1063, 106, '0,1,106', '参数修改', 'F', '', NULL, 'system:config:edit', '#', 3, 0, 0),
(1064, 106, '0,1,106', '参数删除', 'F', '', NULL, 'system:config:remove', '#', 4, 0, 0),
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
(1165, 410, '0,7,410', '重置密钥', 'F', '', NULL, 'open:app:resetSecret', '#', 5, 0, 0),
(1171, 510, '0,500,510', 'AI模型查询', 'F', '', NULL, 'ai:model:query', '#', 1, 0, 0),
(1172, 510, '0,500,510', 'AI模型新增', 'F', '', NULL, 'ai:model:add', '#', 2, 0, 0),
(1173, 510, '0,500,510', 'AI模型修改', 'F', '', NULL, 'ai:model:edit', '#', 3, 0, 0),
(1174, 510, '0,500,510', 'AI模型删除', 'F', '', NULL, 'ai:model:remove', '#', 4, 0, 0),
(1175, 510, '0,500,510', 'AI模型连通性测试', 'F', '', NULL, 'ai:model:test', '#', 5, 0, 0),
(1181, 511, '0,500,511', '知识库查询', 'F', '', NULL, 'ai:kb:query', '#', 1, 0, 0),
(1182, 511, '0,500,511', '知识库新增', 'F', '', NULL, 'ai:kb:add', '#', 2, 0, 0),
(1183, 511, '0,500,511', '知识库修改', 'F', '', NULL, 'ai:kb:edit', '#', 3, 0, 0),
(1184, 511, '0,500,511', '知识库删除', 'F', '', NULL, 'ai:kb:remove', '#', 4, 0, 0),
(1185, 511, '0,500,511', '知识库文档上传', 'F', '', NULL, 'ai:kb:upload', '#', 5, 0, 0),
(1191, 512, '0,500,512', 'MCP查询', 'F', '', NULL, 'ai:mcp:query', '#', 1, 0, 0),
(1192, 512, '0,500,512', 'MCP新增', 'F', '', NULL, 'ai:mcp:add', '#', 2, 0, 0),
(1193, 512, '0,500,512', 'MCP修改', 'F', '', NULL, 'ai:mcp:edit', '#', 3, 0, 0),
(1194, 512, '0,500,512', 'MCP删除', 'F', '', NULL, 'ai:mcp:remove', '#', 4, 0, 0),
(1201, 513, '0,500,513', '智能体新增', 'F', '', NULL, 'ai:agent:add', '#', 1, 0, 0),
(1202, 513, '0,500,513', '智能体修改', 'F', '', NULL, 'ai:agent:edit', '#', 2, 0, 0),
(1203, 513, '0,500,513', '智能体删除', 'F', '', NULL, 'ai:agent:remove', '#', 3, 0, 0),
(1211, 514, '0,500,514', 'AI工作流新增', 'F', '', NULL, 'ai:workflow:add', '#', 1, 0, 0),
(1212, 514, '0,500,514', 'AI工作流修改', 'F', '', NULL, 'ai:workflow:edit', '#', 2, 0, 0),
(1213, 514, '0,500,514', 'AI工作流删除', 'F', '', NULL, 'ai:workflow:remove', '#', 3, 0, 0),
(1221, 515, '0,500,515', 'Prompt模板查询', 'F', '', NULL, 'ai:prompt:query', '#', 1, 0, 0),
(1222, 515, '0,500,515', 'Prompt模板新增', 'F', '', NULL, 'ai:prompt:add', '#', 2, 0, 0),
(1223, 515, '0,500,515', 'Prompt模板编辑', 'F', '', NULL, 'ai:prompt:edit', '#', 3, 0, 0),
(1224, 515, '0,500,515', 'Prompt模板删除', 'F', '', NULL, 'ai:prompt:remove', '#', 4, 0, 0);

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
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark)
SELECT v.id, 1, v.dict_name, v.dict_type, v.status, v.remark FROM (VALUES
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
(14, 'AI Prompt模板分类', 'ai_prompt_category', 0, 'AI Prompt模板分类列表')
) AS v(id, dict_name, dict_type, status, remark);

-- 12. 字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT v.id, 1, v.dict_type, v.dict_label, v.dict_value, v.dict_sort, v.css_class, v.list_class, v.is_default, v.status FROM (VALUES
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
(63, 'ai_prompt_category', '助手模板', 'assistant', 30, '', 'warning', 0, 0)
) AS v(id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status);

-- 12.1 AI 扩展字典类型
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark)
SELECT v.id, 1, v.dict_name, v.dict_type, v.status, v.remark FROM (VALUES
(15, 'AI知识库类型', 'ai_kb_type', 0, 'AI知识库类型列表'),
(16, 'AI MCP传输类型', 'ai_mcp_transport_type', 0, 'AI MCP 传输类型列表'),
(17, 'AI工作流类型', 'ai_workflow_type', 0, 'AI工作流类型列表'),
(18, 'AI知识库索引状态', 'ai_knowledge_index_status', 0, 'AI知识库索引状态列表')
) AS v(id, dict_name, dict_type, status, remark);

-- 12.2 AI 扩展字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT v.id, 1, v.dict_type, v.dict_label, v.dict_value, v.dict_sort, v.css_class, v.list_class, v.is_default, v.status FROM (VALUES
(71, 'ai_kb_type', '通用知识库', 'general', 10, '', 'primary', 1, 0),
(72, 'ai_kb_type', 'QA问答库', 'qa', 20, '', 'success', 0, 0),
(73, 'ai_kb_type', '网页爬取', 'web', 30, '', 'warning', 0, 0),
(74, 'ai_mcp_transport_type', 'SSE', 'sse', 10, '', 'primary', 1, 0),
(75, 'ai_mcp_transport_type', 'Streamable HTTP', 'streamable_http', 20, '', 'success', 0, 0),
(76, 'ai_mcp_transport_type', 'Stdio', 'stdio', 30, '', 'info', 0, 0),
(77, 'ai_workflow_type', '简单对话', 'simple', 10, '', 'primary', 1, 0),
(78, 'ai_workflow_type', '高级编排', 'advanced', 20, '', 'success', 0, 0),
(79, 'ai_knowledge_index_status', '待处理', 'pending', 10, '', 'info', 0, 0),
(80, 'ai_knowledge_index_status', '索引中', 'indexing', 20, '', 'warning', 0, 0),
(81, 'ai_knowledge_index_status', '已完成', 'completed', 30, '', 'success', 0, 0),
(82, 'ai_knowledge_index_status', '失败', 'failed', 40, '', 'danger', 0, 0)
) AS v(id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status);

-- 13. 参数配置
INSERT INTO sys_config (id, tenant_id, config_name, config_key, config_value, config_type, remark)
SELECT v.id, 1, v.config_name, v.config_key, v.config_value, v.config_type, v.remark FROM (VALUES
(1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),
(2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', '初始化密码 123456'),
(3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', '深色主题theme-dark，浅色主题theme-light'),
(4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', '是否开启验证码功能（true开启，false关闭）'),
(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', '是否开启注册用户功能（true开启，false关闭）'),
(6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),
(7, '用户登录-微信扫码登录开关', 'sys.login.wechatEnabled', 'false', 'Y', '是否开启微信扫码登录（true开启，false关闭）；开启前需在服务端配置 WECHAT_OPEN_APP_ID/WECHAT_OPEN_APP_SECRET')
) AS v(id, config_name, config_key, config_value, config_type, remark);

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
-- 租户计费扩展表（PostgreSQL）。

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
-- 开放平台核心表（PostgreSQL）。

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
    supports_vision CHAR(1)         DEFAULT '0',
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
    suggested_questions TEXT            DEFAULT '[]',
    published           CHAR(1)         DEFAULT '0',
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_workflow IS 'AI工作流表';
COMMENT ON COLUMN ai_workflow.suggested_questions IS '开场推荐问题（JSON字符串数组，最多5条）';

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
    images          TEXT,
    meta            TEXT,
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
    suggested_questions TEXT,
    model_id       BIGINT,
    knowledge_base_ids TEXT,
    mcp_server_ids TEXT,
    temperature    NUMERIC(3,2)    DEFAULT 0.7,
    max_tokens     INT             DEFAULT 2048,
    history_limit  INT,
    retrieval_top_k INT,
    similarity_threshold NUMERIC(4,3),
    published      CHAR(1)         DEFAULT '0',
    share_key      VARCHAR(64),
    status         CHAR(1)         DEFAULT '0',
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       INT             DEFAULT 0
);
COMMENT ON TABLE ai_agent IS 'AI智能体';
COMMENT ON COLUMN ai_agent.share_key IS '公开分享链接 key（发布时生成，重置后旧链接失效）';
COMMENT ON COLUMN ai_agent.history_limit IS '对话历史注入条数（NULL=默认12）';
COMMENT ON COLUMN ai_agent.retrieval_top_k IS '知识库检索返回条数（NULL=默认5）';
COMMENT ON COLUMN ai_agent.similarity_threshold IS '向量检索相似度阈值（NULL=默认0.30）';
COMMENT ON COLUMN ai_agent.suggested_questions IS '开场推荐问题（JSON字符串数组，最多5条）';

-- =============================================
-- 32. Prompt模板表
-- =============================================
-- 列宽以 sql/upgrades/postgres/phase8_prompt_template_alignment.sql 为权威口径，
-- 历史上 init 是 100/20/500、phase8 是 200/30/1000，两类环境结构长期分叉。
CREATE TABLE ai_prompt_template (
    template_id   BIGSERIAL       PRIMARY KEY,
    tenant_id     BIGINT,
    template_name VARCHAR(200)    NOT NULL,
    category      VARCHAR(30)     NOT NULL DEFAULT 'system',
    content       TEXT            NOT NULL,
    variables     TEXT,
    description   VARCHAR(1000),
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


-- ============================================================
-- source: postgres\gen\00-schema.sql
-- ============================================================
-- 代码生成器元数据表迁移（PostgreSQL）。

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


-- ============================================================
-- AI 菜单归一化
-- ============================================================
-- 「AI智能」目录与「Prompt模板」菜单在前面的 sys_menu 种子里已经用固定 ID
-- （500 / 515 / 1221-1224）播过一遍，这里再按 perms 语义键归一一次，
-- 保证本文件与 sql/upgrades/postgres/ 下的菜单对齐脚本得到一致结果。
-- 在 clean 库上本段是空操作，只有 ID 被占用的历史库才会真正改动。
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

