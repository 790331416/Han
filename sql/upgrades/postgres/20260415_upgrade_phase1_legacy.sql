-- =============================================
-- han Cloud 第一阶段升级 SQL（PostgreSQL）
-- 包含：操作日志表、登录日志表、通知公告表
-- =============================================

-- ----------------------------
-- 1. 操作日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT          PRIMARY KEY,
    tenant_id       BIGINT          DEFAULT NULL,
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
    oper_param      TEXT            DEFAULT NULL,
    json_result     TEXT            DEFAULT NULL,
    status          SMALLINT        DEFAULT 0,
    error_msg       TEXT            DEFAULT NULL,
    oper_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    cost_time       BIGINT          DEFAULT 0
);

COMMENT ON TABLE sys_oper_log IS '操作日志表';
DO $$
DECLARE
    v_column RECORD;
BEGIN
    FOR v_column IN
        SELECT * FROM (VALUES
            ('id', '日志ID'),
            ('tenant_id', '租户ID'),
            ('title', '模块标题'),
            ('module', '模块标题'),
            ('business_type', '业务类型(0其它 1新增 2修改 3删除 4查询 5导出 6导入 7授权 8强退 9清空)'),
            ('oper_type', '业务类型(0其它 1新增 2修改 3删除 4查询 5导出 6导入 7授权 8强退 9清空)'),
            ('method', '方法名称'),
            ('request_method', '请求方式'),
            ('operator_type', '操作类别(0其它 1后台 2手机)'),
            ('oper_name', '操作人员'),
            ('oper_user_id', '操作用户ID'),
            ('dept_name', '部门名称'),
            ('oper_url', '请求URL'),
            ('oper_ip', '操作IP'),
            ('oper_location', '操作地点'),
            ('oper_param', '请求参数'),
            ('json_result', '返回参数'),
            ('status', '操作状态(0正常 1异常)'),
            ('error_msg', '错误消息'),
            ('oper_time', '操作时间'),
            ('cost_time', '消耗时间(毫秒)')
        ) AS t(column_name, comment_text)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'sys_oper_log'
              AND column_name = v_column.column_name
        ) THEN
            EXECUTE format('COMMENT ON COLUMN sys_oper_log.%I IS %L', v_column.column_name, v_column.comment_text);
        END IF;
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS idx_oper_log_tenant_id ON sys_oper_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_oper_log_oper_time ON sys_oper_log(oper_time);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_oper_log'
          AND column_name = 'business_type'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_oper_log_business_type ON sys_oper_log(business_type);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_oper_log_status ON sys_oper_log(status);

-- ----------------------------
-- 2. 登录日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
    id              BIGINT          PRIMARY KEY,
    tenant_id       BIGINT          DEFAULT NULL,
    user_id         BIGINT          DEFAULT NULL,
    username        VARCHAR(50)     DEFAULT '',
    client_type     VARCHAR(20)     DEFAULT '',
    device_id       VARCHAR(100)    DEFAULT '',
    ipaddr          VARCHAR(128)    DEFAULT '',
    login_location  VARCHAR(255)    DEFAULT '',
    browser         VARCHAR(100)    DEFAULT '',
    os              VARCHAR(100)    DEFAULT '',
    status          SMALLINT        DEFAULT 0,
    msg             VARCHAR(255)    DEFAULT '',
    login_time      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'ipaddr'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'ip_addr'
    ) THEN
        ALTER TABLE sys_login_log RENAME COLUMN ipaddr TO ip_addr;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'msg'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'sys_login_log'
          AND column_name = 'message'
    ) THEN
        ALTER TABLE sys_login_log RENAME COLUMN msg TO message;
    END IF;
END $$;

ALTER TABLE sys_login_log
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS user_id BIGINT,
    ADD COLUMN IF NOT EXISTS username VARCHAR(50) DEFAULT '',
    ADD COLUMN IF NOT EXISTS client_type VARCHAR(20) DEFAULT '',
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(100) DEFAULT '',
    ADD COLUMN IF NOT EXISTS ip_addr VARCHAR(128) DEFAULT '',
    ADD COLUMN IF NOT EXISTS login_location VARCHAR(255) DEFAULT '',
    ADD COLUMN IF NOT EXISTS browser VARCHAR(100) DEFAULT '',
    ADD COLUMN IF NOT EXISTS os VARCHAR(100) DEFAULT '',
    ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS message VARCHAR(255) DEFAULT '',
    ADD COLUMN IF NOT EXISTS login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMENT ON TABLE sys_login_log IS '登录日志表';
COMMENT ON COLUMN sys_login_log.id IS '日志ID';
COMMENT ON COLUMN sys_login_log.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_login_log.user_id IS '用户ID';
COMMENT ON COLUMN sys_login_log.username IS '用户账号';
COMMENT ON COLUMN sys_login_log.client_type IS '客户端类型';
COMMENT ON COLUMN sys_login_log.device_id IS '设备ID';
COMMENT ON COLUMN sys_login_log.ip_addr IS '登录IP';
COMMENT ON COLUMN sys_login_log.login_location IS '登录地点';
COMMENT ON COLUMN sys_login_log.browser IS '浏览器类型';
COMMENT ON COLUMN sys_login_log.os IS '操作系统';
COMMENT ON COLUMN sys_login_log.status IS '登录状态(0成功 1失败)';
COMMENT ON COLUMN sys_login_log.message IS '提示消息';
COMMENT ON COLUMN sys_login_log.login_time IS '登录时间';

CREATE INDEX IF NOT EXISTS idx_login_log_tenant_id ON sys_login_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_login_log_user_id ON sys_login_log(user_id);
CREATE INDEX IF NOT EXISTS idx_login_log_username ON sys_login_log(username);
CREATE INDEX IF NOT EXISTS idx_login_log_login_time ON sys_login_log(login_time);

-- ----------------------------
-- 3. 通知公告表
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_notice (
    id              BIGINT          PRIMARY KEY,
    tenant_id       BIGINT          NOT NULL,
    notice_title    VARCHAR(100)    NOT NULL,
    notice_type     CHAR(1)         NOT NULL,
    notice_content  TEXT            DEFAULT NULL,
    status          SMALLINT        DEFAULT 0,
    create_by       BIGINT          DEFAULT NULL,
    create_name     VARCHAR(50)     DEFAULT NULL,
    create_dept     BIGINT          DEFAULT NULL,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT          DEFAULT NULL,
    update_name     VARCHAR(50)     DEFAULT NULL,
    update_time     TIMESTAMP       DEFAULT NULL,
    del_flag        SMALLINT        DEFAULT 0,
    remark          VARCHAR(500)    DEFAULT NULL
);

COMMENT ON TABLE sys_notice IS '通知公告表';
COMMENT ON COLUMN sys_notice.id IS '公告ID';
COMMENT ON COLUMN sys_notice.tenant_id IS '租户ID';
COMMENT ON COLUMN sys_notice.notice_title IS '公告标题';
COMMENT ON COLUMN sys_notice.notice_type IS '公告类型(1通知 2公告)';
COMMENT ON COLUMN sys_notice.notice_content IS '公告内容';
COMMENT ON COLUMN sys_notice.status IS '状态(0正常 1关闭)';

CREATE INDEX IF NOT EXISTS idx_notice_tenant_id ON sys_notice(tenant_id);
