-- Social login binding table for PostgreSQL.
CREATE TABLE IF NOT EXISTS sys_user_social (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    tenant_id       BIGINT,
    provider        VARCHAR(32) NOT NULL,
    open_id         VARCHAR(128) NOT NULL,
    access_token    VARCHAR(512) DEFAULT NULL,
    nickname        VARCHAR(100) DEFAULT NULL,
    avatar          VARCHAR(500) DEFAULT NULL,
    extra           TEXT DEFAULT NULL,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, open_id)
);

COMMENT ON TABLE sys_user_social IS 'social login binding table';
COMMENT ON COLUMN sys_user_social.user_id IS 'system user id';
COMMENT ON COLUMN sys_user_social.tenant_id IS 'tenant id';
COMMENT ON COLUMN sys_user_social.provider IS 'third party provider';
COMMENT ON COLUMN sys_user_social.open_id IS 'third party user unique id';
COMMENT ON COLUMN sys_user_social.access_token IS 'third party access token';
COMMENT ON COLUMN sys_user_social.nickname IS 'third party nickname';
COMMENT ON COLUMN sys_user_social.avatar IS 'third party avatar';
COMMENT ON COLUMN sys_user_social.extra IS 'extra JSON data';

CREATE INDEX IF NOT EXISTS idx_user_social_user_id ON sys_user_social(user_id);
CREATE INDEX IF NOT EXISTS idx_user_social_provider_openid ON sys_user_social(provider, open_id);
