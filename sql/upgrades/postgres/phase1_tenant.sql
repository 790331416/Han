-- =============================================
-- Phase 1 — 租户隔离核心 SQL 脚本
-- =============================================

-- 确认各业务表已有 tenant_id 列（BizEntity 继承链应已创建）
-- 如未创建，执行以下语句：
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_dept ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_post ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_notice ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

-- 为已有数据设置默认租户（租户ID = 1 为平台租户）
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_role SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_dept SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_post SET tenant_id = 1 WHERE tenant_id IS NULL;

-- 创建索引加速租户过滤
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_tenant ON sys_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_dept_tenant ON sys_dept(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_post_tenant ON sys_post(tenant_id);
