-- 三个已开放目录接口的可调测 OpenAPI 契约。
-- 仅更新已发布版本；不创建不存在的接口，不改变 Scope 或授权状态。
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE open_api_resource_version v
JOIN open_api_resource r ON r.id = v.resource_id
SET v.openapi_json = JSON_OBJECT(
  'openapi', '3.0.3',
  'info', JSON_OBJECT('title', r.resource_name, 'version', v.version),
  'paths', JSON_OBJECT(r.path, JSON_OBJECT('get', JSON_OBJECT(
    'summary', r.resource_name,
    'security', JSON_ARRAY(JSON_OBJECT('bearerAuth', JSON_ARRAY())),
    'parameters', JSON_ARRAY(
      JSON_OBJECT('name', 'schoolId', 'in', 'query', 'required', false, 'description', '授权学校 ID；为空时查询应用已授权学校。', 'schema', JSON_OBJECT('type', 'integer', 'format', 'int64', 'example', 1)),
      JSON_OBJECT('name', 'status', 'in', 'query', 'required', false, 'description', '状态过滤：0 启用、1 停用。', 'schema', JSON_OBJECT('type', 'integer', 'example', 0)),
      JSON_OBJECT('name', 'updatedAfter', 'in', 'query', 'required', false, 'description', '增量同步起始时间，ISO 8601 格式。', 'schema', JSON_OBJECT('type', 'string', 'format', 'date-time', 'example', '2026-08-01T00:00:00')),
      JSON_OBJECT('name', 'pageNum', 'in', 'query', 'required', false, 'description', '页码，默认 1。', 'schema', JSON_OBJECT('type', 'integer', 'minimum', 1, 'default', 1, 'example', 1)),
      JSON_OBJECT('name', 'pageSize', 'in', 'query', 'required', false, 'description', '每页数量，默认 20。', 'schema', JSON_OBJECT('type', 'integer', 'minimum', 1, 'default', 20, 'example', 20))
    ),
    'responses', JSON_OBJECT('200', JSON_OBJECT('description', '成功'))
  ))),
  'components', JSON_OBJECT('securitySchemes', JSON_OBJECT('bearerAuth', JSON_OBJECT('type', 'http', 'scheme', 'bearer')))
),
v.request_example_json = JSON_OBJECT('pageNum', 1, 'pageSize', 20),
v.response_examples_json = JSON_OBJECT('200', JSON_OBJECT('code', 200, 'msg', '操作成功', 'data', JSON_OBJECT('rows', JSON_ARRAY(), 'total', 0, 'pageNum', 1, 'pageSize', 20, 'pages', 0))),
v.error_examples_json = JSON_OBJECT('403', JSON_OBJECT('code', 403, 'msg', '应用未获该学校的数据授权', 'data', NULL))
WHERE r.resource_code IN ('directory.teachers.read', 'directory.students.read', 'directory.devices.read')
  AND v.status = 1
  AND v.del_flag = 0;

COMMIT;
