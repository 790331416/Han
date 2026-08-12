-- Han full 档 MySQL 8.4 全新初始化脚本
-- PostgreSQL 仍为默认数据库；本文件是 full 档的正式 MySQL 入口。
--
-- 本文件是 sql/tiers/full/full-init.sql（PostgreSQL）的 MySQL 8.4 等价版本，
-- 表结构、字段、索引、种子数据与 PostgreSQL 版逐项对齐。
-- 转换约定沿用 sql/tiers/small/small-init-mysql.sql 已在真实 MySQL 8.4 验证过的口径：
--   1. PostgreSQL 的序列自增主键统一写成 BIGINT AUTO_INCREMENT PRIMARY KEY；
--   2. 表达式唯一索引（COALESCE(tenant_id, 0)）改用 STORED 生成列承载，再在生成列上建唯一键；
--   3. PostgreSQL 版重复定义两次的 sys_user_social 合并为一处，索引取两处的并集；
--   4. Quartz 清理语句去掉 PostgreSQL 的级联关键字；
--   5. JobFlow 追加字段直接并入 sys_job 建表语句，不再保留 ALTER 补丁；
--   6. 不写库级 / 表级字符集，统一依赖服务端参数 --character-set-server=utf8mb4，
--      见 deploy/full/docker-compose-mysql.yml。
--
-- 适用范围：只用于 MySQL 全新初始化（clean init），不承担存量 MySQL 库的增量升级。
--
-- ============================================================
-- MySQL 8.4 下无等价物、或语义与 PostgreSQL 不一致的点（落到具体位置时会再次注释）
-- ============================================================
--   A. 部分唯一索引：PostgreSQL 用 "WHERE del_flag = 0" 把唯一性限定在未删除记录上，
--      MySQL 8.4 没有筛选索引。见「22. 唯一约束」段，改为全量唯一索引，
--      软删记录仍占用唯一名字空间，**语义不等价**。
--   B. 表达式索引：MySQL 8.0.13+ 虽然支持函数索引键，但 small 档已确立生成列口径，
--      本文件沿用生成列，因此比 PostgreSQL 版多出 tenant_scope 列（只多不少，不影响读写）。
--   C. PL/pgSQL 匿名块：MySQL 没有对应语法。文件末尾「AI 菜单归一化」段改写为
--      等价的确定性幂等语句，处理方式见该段注释。
--   D. TIMESTAMP 取值范围：MySQL 的 TIMESTAMP 只覆盖 1970-01-01 ~ 2038-01-19，
--      窄于 PostgreSQL 的 TIMESTAMP。sys_tenant.expire_time、sys_tenant_subscription.end_time
--      这类业务到期时间一旦超过 2038 年会直接写入失败，见对应表上的注释。
--   E. 索引的 IF NOT EXISTS：MySQL 不支持 CREATE INDEX IF NOT EXISTS。凡 PostgreSQL 侧
--      写了 IF NOT EXISTS 的索引，一律并入建表语句内联声明，保持脚本可重复执行；
--      PostgreSQL 侧本来就没写 IF NOT EXISTS 的索引，仍保持独立 CREATE INDEX。
--   F. 向量检索：本次转换未发现 PostgreSQL 侧使用 pgvector，ai_paragraph.embedding
--      两边都是 TEXT，因此没有向量能力缺口。若将来 PostgreSQL 侧改用向量类型，
--      MySQL 8.4 无等价类型（VECTOR 到 MySQL 9.0 才提供），届时无法平移。
--   G. 表 / 列注释：PostgreSQL 用独立的注释语句，MySQL 只能内联 COMMENT。
--      注释文本原样照搬 PostgreSQL 版（含其中的英文原文），保证两边库内注释一致。
--   H. TEXT 默认值：MySQL 的 TEXT 默认值必须写成括号表达式，因此 DEFAULT '[]'
--      在本文件里写作 DEFAULT ('[]')，语义等价。
--
-- 前置约定：MySQL 8.4 的 explicit_defaults_for_timestamp 默认为 ON，
-- 未显式声明的 TIMESTAMP 列可空且不会被自动加上 ON UPDATE 行为，与 PostgreSQL 一致；
-- 若目标实例把该参数关成 OFF，首个 TIMESTAMP 列的行为会与本文件预期不符。

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
-- PostgreSQL 版在「4.1」和「21」两处重复建了同一张 sys_user_social，
-- 这里合并为一处，索引取两处的并集（4 个），避免 MySQL 报重复建表。
-- tenant_scope 是【差异点 B】引入的生成列：PostgreSQL 的唯一索引直接建在
-- COALESCE(tenant_id, 0) 表达式上，MySQL 侧用 STORED 生成列承载同一语义，
-- 目的同样是"tenant_id 为空按 0 归一，避免 NULL 逃逸唯一约束"。
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
    -- 租户内一个第三方身份只绑一个账号
    UNIQUE KEY uq_user_social_tenant_provider_openid (tenant_scope, provider, open_id),
    -- 一个账号同 provider 只绑一个第三方身份
    UNIQUE KEY uq_user_social_user_provider (user_id, provider)
);

-- =============================================
-- 5. 岗位表
-- =============================================
-- 列名保持 post_sort：SysPostPo.postSort 走驼峰下划线映射，
-- 与 sql/upgrades/postgres/20260415_system_post_sort_alignment.sql 的口径一致。
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
-- tenant_scope 见【差异点 B】：承载 PostgreSQL 侧 COALESCE(tenant_id, 0) 唯一索引语义。
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
-- expire_time 受【差异点 D】约束：会话过期时间不会超过 2038，风险可接受。
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
-- PostgreSQL 版在这里第二次定义 sys_user_social，MySQL 侧已在「4.1」合并处理，
-- 索引 idx_user_social_provider_openid / uq_user_social_tenant_provider_openid /
-- uq_user_social_user_provider 全部并到上面那张表，本段无需再建表。

-- =============================================
-- 22. 唯一约束（PostgreSQL 侧是与逻辑删除兼容的部分唯一索引）
-- =============================================
-- PostgreSQL 版这几个索引都带 "WHERE del_flag = 0"，作用是让软删除过的记录
-- 不占用唯一名字空间，删掉 admin 之后还能再建一个 admin。
-- MySQL 8.4 确实没有筛选索引，但有函数式键部件（8.0.13 起支持）：
-- 追加一列 (IF(del_flag = 0, 0, NULL))，未删除行取 0 参与唯一性判定，
-- 已删除行取 NULL —— NULL 在唯一索引里互不冲突，语义被完整复现。
-- 写法与 small-init-mysql.sql 一致，三档保持同一口径。
-- 与 PostgreSQL 的差异只剩索引体积：MySQL 仍收录已删除行，只是键值判为 NULL。
-- 索引名与列顺序保持与 PostgreSQL 版一致，便于两边对照与后续升级链复用。
CREATE UNIQUE INDEX sys_user_username_tenant_uniq
    ON sys_user (username, tenant_id, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_role_key_tenant
    ON sys_role (tenant_id, role_key, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_role_name_tenant
    ON sys_role (tenant_id, role_name, (IF(del_flag = 0, 0, NULL)));

CREATE UNIQUE INDEX uk_sys_post_code_tenant
    ON sys_post (tenant_id, post_code, (IF(del_flag = 0, 0, NULL)));

-- 下面三个在 PostgreSQL 上同时是"表达式索引 + 部分索引"，
-- MySQL 侧用 tenant_scope 生成列还原 COALESCE(tenant_id, 0)，
-- 用 IF(del_flag...) 函数式键部件还原 del_flag 过滤，两部分都等价。
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
(108, 1, '0,1', '客户端管理', 'C', 'client', 'system/client/index', 'system:client:list', 'client', 9, 0, 0),
(109, 1, '0,1', '文件管理', 'C', 'file', 'system/file/index', 'file:list', 'upload', 10, 0, 0),
(110, 1, '0,1', 'OSS配置', 'C', 'oss-config', 'system/oss-config/index', 'system:oss:list', 'upload', 11, 0, 0);

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
-- PostgreSQL 版用 "SELECT ... FROM (VALUES ...) AS v(...)" 拼这批常量，
-- MySQL 不支持派生表列别名列表，改写成直接多行 VALUES，tenant_id 逐行写 1，数据完全一致。
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
(11, 1, '租户隔离类型', 'sys_isolation_type', 0, '租户隔离类型列表'),
(12, 1, 'AI模型类型', 'ai_model_type', 0, 'AI模型管理模型类型列表'),
(13, 1, 'AI模型供应商', 'ai_model_provider', 0, 'AI模型管理供应商列表'),
(14, 1, 'AI Prompt模板分类', 'ai_prompt_category', 0, 'AI Prompt模板分类列表');

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
(39, 1, 'sys_isolation_type', '混合隔离', 'hybrid', 3, '', 'info', 0, 0),
(40, 1, 'ai_model_type', '大语言模型', 'LLM', 10, '', 'primary', 1, 0),
(41, 1, 'ai_model_type', '图片生成模型', 'IMAGE', 20, '', 'success', 0, 0),
(42, 1, 'ai_model_type', '视频生成模型', 'VIDEO', 30, '', 'warning', 0, 0),
(43, 1, 'ai_model_type', '视频剪辑合成', 'VIDEO_EDIT', 40, '', 'warning', 0, 0),
(44, 1, 'ai_model_type', '向量模型', 'EMBEDDING', 50, '', 'info', 0, 0),
(45, 1, 'ai_model_type', '重排模型', 'RERANK', 60, '', 'info', 0, 0),
(46, 1, 'ai_model_type', '语音合成', 'TTS', 70, '', 'success', 0, 0),
(47, 1, 'ai_model_type', '语音识别', 'STT', 80, '', 'info', 0, 0),
(48, 1, 'ai_model_provider', 'OpenAI', 'openai', 10, '', 'primary', 0, 0),
(49, 1, 'ai_model_provider', '火山引擎/方舟', 'volcengine', 20, '', 'warning', 1, 0),
(50, 1, 'ai_model_provider', 'DeepSeek', 'deepseek', 30, '', 'success', 0, 0),
(51, 1, 'ai_model_provider', '通义千问', 'qwen', 40, '', 'success', 0, 0),
(52, 1, 'ai_model_provider', '智谱AI', 'zhipu', 50, '', 'primary', 0, 0),
(53, 1, 'ai_model_provider', '百度千帆', 'baidu', 60, '', 'primary', 0, 0),
(54, 1, 'ai_model_provider', 'Ollama', 'ollama', 70, '', 'info', 0, 0),
(55, 1, 'ai_model_provider', 'Azure OpenAI', 'azure', 80, '', 'primary', 0, 0),
(56, 1, 'ai_model_provider', 'Anthropic', 'anthropic', 90, '', 'info', 0, 0),
(57, 1, 'ai_model_provider', 'SiliconFlow', 'siliconflow', 100, '', 'success', 0, 0),
(58, 1, 'ai_model_provider', 'Coze(扣子)', 'coze', 110, '', 'warning', 0, 0),
(59, 1, 'ai_model_provider', 'DIFY', 'dify', 120, '', 'info', 0, 0),
(60, 1, 'ai_model_provider', 'FastGPT', 'fastgpt', 130, '', 'info', 0, 0),
(61, 1, 'ai_prompt_category', '系统提示词', 'system', 10, '', 'primary', 1, 0),
(62, 1, 'ai_prompt_category', '用户模板', 'user', 20, '', 'success', 0, 0),
(63, 1, 'ai_prompt_category', '助手模板', 'assistant', 30, '', 'warning', 0, 0);

-- 12.1 AI 扩展字典类型
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark) VALUES
(15, 1, 'AI知识库类型', 'ai_kb_type', 0, 'AI知识库类型列表'),
(16, 1, 'AI MCP传输类型', 'ai_mcp_transport_type', 0, 'AI MCP 传输类型列表'),
(17, 1, 'AI工作流类型', 'ai_workflow_type', 0, 'AI工作流类型列表'),
(18, 1, 'AI知识库索引状态', 'ai_knowledge_index_status', 0, 'AI知识库索引状态列表');

-- 12.2 AI 扩展字典数据
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) VALUES
(71, 1, 'ai_kb_type', '通用知识库', 'general', 10, '', 'primary', 1, 0),
(72, 1, 'ai_kb_type', 'QA问答库', 'qa', 20, '', 'success', 0, 0),
(73, 1, 'ai_kb_type', '网页爬取', 'web', 30, '', 'warning', 0, 0),
(74, 1, 'ai_mcp_transport_type', 'SSE', 'sse', 10, '', 'primary', 1, 0),
(75, 1, 'ai_mcp_transport_type', 'Streamable HTTP', 'streamable_http', 20, '', 'success', 0, 0),
(76, 1, 'ai_mcp_transport_type', 'Stdio', 'stdio', 30, '', 'info', 0, 0),
(77, 1, 'ai_workflow_type', '简单对话', 'simple', 10, '', 'primary', 1, 0),
(78, 1, 'ai_workflow_type', '高级编排', 'advanced', 20, '', 'success', 0, 0),
(79, 1, 'ai_knowledge_index_status', '待处理', 'pending', 10, '', 'info', 0, 0),
(80, 1, 'ai_knowledge_index_status', '索引中', 'indexing', 20, '', 'warning', 0, 0),
(81, 1, 'ai_knowledge_index_status', '已完成', 'completed', 30, '', 'success', 0, 0),
(82, 1, 'ai_knowledge_index_status', '失败', 'failed', 40, '', 'danger', 0, 0);

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

-- 1. JobFlow 字段（service_name / handler）及其列注释已包含在上方 sys_job 建表语句中。

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

-- 【差异点 D】expire_time 是租户到期时间，MySQL 的 TIMESTAMP 上限是 2038-01-19，
-- 超过该时间点的到期日在 MySQL 上会直接写入报错，PostgreSQL 上不会。
-- 若业务确实需要 2038 年之后的到期日，需要把该列单独改成 DATETIME，
-- 这属于跨档结构变更，需主控统一决策，本文件不擅自改动。
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
-- 租户计费扩展表（MySQL）。
-- 【差异点 E】PostgreSQL 侧这两张表的索引写的是 CREATE INDEX IF NOT EXISTS，
-- MySQL 不支持该写法，索引改为并入建表语句内联声明，索引名保持一致。
-- 【差异点 G】表 / 列注释文本原样照搬 PostgreSQL 版（含英文原文），保证两边库内注释一致。
-- 【差异点 D】end_time 是订阅到期时间，超过 2038-01-19 在 MySQL 上会写入失败。

CREATE TABLE IF NOT EXISTS sys_tenant_subscription (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL COMMENT 'Tenant ID',
    package_id BIGINT NOT NULL COMMENT 'Package ID',
    start_time TIMESTAMP NOT NULL COMMENT 'Subscription start time',
    end_time TIMESTAMP NOT NULL COMMENT 'Subscription end time',
    status SMALLINT DEFAULT 0 COMMENT '0 active, 1 expired, 2 canceled',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT 'Subscription amount',
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
    amount DECIMAL(10,2) NOT NULL COMMENT 'Bill amount',
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
-- 【差异点 E】PostgreSQL 侧的 CREATE INDEX IF NOT EXISTS 统一改为建表内联索引。

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
-- PostgreSQL 侧用"冲突时跳过"兜底重复执行，MySQL 用 INSERT IGNORE 表达同一语义
-- （命中 uk_wf_category_code 时跳过该行，不报错）。
INSERT IGNORE INTO wf_category (tenant_id, category_code, category_name, parent_id, sort, status)
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
(1, 'invoice', '发票申请', 3, 2, 0);


-- ============================================================
-- source: postgres\open\00-schema.sql
-- ============================================================
-- 开放平台核心表（MySQL）。

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


-- ============================================================
-- source: postgres\ai\00-schema.sql
-- ============================================================
-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。
-- 类型映射：PostgreSQL 的 NUMERIC(p,s) 对应 MySQL 的 DECIMAL(p,s)（同一精度语义）；
-- INTEGER 统一写作 INT；TEXT 的默认值按【差异点 H】写成括号表达式。

CREATE TABLE ai_model (
    model_id        BIGINT          AUTO_INCREMENT PRIMARY KEY,
    model_name      VARCHAR(100)    NOT NULL,
    model_type      VARCHAR(20)     NOT NULL DEFAULT 'LLM',
    provider        VARCHAR(50)     NOT NULL DEFAULT 'openai',
    model_code      VARCHAR(100)    NOT NULL,
    base_url        VARCHAR(500)    NOT NULL,
    api_key         VARCHAR(500)    DEFAULT '',
    max_tokens      INT             DEFAULT 2048,
    temperature     DECIMAL(3,2)    DEFAULT 0.70,
    supports_vision CHAR(1)         DEFAULT '0',
    status          CHAR(1)         DEFAULT '0',
    remark          VARCHAR(500)    DEFAULT '',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI模型配置表';

-- =============================================
-- 24. 知识库表
-- =============================================
CREATE TABLE ai_knowledge_base (
    kb_id               BIGINT          AUTO_INCREMENT PRIMARY KEY,
    kb_name             VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    kb_type             VARCHAR(20)     NOT NULL DEFAULT 'general',
    embedding_model_id  BIGINT,
    document_count      INT             DEFAULT 0,
    paragraph_count     INT             DEFAULT 0,
    char_count          BIGINT          DEFAULT 0,
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='知识库表';

-- =============================================
-- 25. 知识库文档表
-- =============================================
CREATE TABLE ai_document (
    doc_id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    kb_id           BIGINT          NOT NULL,
    doc_name        VARCHAR(500)    NOT NULL,
    doc_type        VARCHAR(20)     DEFAULT 'txt',
    file_path       VARCHAR(1000)   DEFAULT '',
    file_size       BIGINT          DEFAULT 0,
    char_count      BIGINT          DEFAULT 0,
    paragraph_count INT             DEFAULT 0,
    index_status    VARCHAR(20)     DEFAULT 'pending',
    index_error     TEXT            DEFAULT (''),
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='知识库文档表';
CREATE INDEX idx_ai_document_kb_id ON ai_document(kb_id);

-- =============================================
-- 26. 知识库段落表
-- =============================================
-- 【差异点 F】embedding 在 PostgreSQL 版就是 TEXT（没有用 pgvector 扩展），
-- 所以这里 1:1 平移即可，不存在向量类型缺口；向量检索由应用层负责。
-- 一旦 PostgreSQL 侧改用向量类型，MySQL 8.4 没有等价类型可对齐（VECTOR 是 9.0 才有的能力）。
CREATE TABLE ai_paragraph (
    paragraph_id    BIGINT          AUTO_INCREMENT PRIMARY KEY,
    doc_id          BIGINT          NOT NULL,
    kb_id           BIGINT          NOT NULL,
    title           VARCHAR(500)    DEFAULT '',
    content         TEXT            NOT NULL,
    char_count      INT             DEFAULT 0,
    hit_count       INT             DEFAULT 0,
    embedding       TEXT,
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       INT             DEFAULT 0
) COMMENT='知识库段落表';
CREATE INDEX idx_ai_paragraph_doc ON ai_paragraph(doc_id);
CREATE INDEX idx_ai_paragraph_kb ON ai_paragraph(kb_id);

-- =============================================
-- 27. MCP服务器配置表
-- =============================================
CREATE TABLE ai_mcp_server (
    mcp_id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    server_name     VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000)   DEFAULT '',
    transport_type  VARCHAR(30)     NOT NULL DEFAULT 'sse',
    command         VARCHAR(500)    DEFAULT '',
    args            TEXT            DEFAULT ('[]'),
    env_vars        TEXT            DEFAULT ('{}'),
    url             VARCHAR(500)    DEFAULT '',
    tools           TEXT            DEFAULT ('[]'),
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='MCP服务器配置表';

-- =============================================
-- 28. AI工作流表
-- =============================================
CREATE TABLE ai_workflow (
    workflow_id         BIGINT          AUTO_INCREMENT PRIMARY KEY,
    workflow_name       VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    workflow_type       VARCHAR(20)     NOT NULL DEFAULT 'simple',
    model_id            BIGINT,
    knowledge_base_ids  TEXT            DEFAULT ('[]'),
    mcp_server_ids      TEXT            DEFAULT ('[]'),
    system_prompt       TEXT            DEFAULT (''),
    flow_config         TEXT            DEFAULT ('{}'),
    prologue            VARCHAR(2000)   DEFAULT '',
    suggested_questions TEXT            DEFAULT ('[]') COMMENT '开场推荐问题（JSON字符串数组，最多5条）',
    published           CHAR(1)         DEFAULT '0',
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI工作流表';

-- =============================================
-- 29. AI对话会话表
-- =============================================
CREATE TABLE ai_conversation (
    conversation_id BIGINT          AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(500)    DEFAULT '新对话',
    workflow_id     BIGINT,
    model_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    message_count   INT             DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI对话会话表';
CREATE INDEX idx_ai_conversation_user ON ai_conversation(user_id);

-- =============================================
-- 30. AI对话消息表
-- =============================================
CREATE TABLE ai_chat_message (
    message_id      BIGINT          AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    content         TEXT            NOT NULL,
    token_count     INT             DEFAULT 0,
    sort_order      INT             DEFAULT 0,
    images          TEXT,
    meta            TEXT,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI对话消息表';
CREATE INDEX idx_ai_chat_message_conversation ON ai_chat_message(conversation_id);
CREATE INDEX idx_ai_chat_message_tenant ON ai_chat_message(tenant_id);

-- =============================================
-- 31. AI智能体表
-- =============================================
CREATE TABLE ai_agent (
    agent_id       BIGINT          AUTO_INCREMENT PRIMARY KEY,
    agent_name     VARCHAR(100)    NOT NULL,
    description    TEXT,
    avatar         VARCHAR(500),
    system_prompt  TEXT,
    prologue       TEXT,
    suggested_questions TEXT       COMMENT '开场推荐问题（JSON字符串数组，最多5条）',
    model_id       BIGINT,
    knowledge_base_ids TEXT,
    mcp_server_ids TEXT,
    temperature    DECIMAL(3,2)    DEFAULT 0.7,
    max_tokens     INT             DEFAULT 2048,
    history_limit  INT             COMMENT '对话历史注入条数（NULL=默认12）',
    retrieval_top_k INT            COMMENT '知识库检索返回条数（NULL=默认5）',
    similarity_threshold DECIMAL(4,3) COMMENT '向量检索相似度阈值（NULL=默认0.30）',
    published      CHAR(1)         DEFAULT '0',
    share_key      VARCHAR(64)     COMMENT '公开分享链接 key（发布时生成，重置后旧链接失效）',
    status         CHAR(1)         DEFAULT '0',
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag       INT             DEFAULT 0
) COMMENT='AI智能体';

-- =============================================
-- 32. Prompt模板表
-- =============================================
-- 列宽以 sql/upgrades/postgres/phase8_prompt_template_alignment.sql 为权威口径，
-- 历史上 init 是 100/20/500、phase8 是 200/30/1000，两类环境结构长期分叉。
CREATE TABLE ai_prompt_template (
    template_id   BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
) COMMENT='Prompt模板表';
CREATE INDEX idx_prompt_tpl_tenant ON ai_prompt_template(tenant_id);

-- =============================================
-- 33. Token用量记录表
-- =============================================
CREATE TABLE ai_token_usage (
    usage_id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT,
    user_id           BIGINT,
    conversation_id   BIGINT,
    model_id          BIGINT,
    model_name        VARCHAR(100),
    prompt_tokens     INT             DEFAULT 0,
    completion_tokens INT             DEFAULT 0,
    total_tokens      INT             DEFAULT 0,
    create_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='AI Token用量记录表';
CREATE INDEX idx_token_usage_tenant ON ai_token_usage(tenant_id);
CREATE INDEX idx_token_usage_user ON ai_token_usage(user_id);
CREATE INDEX idx_token_usage_time ON ai_token_usage(create_time);

-- =============================================
-- 34. 知识图谱节点表
-- =============================================
CREATE TABLE ai_graph_node (
    node_id        BIGINT          AUTO_INCREMENT PRIMARY KEY,
    kb_id          BIGINT,
    node_name      VARCHAR(200)    NOT NULL,
    node_type      VARCHAR(50)     NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='知识图谱节点';
CREATE INDEX idx_graph_node_kb ON ai_graph_node(kb_id);
CREATE INDEX idx_graph_node_type ON ai_graph_node(node_type);

-- =============================================
-- 35. 知识图谱关系表
-- =============================================
CREATE TABLE ai_graph_edge (
    edge_id        BIGINT          AUTO_INCREMENT PRIMARY KEY,
    kb_id          BIGINT,
    source_node_id BIGINT          NOT NULL,
    target_node_id BIGINT          NOT NULL,
    relation_type  VARCHAR(100)    NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
) COMMENT='知识图谱关系';
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
-- 代码生成器元数据表迁移（MySQL）。
-- 【差异点 C/E】PostgreSQL 侧用匿名块补建外键、用 CREATE INDEX IF NOT EXISTS 补建索引，
-- MySQL 两者都不支持，统一在建表语句里内联声明，约束名与索引名保持一致。
-- 外键要求 gen_table 先于 gen_table_column 创建，本文件已保证该顺序。

CREATE TABLE IF NOT EXISTS gen_table (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    table_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'Database table name',
    table_comment VARCHAR(500) DEFAULT '' COMMENT 'Database table comment',
    package_name VARCHAR(200) DEFAULT 'com.han.system' COMMENT 'Generated package name',
    module_name VARCHAR(50) DEFAULT '' COMMENT 'Generated module name',
    business_name VARCHAR(50) DEFAULT '' COMMENT 'Generated business name',
    function_name VARCHAR(50) DEFAULT '' COMMENT 'Generated function label',
    author VARCHAR(50) DEFAULT 'HanCloud' COMMENT 'Generated author',
    parent_menu_id BIGINT DEFAULT NULL COMMENT 'Parent menu ID',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT NULL
) COMMENT='Code generator table metadata';

CREATE TABLE IF NOT EXISTS gen_table_column (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 0,
    table_id BIGINT NOT NULL COMMENT 'Related table ID',
    column_name VARCHAR(200) NOT NULL COMMENT 'Database column name',
    column_comment VARCHAR(500) DEFAULT '' COMMENT 'Database column comment',
    column_type VARCHAR(100) DEFAULT '' COMMENT 'Database column type',
    java_type VARCHAR(50) DEFAULT 'String' COMMENT 'Generated Java type',
    java_field VARCHAR(200) DEFAULT '' COMMENT 'Generated Java field',
    is_pk SMALLINT DEFAULT 0 COMMENT 'Whether the column is a primary key',
    is_increment SMALLINT DEFAULT 0 COMMENT 'Whether the column is auto increment',
    is_required SMALLINT DEFAULT 0 COMMENT 'Whether the column is required',
    is_insert SMALLINT DEFAULT 1 COMMENT 'Whether the column is included on insert',
    is_edit SMALLINT DEFAULT 1 COMMENT 'Whether the column is included on edit',
    is_list SMALLINT DEFAULT 1 COMMENT 'Whether the column is included on list view',
    is_query SMALLINT DEFAULT 0 COMMENT 'Whether the column is queryable',
    query_type VARCHAR(20) DEFAULT 'EQ' COMMENT 'Query operator',
    html_type VARCHAR(50) DEFAULT 'input' COMMENT 'Rendered form control',
    dict_type VARCHAR(200) DEFAULT '' COMMENT 'Bound dictionary type',
    sort INT DEFAULT 0 COMMENT 'Display order',
    INDEX idx_gen_table_column_table_id (table_id),
    CONSTRAINT fk_gen_table_column_table_id FOREIGN KEY (table_id) REFERENCES gen_table (id) ON DELETE CASCADE
) COMMENT='Code generator column metadata';


-- ============================================================
-- source: postgres\gen\10-seed.sql
-- ============================================================
-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。


-- ============================================================
-- AI 菜单归一化
-- ============================================================
-- 【差异点 C】PostgreSQL 版这里是一段 PL/pgSQL 匿名块：先按 perms 语义键找到
-- 「AI智能」目录和「Prompt模板」菜单（找不到就补建、ID 冲突就顺延），再把 4 个
-- Prompt 按钮权限挂到正确父节点上，最后给超管补授权。
-- MySQL 8.4 没有匿名过程块，只能建存储过程再调用再删除，代价与风险都高于收益。
--
-- 本文件是 MySQL 全新初始化入口，上面的 sys_menu 种子已经用固定 ID
-- （500 / 515 / 1221-1224）播出了同一份目标状态，sys_role_menu 也已把全部菜单
-- 授给超管，因此这里改写成等价的确定性幂等语句：按 perms 语义键纠正父子关系，
-- 再用忽略重复的方式补授权。在 clean 库上本段是空操作，只是把"目标状态"再确认一次。
--
-- 边界：语句里的父节点 ID（500 / 515）是全新初始化下的固定编号，不做动态探测；
-- 存量 MySQL 库的菜单归一需要另写 MySQL 升级脚本，本文件不承担
-- （MySQL 目前只支持 clean init，没有历史增量升级路径）。

UPDATE sys_menu
SET menu_name = 'Prompt模板',
    parent_id = 500,
    ancestors = '0,500',
    sort = 6,
    path = 'prompt',
    component = 'ai/prompt/index',
    query = NULL,
    menu_type = 'C',
    visible = 0,
    status = 0,
    icon = 'document',
    is_frame = 1,
    is_cache = 0
WHERE perms = 'ai:prompt:list';

UPDATE sys_menu
SET menu_name = 'Prompt模板查询', parent_id = 515, ancestors = '0,500,515', sort = 1,
    path = '', component = NULL, query = NULL, menu_type = 'F',
    visible = 0, status = 0, icon = '#', is_frame = 1, is_cache = 0
WHERE perms = 'ai:prompt:query';

UPDATE sys_menu
SET menu_name = 'Prompt模板新增', parent_id = 515, ancestors = '0,500,515', sort = 2,
    path = '', component = NULL, query = NULL, menu_type = 'F',
    visible = 0, status = 0, icon = '#', is_frame = 1, is_cache = 0
WHERE perms = 'ai:prompt:add';

UPDATE sys_menu
SET menu_name = 'Prompt模板编辑', parent_id = 515, ancestors = '0,500,515', sort = 3,
    path = '', component = NULL, query = NULL, menu_type = 'F',
    visible = 0, status = 0, icon = '#', is_frame = 1, is_cache = 0
WHERE perms = 'ai:prompt:edit';

UPDATE sys_menu
SET menu_name = 'Prompt模板删除', parent_id = 515, ancestors = '0,500,515', sort = 4,
    path = '', component = NULL, query = NULL, menu_type = 'F',
    visible = 0, status = 0, icon = '#', is_frame = 1, is_cache = 0
WHERE perms = 'ai:prompt:remove';

-- 超管补授权：AI 根目录 + Prompt 模板菜单及其按钮权限；
-- 主键冲突时跳过，等价于 PostgreSQL 版的冲突忽略写法。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE (r.id = 1 OR r.role_key IN ('admin', 'super_admin'))
  AND (
      (m.menu_type = 'M' AND m.path = 'ai')
      OR m.perms IN ('ai:prompt:list', 'ai:prompt:query', 'ai:prompt:add', 'ai:prompt:edit', 'ai:prompt:remove')
  );

-- 当前模块暂无独立初始化种子。
-- 如后续新增预置数据，请在本文件补充。
