-- 按编码查询设备：删除旧数字校园无数据来源的空占位字段，仅保留当前真实设备目录字段。
SET @compact_device_schema = '{
  "type":"object",
  "required":["pk_id","device_code","device_name","org_id","org_name"],
  "properties":{
    "pk_id":{"type":"string","description":"当前设备目录主键"},
    "device_code":{"type":"string","description":"设备编码"},
    "device_name":{"type":"string","description":"设备名称"},
    "device_type":{"type":"string","description":"设备类型编码"},
    "device_type_name":{"type":"string","description":"设备类型名称"},
    "device_status":{"type":"integer","format":"int32","description":"设备状态"},
    "org_id":{"type":"string","description":"所属学校ID，可直接用于创建课程的 organId"},
    "org_name":{"type":"string","description":"所属学校名称"},
    "place_id":{"type":"string","description":"绑定教室ID；未绑定教室时不返回"},
    "place_name":{"type":"string","description":"绑定教室名称；未绑定教室时不返回"},
    "application_type":{"type":"string","description":"设备应用场景编码，多个用逗号分隔"},
    "application_type_name":{"type":"string","description":"设备应用场景名称"},
    "state":{"type":"string","description":"兼容状态字符串"}
  }
}';

SET @compact_device_example = '{
  "success":true,
  "message":"操作成功！",
  "code":200,
  "result":{
    "pk_id":"10001",
    "device_code":"DEVICE-TEST-001",
    "device_name":"示例互动终端",
    "device_type":"RECORDER",
    "device_type_name":"RECORDER",
    "device_status":0,
    "org_id":"1001",
    "org_name":"示例学校",
    "place_id":"2001",
    "place_name":"示例教室",
    "application_type":"RECORDER:LIVE",
    "application_type_name":"RECORDER:LIVE",
    "state":"0"
  },
  "timestamp":0
}';

UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = JSON_SET(
      version_row.openapi_json,
      '$.components.schemas.LegacyDevice', JSON_EXTRACT(@compact_device_schema, '$'),
      '$.paths."/open/api/v1/classroom/common/getDeviceInfoByDeviceCode".get.responses."200".content."application/json".example', JSON_EXTRACT(@compact_device_example, '$')
    ),
    version_row.response_examples_json = @compact_device_example,
    version_row.update_time = NOW()
WHERE resource_row.resource_code = 'classroom.device.read'
  AND version_row.version = 'v1'
  AND version_row.status = 1;

SELECT resource_row.resource_code,
       JSON_LENGTH(JSON_EXTRACT(version_row.openapi_json, '$.components.schemas.LegacyDevice.properties')) AS response_field_count
FROM open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
WHERE resource_row.resource_code = 'classroom.device.read'
  AND version_row.version = 'v1';
