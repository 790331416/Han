-- 密码策略字段迁移
-- 为用户表添加密码修改时间和重置标记字段

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_update_time TIMESTAMP DEFAULT NULL COMMENT '密码最后修改时间';
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_reset_flag SMALLINT DEFAULT 0 COMMENT '密码重置标记（1=需要修改密码）';

-- 初始化已有用户的密码修改时间为创建时间
UPDATE sys_user SET pwd_update_time = create_time WHERE pwd_update_time IS NULL;
