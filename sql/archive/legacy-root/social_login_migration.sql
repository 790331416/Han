-- 社交登录绑定表
CREATE TABLE IF NOT EXISTS sys_user_social (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL COMMENT '系统用户ID',
    tenant_id       BIGINT COMMENT '租户ID',
    provider        VARCHAR(32) NOT NULL COMMENT '第三方平台（github/wechat/google）',
    open_id         VARCHAR(128) NOT NULL COMMENT '第三方平台用户唯一标识',
    access_token    VARCHAR(512) DEFAULT NULL COMMENT '第三方访问令牌',
    nickname        VARCHAR(100) DEFAULT NULL COMMENT '第三方昵称',
    avatar          VARCHAR(500) DEFAULT NULL COMMENT '第三方头像',
    extra           TEXT DEFAULT NULL COMMENT '扩展信息（JSON）',
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, open_id)
);

COMMENT ON TABLE sys_user_social IS '社交登录绑定表';
CREATE INDEX IF NOT EXISTS idx_user_social_user_id ON sys_user_social(user_id);
CREATE INDEX IF NOT EXISTS idx_user_social_provider_openid ON sys_user_social(provider, open_id);
