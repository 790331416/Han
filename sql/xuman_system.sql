-- =============================================
-- XuMan Cloud 系统管理模块数据库脚本
-- 数据库：MySQL 8.0+
-- 编码：UTF-8
-- =============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 租户表
-- ----------------------------
DROP TABLE IF EXISTS sys_tenant;
CREATE TABLE sys_tenant (
    id              BIGINT          NOT NULL                    COMMENT '租户ID',
    tenant_name     VARCHAR(100)    NOT NULL                    COMMENT '租户名称',
    contact_name    VARCHAR(50)     DEFAULT NULL                COMMENT '联系人',
    contact_phone   VARCHAR(20)     DEFAULT NULL                COMMENT '联系电话',
    contact_email   VARCHAR(100)    DEFAULT NULL                COMMENT '联系邮箱',
    package_id      BIGINT          DEFAULT NULL                COMMENT '租户套餐ID',
    user_limit      INT             DEFAULT -1                  COMMENT '用户数量限制(-1不限制)',
    account_limit   INT             DEFAULT -1                  COMMENT '账号数量限制(-1不限制)',
    expire_time     DATETIME        DEFAULT NULL                COMMENT '过期时间',
    isolation_type  VARCHAR(20)     DEFAULT 'logical'           COMMENT '隔离类型(logical逻辑隔离/physical物理隔离/hybrid混合)',
    domain          VARCHAR(200)    DEFAULT NULL                COMMENT '绑定域名',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id),
    INDEX idx_tenant_name (tenant_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

-- ----------------------------
-- 2. 租户套餐表
-- ----------------------------
DROP TABLE IF EXISTS sys_tenant_package;
CREATE TABLE sys_tenant_package (
    id              BIGINT          NOT NULL                    COMMENT '套餐ID',
    package_name    VARCHAR(100)    NOT NULL                    COMMENT '套餐名称',
    menu_ids        TEXT            DEFAULT NULL                COMMENT '关联菜单ID(JSON数组)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户套餐表';

-- ----------------------------
-- 3. 部门表
-- ----------------------------
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id              BIGINT          NOT NULL                    COMMENT '部门ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    parent_id       BIGINT          DEFAULT 0                   COMMENT '父部门ID',
    ancestors       VARCHAR(500)    DEFAULT ''                  COMMENT '祖级列表',
    dept_name       VARCHAR(100)    NOT NULL                    COMMENT '部门名称',
    dept_code       VARCHAR(50)     DEFAULT NULL                COMMENT '部门编码',
    leader          VARCHAR(50)     DEFAULT NULL                COMMENT '负责人',
    phone           VARCHAR(20)     DEFAULT NULL                COMMENT '联系电话',
    email           VARCHAR(100)    DEFAULT NULL                COMMENT '邮箱',
    sort            INT             DEFAULT 0                   COMMENT '显示顺序',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- ----------------------------
-- 4. 用户表
-- ----------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL                    COMMENT '用户ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    dept_id         BIGINT          DEFAULT NULL                COMMENT '部门ID',
    username        VARCHAR(50)     NOT NULL                    COMMENT '用户账号',
    nickname        VARCHAR(50)     DEFAULT ''                  COMMENT '用户昵称',
    user_type       VARCHAR(10)     DEFAULT 'sys'               COMMENT '用户类型(sys系统用户 api接口用户)',
    email           VARCHAR(100)    DEFAULT ''                  COMMENT '邮箱',
    phone           VARCHAR(20)     DEFAULT ''                  COMMENT '手机号码',
    sex             TINYINT         DEFAULT 0                   COMMENT '性别(0未知 1男 2女)',
    avatar          VARCHAR(500)    DEFAULT ''                  COMMENT '头像地址',
    password        VARCHAR(200)    NOT NULL                    COMMENT '密码(BCrypt加密)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    login_ip        VARCHAR(128)    DEFAULT ''                  COMMENT '最后登录IP',
    login_time      DATETIME        DEFAULT NULL                COMMENT '最后登录时间',
    pwd_update_time DATETIME        DEFAULT NULL                COMMENT '密码最后更新时间',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_username_tenant (username, tenant_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_dept_id (dept_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 5. 岗位表
-- ----------------------------
DROP TABLE IF EXISTS sys_post;
CREATE TABLE sys_post (
    id              BIGINT          NOT NULL                    COMMENT '岗位ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    post_code       VARCHAR(50)     NOT NULL                    COMMENT '岗位编码',
    post_name       VARCHAR(100)    NOT NULL                    COMMENT '岗位名称',
    sort            INT             DEFAULT 0                   COMMENT '显示顺序',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_post_code_tenant (post_code, tenant_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位表';

-- ----------------------------
-- 6. 角色表
-- ----------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id              BIGINT          NOT NULL                    COMMENT '角色ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    role_name       VARCHAR(50)     NOT NULL                    COMMENT '角色名称',
    role_key        VARCHAR(50)     NOT NULL                    COMMENT '角色权限字符',
    role_sort       INT             DEFAULT 0                   COMMENT '显示顺序',
    data_scope      CHAR(1)         DEFAULT '1'                 COMMENT '数据范围(1全部 2自定义 3本部门 4本部门及以下 5仅本人)',
    menu_check_strictly     TINYINT DEFAULT 1                   COMMENT '菜单树选择项是否关联显示',
    dept_check_strictly     TINYINT DEFAULT 1                   COMMENT '部门树选择项是否关联显示',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_role_key_tenant (role_key, tenant_id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ----------------------------
-- 7. 菜单权限表
-- ----------------------------
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id              BIGINT          NOT NULL                    COMMENT '菜单ID',
    parent_id       BIGINT          DEFAULT 0                   COMMENT '父菜单ID',
    ancestors       VARCHAR(500)    DEFAULT ''                  COMMENT '祖级列表',
    menu_name       VARCHAR(100)    NOT NULL                    COMMENT '菜单名称',
    menu_type       CHAR(1)         NOT NULL                    COMMENT '菜单类型(M目录 C菜单 F按钮)',
    path            VARCHAR(200)    DEFAULT ''                  COMMENT '路由地址',
    component       VARCHAR(255)    DEFAULT NULL                COMMENT '组件路径',
    query           VARCHAR(255)    DEFAULT NULL                COMMENT '路由参数',
    perms           VARCHAR(200)    DEFAULT NULL                COMMENT '权限标识',
    icon            VARCHAR(100)    DEFAULT '#'                 COMMENT '菜单图标',
    sort            INT             DEFAULT 0                   COMMENT '显示顺序',
    visible         TINYINT         DEFAULT 0                   COMMENT '显示状态(0显示 1隐藏)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    is_frame        TINYINT         DEFAULT 1                   COMMENT '是否为外链(0是 1否)',
    is_cache        TINYINT         DEFAULT 0                   COMMENT '是否缓存(0缓存 1不缓存)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_perms (perms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

-- ----------------------------
-- 8. 用户和角色关联表
-- ----------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    role_id         BIGINT          NOT NULL                    COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和角色关联表';

-- ----------------------------
-- 9. 用户和岗位关联表
-- ----------------------------
DROP TABLE IF EXISTS sys_user_post;
CREATE TABLE sys_user_post (
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    post_id         BIGINT          NOT NULL                    COMMENT '岗位ID',
    PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和岗位关联表';

-- ----------------------------
-- 10. 角色和菜单关联表
-- ----------------------------
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
    role_id         BIGINT          NOT NULL                    COMMENT '角色ID',
    menu_id         BIGINT          NOT NULL                    COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和菜单关联表';

-- ----------------------------
-- 11. 角色和部门关联表(数据权限)
-- ----------------------------
DROP TABLE IF EXISTS sys_role_dept;
CREATE TABLE sys_role_dept (
    role_id         BIGINT          NOT NULL                    COMMENT '角色ID',
    dept_id         BIGINT          NOT NULL                    COMMENT '部门ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和部门关联表';

-- ----------------------------
-- 12. 字典类型表
-- ----------------------------
DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type (
    id              BIGINT          NOT NULL                    COMMENT '字典ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID(NULL表示系统级)',
    dict_name       VARCHAR(100)    NOT NULL                    COMMENT '字典名称',
    dict_type       VARCHAR(100)    NOT NULL                    COMMENT '字典类型',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_dict_type (dict_type, tenant_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- ----------------------------
-- 13. 字典数据表
-- ----------------------------
DROP TABLE IF EXISTS sys_dict_data;
CREATE TABLE sys_dict_data (
    id              BIGINT          NOT NULL                    COMMENT '字典数据ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID(NULL表示系统级)',
    dict_type       VARCHAR(100)    NOT NULL                    COMMENT '字典类型',
    dict_label      VARCHAR(100)    NOT NULL                    COMMENT '字典标签',
    dict_value      VARCHAR(100)    NOT NULL                    COMMENT '字典键值',
    dict_sort       INT             DEFAULT 0                   COMMENT '字典排序',
    css_class       VARCHAR(100)    DEFAULT NULL                COMMENT '样式属性(前端)',
    list_class      VARCHAR(100)    DEFAULT NULL                COMMENT '表格回显样式',
    is_default      TINYINT         DEFAULT 0                   COMMENT '是否默认(0否 1是)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_dict_type (dict_type),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';

-- ----------------------------
-- 14. 参数配置表
-- ----------------------------
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    id              BIGINT          NOT NULL                    COMMENT '参数ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID(NULL表示系统级)',
    config_name     VARCHAR(100)    NOT NULL                    COMMENT '参数名称',
    config_key      VARCHAR(100)    NOT NULL                    COMMENT '参数键名',
    config_value    VARCHAR(2000)   DEFAULT ''                  COMMENT '参数键值',
    config_type     CHAR(1)         DEFAULT 'N'                 COMMENT '系统内置(Y是 N否)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_config_key (config_key, tenant_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='参数配置表';

-- ----------------------------
-- 15. 通知公告表
-- ----------------------------
DROP TABLE IF EXISTS sys_notice;
CREATE TABLE sys_notice (
    id              BIGINT          NOT NULL                    COMMENT '公告ID',
    tenant_id       BIGINT          NOT NULL                    COMMENT '租户ID',
    notice_title    VARCHAR(100)    NOT NULL                    COMMENT '公告标题',
    notice_type     CHAR(1)         NOT NULL                    COMMENT '公告类型(1通知 2公告)',
    notice_content  LONGTEXT        DEFAULT NULL                COMMENT '公告内容',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1关闭)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_name     VARCHAR(50)     DEFAULT NULL                COMMENT '创建者名称',
    create_dept     BIGINT          DEFAULT NULL                COMMENT '创建部门',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_name     VARCHAR(50)     DEFAULT NULL                COMMENT '更新者名称',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知公告表';

-- ----------------------------
-- 16. 操作日志表
-- ----------------------------
DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '日志ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    title           VARCHAR(100)    DEFAULT ''                  COMMENT '模块标题',
    business_type   TINYINT         DEFAULT 0                   COMMENT '业务类型(0其它 1新增 2修改 3删除 4查询 5导出 6导入 7授权 8强退 9清空)',
    method          VARCHAR(200)    DEFAULT ''                  COMMENT '方法名称',
    request_method  VARCHAR(10)     DEFAULT ''                  COMMENT '请求方式',
    operator_type   TINYINT         DEFAULT 0                   COMMENT '操作类别(0其它 1后台 2手机)',
    oper_name       VARCHAR(50)     DEFAULT ''                  COMMENT '操作人员',
    dept_name       VARCHAR(100)    DEFAULT ''                  COMMENT '部门名称',
    oper_url        VARCHAR(500)    DEFAULT ''                  COMMENT '请求URL',
    oper_ip         VARCHAR(128)    DEFAULT ''                  COMMENT '操作IP',
    oper_location   VARCHAR(255)    DEFAULT ''                  COMMENT '操作地点',
    oper_param      TEXT            DEFAULT NULL                COMMENT '请求参数',
    json_result     TEXT            DEFAULT NULL                COMMENT '返回参数',
    status          TINYINT         DEFAULT 0                   COMMENT '操作状态(0正常 1异常)',
    error_msg       TEXT            DEFAULT NULL                COMMENT '错误消息',
    oper_time       DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '操作时间',
    cost_time       BIGINT          DEFAULT 0                   COMMENT '消耗时间(毫秒)',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_oper_time (oper_time),
    INDEX idx_business_type (business_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ----------------------------
-- 17. 登录日志表
-- ----------------------------
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT     COMMENT '日志ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    user_id         BIGINT          DEFAULT NULL                COMMENT '用户ID',
    username        VARCHAR(50)     DEFAULT ''                  COMMENT '用户账号',
    client_type     VARCHAR(20)     DEFAULT ''                  COMMENT '客户端类型(pc/app/wechat_mp/wechat_oa)',
    device_id       VARCHAR(100)    DEFAULT ''                  COMMENT '设备ID',
    ipaddr          VARCHAR(128)    DEFAULT ''                  COMMENT '登录IP',
    login_location  VARCHAR(255)    DEFAULT ''                  COMMENT '登录地点',
    browser         VARCHAR(100)    DEFAULT ''                  COMMENT '浏览器类型',
    os              VARCHAR(100)    DEFAULT ''                  COMMENT '操作系统',
    status          TINYINT         DEFAULT 0                   COMMENT '登录状态(0成功 1失败)',
    msg             VARCHAR(255)    DEFAULT ''                  COMMENT '提示消息',
    login_time      DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '登录时间',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_user_id (user_id),
    INDEX idx_username (username),
    INDEX idx_login_time (login_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- ----------------------------
-- 18. 在线用户表
-- ----------------------------
DROP TABLE IF EXISTS sys_user_online;
CREATE TABLE sys_user_online (
    id              VARCHAR(64)     NOT NULL                    COMMENT '会话ID(Token)',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    user_id         BIGINT          NOT NULL                    COMMENT '用户ID',
    username        VARCHAR(50)     DEFAULT ''                  COMMENT '用户账号',
    client_type     VARCHAR(20)     DEFAULT ''                  COMMENT '客户端类型',
    device_id       VARCHAR(100)    DEFAULT ''                  COMMENT '设备ID',
    ipaddr          VARCHAR(128)    DEFAULT ''                  COMMENT '登录IP',
    login_location  VARCHAR(255)    DEFAULT ''                  COMMENT '登录地点',
    browser         VARCHAR(100)    DEFAULT ''                  COMMENT '浏览器类型',
    os              VARCHAR(100)    DEFAULT ''                  COMMENT '操作系统',
    login_time      DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '登录时间',
    expire_time     DATETIME        DEFAULT NULL                COMMENT '过期时间',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线用户表';

-- ----------------------------
-- 19. 文件信息表
-- ----------------------------
DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file (
    id              BIGINT          NOT NULL                    COMMENT '文件ID',
    tenant_id       BIGINT          DEFAULT NULL                COMMENT '租户ID',
    file_name       VARCHAR(200)    NOT NULL                    COMMENT '原始文件名',
    file_path       VARCHAR(500)    NOT NULL                    COMMENT '文件存储路径',
    file_url        VARCHAR(500)    DEFAULT NULL                COMMENT '文件访问URL',
    file_size       BIGINT          DEFAULT 0                   COMMENT '文件大小(字节)',
    file_type       VARCHAR(50)     DEFAULT ''                  COMMENT '文件类型(后缀)',
    mime_type       VARCHAR(100)    DEFAULT ''                  COMMENT 'MIME类型',
    storage_type    VARCHAR(20)     DEFAULT 'local'             COMMENT '存储类型(local/minio/oss)',
    bucket          VARCHAR(100)    DEFAULT ''                  COMMENT '存储桶',
    md5             VARCHAR(64)     DEFAULT ''                  COMMENT '文件MD5',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    PRIMARY KEY (id),
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_md5 (md5),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';

-- ----------------------------
-- 20. 客户端配置表(多端鉴权)
-- ----------------------------
DROP TABLE IF EXISTS sys_client;
CREATE TABLE sys_client (
    id              BIGINT          NOT NULL                    COMMENT '客户端ID',
    client_key      VARCHAR(50)     NOT NULL                    COMMENT '客户端标识',
    client_secret   VARCHAR(200)    NOT NULL                    COMMENT '客户端密钥',
    client_type     VARCHAR(20)     NOT NULL                    COMMENT '客户端类型(pc/app/wechat_mp/wechat_oa/h5/api)',
    token_expire    INT             DEFAULT 1800                COMMENT 'Token有效期(秒)',
    refresh_expire  INT             DEFAULT 604800              COMMENT '刷新Token有效期(秒)',
    max_online      INT             DEFAULT 1                   COMMENT '最大同时在线数(0不限制)',
    kick_strategy   VARCHAR(20)     DEFAULT 'kick_old'          COMMENT '踢出策略(kick_old踢旧/reject_new拒新)',
    status          TINYINT         DEFAULT 0                   COMMENT '状态(0正常 1停用)',
    create_by       BIGINT          DEFAULT NULL                COMMENT '创建者',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL                COMMENT '更新者',
    update_time     DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        TINYINT         DEFAULT 0                   COMMENT '删除标志(0存在 1删除)',
    remark          VARCHAR(500)    DEFAULT NULL                COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_client_key (client_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户端配置表';

SET FOREIGN_KEY_CHECKS = 1;
