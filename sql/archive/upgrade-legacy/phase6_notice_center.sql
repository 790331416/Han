-- 通知中心闭环升级脚本
-- 目标：
-- 1. 增加用户通知已读状态表
-- 2. 为通知已读查询补齐索引

CREATE TABLE IF NOT EXISTS sys_notice_read (
    id              BIGINT          NOT NULL PRIMARY KEY,
    tenant_id       BIGINT          DEFAULT NULL,
    notice_id       BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    read_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag        SMALLINT        DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_notice_read_notice_user
    ON sys_notice_read (tenant_id, notice_id, user_id);

CREATE INDEX IF NOT EXISTS idx_sys_notice_read_user
    ON sys_notice_read (tenant_id, user_id, del_flag);

CREATE INDEX IF NOT EXISTS idx_sys_notice_read_notice
    ON sys_notice_read (tenant_id, notice_id, del_flag);
