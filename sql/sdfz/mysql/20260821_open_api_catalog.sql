-- 开放接口授权目录：扫描/登记到目录后仍需管理员在应用中手工选择，不自动授权。
SET NAMES utf8mb4;
START TRANSACTION;

CREATE TABLE IF NOT EXISTS open_api_resource (
    id              BIGINT        NOT NULL PRIMARY KEY COMMENT '主键',
    resource_code   VARCHAR(100)  NOT NULL COMMENT '接口资源编码',
    resource_name   VARCHAR(100)  NOT NULL COMMENT '接口名称',
    category        VARCHAR(50)   NOT NULL COMMENT '接口分类',
    http_method     VARCHAR(10)   NOT NULL COMMENT '请求方法',
    path            VARCHAR(255)  NOT NULL COMMENT '开放路径',
    scope_code      VARCHAR(100)  NOT NULL COMMENT 'OAuth2 Scope',
    description     VARCHAR(500)  NULL COMMENT '接口说明',
    sensitivity     VARCHAR(20)   NOT NULL DEFAULT 'NORMAL' COMMENT '风险等级：NORMAL/SENSITIVE/CONTROL',
    status          SMALLINT      NOT NULL DEFAULT 0 COMMENT '状态：0启用，1停用',
    sort            INT           NOT NULL DEFAULT 0 COMMENT '排序',
    UNIQUE KEY uk_open_api_resource_code (resource_code),
    UNIQUE KEY uk_open_api_resource_path (http_method, path),
    KEY idx_open_api_resource_status (status, category, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='开放接口授权目录';

INSERT INTO open_api_resource
    (id, resource_code, resource_name, category, http_method, path, scope_code, description, sensitivity, status, sort)
VALUES
  (1, 'directory.teachers.read', '教师目录', '教育目录', 'GET', '/open/api/v1/directory/teachers', 'edu.teacher.read', '查询授权学校的教师目录', 'NORMAL', 0, 10),
  (2, 'directory.students.read', '学生目录', '教育目录', 'GET', '/open/api/v1/directory/students', 'edu.student.read', '查询授权学校的学生目录', 'NORMAL', 0, 20),
  (3, 'directory.devices.read', '设备目录', '教育目录', 'GET', '/open/api/v1/directory/devices', 'edu.device.read', '查询授权学校的设备目录', 'NORMAL', 0, 30)
ON DUPLICATE KEY UPDATE
  resource_name = VALUES(resource_name), category = VALUES(category), http_method = VALUES(http_method),
  path = VALUES(path), scope_code = VALUES(scope_code), description = VALUES(description),
  sensitivity = VALUES(sensitivity), status = VALUES(status), sort = VALUES(sort);

COMMIT;
