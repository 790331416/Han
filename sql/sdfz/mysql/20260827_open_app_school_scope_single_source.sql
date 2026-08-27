-- 开放平台学校范围只由 open_app.school_scope 维护。
-- 接口授权仍负责资源、环境、状态和有效期，不再保存学校子范围。
UPDATE open_app_resource_grant
SET data_scope = NULL,
    update_time = NOW()
WHERE data_scope IS NOT NULL;

SELECT COUNT(*) AS remaining_resource_data_scope
FROM open_app_resource_grant
WHERE data_scope IS NOT NULL;
