-- 开放平台 14 个视频课堂接口完整契约（幂等）。
-- 仅更新已存在、当前启用的 v1 资源版本；不建表、不插入资源、不变更授权。
SET NAMES utf8mb4;
START TRANSACTION;

-- 查询直播状态 (classroom.live-status.read)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 查询直播状态",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/course/deliveryClassroom/getLiveStatusByUUID": {
      "get": {
        "tags": [
          "视频课堂"
        ],
        "summary": "查询直播状态",
        "description": "按视频房间 ID 查询直播与录制状态。视频能力服务不可用时返回业务错误。",
        "operationId": "getLiveStatusByUUID",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.live.read"
            ]
          }
        ],
        "parameters": [
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "description": "视频能力平台返回的直播与录制状态；不同视频平台版本可追加字段",
                      "properties": {
                        "isLive": {
                          "type": "boolean",
                          "description": "房间当前是否直播"
                        }
                      },
                      "additionalProperties": true
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "isLive": true
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.live.read": "查询直播状态所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"roomId":"ROOM-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"isLive":true},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.live-status.read'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 查询应用升级信息 (classroom.app-upgrade.read)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 查询应用升级信息",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/user/tAppUpgrade/getAppUpgradeInfo": {
      "get": {
        "tags": [
          "视频课堂"
        ],
        "summary": "查询应用升级信息",
        "description": "按应用包名和当前版本号查询最新升级信息；无可用升级时 result 为 null。",
        "operationId": "getAppUpgradeInfo",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.app.read"
            ]
          }
        ],
        "parameters": [
          {
            "name": "appId",
            "in": "query",
            "required": true,
            "description": "应用包名",
            "schema": {
              "type": "string"
            },
            "example": "com.example.video"
          },
          {
            "name": "versionCode",
            "in": "query",
            "required": true,
            "description": "当前版本号，必须为整数字符串",
            "schema": {
              "type": "string"
            },
            "example": "1"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "有新版本时返回升级信息；已是最新版本时为空",
                      "properties": {
                        "pkId": {
                          "type": "integer",
                          "description": "升级记录主键",
                          "format": "int64"
                        },
                        "appId": {
                          "type": "string",
                          "description": "应用包名"
                        },
                        "versionCode": {
                          "type": "integer",
                          "description": "版本号",
                          "format": "int32"
                        },
                        "versionName": {
                          "type": "string",
                          "description": "版本名称"
                        },
                        "changeDesc": {
                          "type": "string",
                          "description": "升级说明"
                        },
                        "downloadUrl": {
                          "type": "string",
                          "description": "安装包下载地址",
                          "format": "uri"
                        },
                        "forceUpgrade": {
                          "type": "string",
                          "description": "是否强制升级：0 是，1 否",
                          "enum": [
                            "0",
                            "1"
                          ]
                        },
                        "createId": {
                          "type": "integer",
                          "description": "创建人 ID",
                          "format": "int64"
                        },
                        "createName": {
                          "type": "string",
                          "description": "创建人名称"
                        },
                        "createTime": {
                          "type": "string",
                          "description": "创建时间"
                        },
                        "updateId": {
                          "type": "integer",
                          "description": "更新人 ID",
                          "format": "int64",
                          "nullable": true
                        },
                        "updateName": {
                          "type": "string",
                          "description": "更新人名称",
                          "nullable": true
                        },
                        "updateTime": {
                          "type": "string",
                          "description": "更新时间",
                          "nullable": true
                        }
                      }
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "pkId": 1,
                    "appId": "com.example.video",
                    "versionCode": 2,
                    "versionName": "2.0.0",
                    "changeDesc": "修复已知问题",
                    "downloadUrl": "https://example.invalid/app.apk",
                    "forceUpgrade": "1",
                    "createId": 1,
                    "createName": "管理员",
                    "createTime": "2026-08-26 09:00:00",
                    "updateId": null,
                    "updateName": null,
                    "updateTime": null
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.app.read": "查询应用升级信息所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"appId":"com.example.video","versionCode":"1"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"pkId":1,"appId":"com.example.video","versionCode":2,"versionName":"2.0.0","changeDesc":"修复已知问题","downloadUrl":"https://example.invalid/app.apk","forceUpgrade":"1","createId":1,"createName":"管理员","createTime":"2026-08-26 09:00:00","updateId":null,"updateName":null,"updateTime":null},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.app-upgrade.read'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 查询课程列表 (classroom.course.list)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 查询课程列表",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/tb-course-info/getCourseInfoList": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "查询课程列表",
        "description": "按旧三个课堂查询条件分页检索课程。全部业务条件均放在查询字符串中；应用必须只授权一个学校范围。",
        "operationId": "getCourseInfoList",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.course.read"
            ]
          }
        ],
        "parameters": [
          {
            "name": "courseName",
            "in": "query",
            "required": false,
            "description": "课程名称，支持模糊查询",
            "schema": {
              "type": "string"
            },
            "example": "开放平台"
          },
          {
            "name": "courseType",
            "in": "query",
            "required": false,
            "description": "课程类型：1 专递课堂，2 名师课堂，3 名校网络课堂，4 视频会议，5 直播间",
            "schema": {
              "type": "string",
              "enum": [
                "1",
                "2",
                "3",
                "4",
                "5"
              ]
            },
            "example": "1"
          },
          {
            "name": "liveStatus",
            "in": "query",
            "required": false,
            "description": "直播状态：1 未开始，2 直播中，3 已结束",
            "schema": {
              "type": "string",
              "enum": [
                "1",
                "2",
                "3"
              ]
            },
            "example": "1"
          },
          {
            "name": "classId",
            "in": "query",
            "required": false,
            "description": "主讲教室或班级 ID",
            "schema": {
              "type": "string"
            },
            "example": "CLASS-001"
          },
          {
            "name": "className",
            "in": "query",
            "required": false,
            "description": "主讲教室或班级名称",
            "schema": {
              "type": "string"
            },
            "example": "示例主讲教室"
          },
          {
            "name": "teacherId",
            "in": "query",
            "required": false,
            "description": "排课教师 ID",
            "schema": {
              "type": "string"
            },
            "example": "TEACHER-001"
          },
          {
            "name": "teacherName",
            "in": "query",
            "required": false,
            "description": "排课教师名称",
            "schema": {
              "type": "string"
            },
            "example": "示例教师"
          },
          {
            "name": "timeBegin",
            "in": "query",
            "required": false,
            "description": "查询开始时间，格式 yyyy-MM-dd HH:mm:ss",
            "schema": {
              "type": "string"
            },
            "example": "2026-08-26 00:00:00"
          },
          {
            "name": "timeEnd",
            "in": "query",
            "required": false,
            "description": "查询结束时间，格式 yyyy-MM-dd HH:mm:ss",
            "schema": {
              "type": "string"
            },
            "example": "2026-08-26 23:59:59"
          },
          {
            "name": "stageCode",
            "in": "query",
            "required": false,
            "description": "学段编码",
            "schema": {
              "type": "string"
            },
            "example": "2"
          },
          {
            "name": "gradeCode",
            "in": "query",
            "required": false,
            "description": "年级编码",
            "schema": {
              "type": "string"
            },
            "example": "7"
          },
          {
            "name": "subjectCode",
            "in": "query",
            "required": false,
            "description": "学科编码",
            "schema": {
              "type": "string"
            },
            "example": "01"
          },
          {
            "name": "memberId",
            "in": "query",
            "required": false,
            "description": "主讲或听讲成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "PRESENTER-001"
          },
          {
            "name": "roomId",
            "in": "query",
            "required": false,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          },
          {
            "name": "viewAuth",
            "in": "query",
            "required": false,
            "description": "观看权限：0 私密，1 公开，2 登录，3 密码",
            "schema": {
              "type": "string",
              "enum": [
                "0",
                "1",
                "2",
                "3"
              ]
            },
            "example": "1"
          },
          {
            "name": "provinceCode",
            "in": "query",
            "required": false,
            "description": "省编码",
            "schema": {
              "type": "string"
            },
            "example": "500000"
          },
          {
            "name": "cityCode",
            "in": "query",
            "required": false,
            "description": "市编码",
            "schema": {
              "type": "string"
            },
            "example": "500100"
          },
          {
            "name": "countyCode",
            "in": "query",
            "required": false,
            "description": "区县编码",
            "schema": {
              "type": "string"
            },
            "example": "500103"
          },
          {
            "name": "organId",
            "in": "query",
            "required": false,
            "description": "学校或机构 ID；若传入必须属于应用授权学校",
            "schema": {
              "type": "string"
            },
            "example": "9026081001"
          },
          {
            "name": "isReview",
            "in": "query",
            "required": false,
            "description": "是否允许回看：1 是，2 否",
            "schema": {
              "type": "string",
              "enum": [
                "1",
                "2"
              ]
            },
            "example": "2"
          },
          {
            "name": "pageNum",
            "in": "query",
            "required": false,
            "description": "页码，从 1 开始",
            "schema": {
              "type": "integer",
              "format": "int32",
              "minimum": 1,
              "default": 1
            },
            "example": 1
          },
          {
            "name": "pageSize",
            "in": "query",
            "required": false,
            "description": "每页条数",
            "schema": {
              "type": "integer",
              "format": "int32",
              "minimum": 1,
              "default": 10
            },
            "example": 10
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "properties": {
                        "records": {
                          "type": "array",
                          "items": {
                            "$ref": "#/components/schemas/TbCourseInfo"
                          }
                        },
                        "total": {
                          "type": "integer",
                          "description": "总条数",
                          "format": "int64"
                        },
                        "size": {
                          "type": "integer",
                          "description": "每页条数",
                          "format": "int64"
                        },
                        "current": {
                          "type": "integer",
                          "description": "当前页码",
                          "format": "int64"
                        },
                        "pages": {
                          "type": "integer",
                          "description": "总页数",
                          "format": "int64"
                        }
                      }
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "records": [
                      {
                        "courseId": "COURSE-TEST-001",
                        "courseName": "开放平台示例课程",
                        "courseType": "1",
                        "courseDesc": "用于接口联调的示例课程",
                        "organId": "9026081001",
                        "organName": "示例学校",
                        "classId": "CLASS-001",
                        "className": "示例主讲教室",
                        "roomId": "ROOM-TEST-001",
                        "roomName": "开放平台示例课程的直播",
                        "memberId": "PRESENTER-001",
                        "memberName": "示例主讲人",
                        "teacherId": "TEACHER-001",
                        "teacherName": "示例教师",
                        "stageCode": "2",
                        "stageName": "初中",
                        "gradeCode": "7",
                        "gradeName": "七年级",
                        "subjectCode": "01",
                        "subjectName": "语文",
                        "timeBegin": "2026-08-26 09:00:00",
                        "timeEnd": "2026-08-26 09:40:00",
                        "streamType": "1",
                        "isLive": "1",
                        "isRecord": "2",
                        "isQualityCourse": "2",
                        "peopleNumber": 30,
                        "viewAuth": "1",
                        "reviewAuth": "1",
                        "liveStatus": "1",
                        "ruleId": "RULE-001",
                        "status": "0",
                        "tbCourseAttendList": [
                          {
                            "attendId": "ATTEND-001",
                            "fkCourseId": "COURSE-TEST-001",
                            "courseType": "1",
                            "organId": "9026081001",
                            "organName": "示例学校",
                            "classId": "CLASS-002",
                            "className": "示例听讲教室",
                            "roomId": "ROOM-TEST-001",
                            "roomName": "开放平台示例课程的直播",
                            "memberId": "LISTENER-001",
                            "memberName": "示例听讲成员",
                            "status": "0"
                          }
                        ]
                      }
                    ],
                    "total": 1,
                    "size": 10,
                    "current": 1,
                    "pages": 1
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.course.read": "查询课程列表所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      },
      "TbCourseInfo": {
        "type": "object",
        "required": [
          "courseName",
          "organId"
        ],
        "anyOf": [
          {
            "required": [
              "ruleId"
            ],
            "description": "直接指定课程节次规则"
          },
          {
            "required": [
              "timeBegin"
            ],
            "description": "未指定 ruleId 时由计划开始时间匹配节次"
          }
        ],
        "properties": {
          "courseId": {
            "type": "string",
            "description": "课程 ID；创建时由服务端生成",
            "readOnly": true
          },
          "courseName": {
            "type": "string",
            "description": "课程名称"
          },
          "courseType": {
            "type": "string",
            "description": "课程类型：1 专递课堂，2 名师课堂，3 名校网络课堂，4 视频会议，5 直播间",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "courseDesc": {
            "type": "string",
            "description": "课程描述"
          },
          "courseCover": {
            "type": "string",
            "description": "课程封面业务标识或地址"
          },
          "provinceCode": {
            "type": "string",
            "description": "省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "市编码"
          },
          "cityName": {
            "type": "string",
            "description": "市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "区县名称"
          },
          "organId": {
            "type": "string",
            "description": "主讲学校或机构 ID；必须属于应用授权学校范围"
          },
          "organName": {
            "type": "string",
            "description": "主讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "主讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "主讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "主讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "主讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID；通常在进入课程后由服务端写入"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "主讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "主讲成员名称"
          },
          "teacherId": {
            "type": "string",
            "description": "排课教师 ID"
          },
          "teacherName": {
            "type": "string",
            "description": "排课教师名称"
          },
          "stageCode": {
            "type": "string",
            "description": "学段编码"
          },
          "stageName": {
            "type": "string",
            "description": "学段名称"
          },
          "gradeCode": {
            "type": "string",
            "description": "年级编码"
          },
          "gradeName": {
            "type": "string",
            "description": "年级名称"
          },
          "subjectCode": {
            "type": "string",
            "description": "学科编码"
          },
          "subjectName": {
            "type": "string",
            "description": "学科名称"
          },
          "timeBegin": {
            "type": "string",
            "description": "计划开始时间，格式 yyyy-MM-dd HH:mm:ss；ruleId 为空时用于匹配节次"
          },
          "timeEnd": {
            "type": "string",
            "description": "计划结束时间，格式 yyyy-MM-dd HH:mm:ss"
          },
          "actualStartTime": {
            "type": "string",
            "description": "实际开始时间，由服务端维护",
            "readOnly": true
          },
          "actualEndTime": {
            "type": "string",
            "description": "实际结束时间，由服务端维护",
            "readOnly": true
          },
          "liveTime": {
            "type": "integer",
            "description": "直播时长，单位秒，由服务端维护",
            "format": "int64",
            "readOnly": true
          },
          "streamType": {
            "type": "string",
            "description": "流媒体类型"
          },
          "isLive": {
            "type": "string",
            "description": "是否平台直播：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isRecord": {
            "type": "string",
            "description": "是否平台录制：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isQualityCourse": {
            "type": "string",
            "description": "是否优质课：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "peopleNumber": {
            "type": "integer",
            "description": "预计人数",
            "format": "int32"
          },
          "viewAuth": {
            "type": "string",
            "description": "观看权限：0 私密，1 公开，2 登录，3 密码",
            "enum": [
              "0",
              "1",
              "2",
              "3"
            ]
          },
          "coursePassword": {
            "type": "string",
            "description": "课程观看密码；viewAuth=3 时使用"
          },
          "isReview": {
            "type": "string",
            "description": "是否允许回看：1 是，2 否；创建时服务端初始化为 2",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "reviewAuth": {
            "type": "string",
            "description": "回看权限：1 公开，2 登录，3 密码",
            "enum": [
              "1",
              "2",
              "3"
            ]
          },
          "reviewPassword": {
            "type": "string",
            "description": "回看密码；reviewAuth=3 时使用"
          },
          "liveStatus": {
            "type": "string",
            "description": "直播状态：1 未开始，2 直播中，3 已结束；创建时服务端初始化为 1",
            "enum": [
              "1",
              "2",
              "3"
            ],
            "readOnly": true
          },
          "ruleId": {
            "type": "string",
            "description": "课程节次规则 ID；为空时服务端按 timeBegin 匹配"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "time": {
            "type": "integer",
            "description": "兼容计算字段",
            "format": "int64",
            "readOnly": true
          },
          "courseCoverUrl": {
            "type": "string",
            "description": "课程封面完整访问地址",
            "readOnly": true
          },
          "tbCourseAttendList": {
            "type": "array",
            "description": "听讲端列表，可不传或传空数组",
            "items": {
              "$ref": "#/components/schemas/TbCourseAttend"
            }
          },
          "tbResourceFile": {
            "allOf": [
              {
                "$ref": "#/components/schemas/TbResourceFile"
              }
            ],
            "description": "兼容附件对象；当前创建课程流程不保存该对象"
          },
          "attendOrganId": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构 ID",
            "readOnly": true
          },
          "attendOrganName": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构名称",
            "readOnly": true
          },
          "attendClassId": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级 ID",
            "readOnly": true
          },
          "attendClassName": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级名称",
            "readOnly": true
          },
          "isMainTeach": {
            "type": "string",
            "description": "是否主讲端：1 主讲端，2 听讲端",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "dpTeacherName": {
            "type": "string",
            "description": "兼容展示字段：点评教师名称",
            "readOnly": true
          },
          "allowed": {
            "type": "string",
            "description": "兼容控制字段：1 表示踢出后禁止再次加入",
            "enum": [
              "0",
              "1"
            ]
          }
        }
      },
      "TbCourseAttend": {
        "type": "object",
        "properties": {
          "attendId": {
            "type": "string",
            "description": "听讲记录 ID；创建时由服务端生成",
            "readOnly": true
          },
          "fkCourseId": {
            "type": "string",
            "description": "关联课程 ID；创建时由服务端写入",
            "readOnly": true
          },
          "courseType": {
            "type": "string",
            "description": "课程类型；创建时继承主课程",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ],
            "readOnly": true
          },
          "provinceCode": {
            "type": "string",
            "description": "听讲端省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "听讲端省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "听讲端市编码"
          },
          "cityName": {
            "type": "string",
            "description": "听讲端市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "听讲端区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "听讲端区县名称"
          },
          "organId": {
            "type": "string",
            "description": "听讲学校或机构 ID"
          },
          "organName": {
            "type": "string",
            "description": "听讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "听讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "听讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "听讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "听讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "听讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "听讲成员名称"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由主课程 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由主课程 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "distance": {
            "type": "number",
            "format": "double",
            "description": "兼容距离计算字段",
            "readOnly": true
          }
        }
      },
      "TbResourceFile": {
        "type": "object",
        "properties": {
          "pkId": {
            "type": "integer",
            "description": "附件主键",
            "format": "int64"
          },
          "resourceId": {
            "type": "string",
            "description": "资源主键"
          },
          "fileName": {
            "type": "string",
            "description": "文件名称"
          },
          "fileType": {
            "type": "string",
            "description": "类型：1 文档，2 图片，3 音视频，4 资料，5 盒子录制回放",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "fileUrl": {
            "type": "string",
            "description": "文件访问地址"
          },
          "hanFileId": {
            "type": "integer",
            "description": "Han 统一文件服务 ID",
            "format": "int64"
          },
          "fileExt": {
            "type": "string",
            "description": "文件扩展名"
          },
          "fileSize": {
            "type": "string",
            "description": "文件大小"
          },
          "oldName": {
            "type": "string",
            "description": "原始文件名称"
          },
          "status": {
            "type": "string",
            "description": "状态"
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID"
          },
          "createName": {
            "type": "string",
            "description": "创建人名称"
          },
          "createTime": {
            "type": "string",
            "description": "创建时间"
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID"
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称"
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"courseName":"开放平台","courseType":"1","liveStatus":"1","classId":"CLASS-001","className":"示例主讲教室","teacherId":"TEACHER-001","teacherName":"示例教师","timeBegin":"2026-08-26 00:00:00","timeEnd":"2026-08-26 23:59:59","stageCode":"2","gradeCode":"7","subjectCode":"01","memberId":"PRESENTER-001","roomId":"ROOM-TEST-001","viewAuth":"1","provinceCode":"500000","cityCode":"500100","countyCode":"500103","organId":"9026081001","isReview":"2","pageNum":1,"pageSize":10}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"records":[{"courseId":"COURSE-TEST-001","courseName":"开放平台示例课程","courseType":"1","courseDesc":"用于接口联调的示例课程","organId":"9026081001","organName":"示例学校","classId":"CLASS-001","className":"示例主讲教室","roomId":"ROOM-TEST-001","roomName":"开放平台示例课程的直播","memberId":"PRESENTER-001","memberName":"示例主讲人","teacherId":"TEACHER-001","teacherName":"示例教师","stageCode":"2","stageName":"初中","gradeCode":"7","gradeName":"七年级","subjectCode":"01","subjectName":"语文","timeBegin":"2026-08-26 09:00:00","timeEnd":"2026-08-26 09:40:00","streamType":"1","isLive":"1","isRecord":"2","isQualityCourse":"2","peopleNumber":30,"viewAuth":"1","reviewAuth":"1","liveStatus":"1","ruleId":"RULE-001","status":"0","tbCourseAttendList":[{"attendId":"ATTEND-001","fkCourseId":"COURSE-TEST-001","courseType":"1","organId":"9026081001","organName":"示例学校","classId":"CLASS-002","className":"示例听讲教室","roomId":"ROOM-TEST-001","roomName":"开放平台示例课程的直播","memberId":"LISTENER-001","memberName":"示例听讲成员","status":"0"}]}],"total":1,"size":10,"current":1,"pages":1},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.course.list'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 创建课程 (classroom.course.save)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 创建课程",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/tb-course-info/saveCourseInfo": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "创建课程",
        "description": "按旧 TbCourseInfo JSON 契约创建课程及可选听讲端。organId 必须属于应用授权学校；courseId、状态和审计字段由服务端覆盖。",
        "operationId": "saveCourseInfo",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.course.write"
            ]
          }
        ],
        "requestBody": {
          "required": true,
          "description": "课程对象；courseName 与 organId 必填，并应提供 ruleId 或 timeBegin。",
          "content": {
            "application/json": {
              "schema": {
                "$ref": "#/components/schemas/TbCourseInfo"
              },
              "example": {
                "courseName": "开放平台联调课程",
                "courseType": "1",
                "courseDesc": "联调完成后请按业务流程清理",
                "courseCover": "COURSE-COVER-001",
                "provinceCode": "500000",
                "provinceName": "重庆市",
                "cityCode": "500100",
                "cityName": "重庆市",
                "countyCode": "500103",
                "countyName": "渝中区",
                "organId": "9026081001",
                "organName": "示例学校",
                "classId": "CLASS-001",
                "className": "示例主讲教室",
                "placeId": "PLACE-001",
                "placeName": "示例主讲场所",
                "memberId": "PRESENTER-001",
                "memberName": "示例主讲人",
                "teacherId": "TEACHER-001",
                "teacherName": "示例教师",
                "stageCode": "2",
                "stageName": "初中",
                "gradeCode": "7",
                "gradeName": "七年级",
                "subjectCode": "01",
                "subjectName": "语文",
                "timeBegin": "2026-08-26 09:00:00",
                "timeEnd": "2026-08-26 09:40:00",
                "streamType": "1",
                "isLive": "1",
                "isRecord": "2",
                "isQualityCourse": "2",
                "peopleNumber": 30,
                "viewAuth": "1",
                "coursePassword": "",
                "reviewAuth": "1",
                "reviewPassword": "",
                "ruleId": "RULE-001",
                "tbCourseAttendList": [
                  {
                    "provinceCode": "500000",
                    "provinceName": "重庆市",
                    "cityCode": "500100",
                    "cityName": "重庆市",
                    "countyCode": "500103",
                    "countyName": "渝中区",
                    "organId": "9026081001",
                    "organName": "示例学校",
                    "classId": "CLASS-002",
                    "className": "示例听讲教室",
                    "placeId": "PLACE-002",
                    "placeName": "示例听讲场所",
                    "memberId": "LISTENER-001",
                    "memberName": "示例听讲成员"
                  }
                ],
                "tbResourceFile": {
                  "resourceId": "RESOURCE-001",
                  "fileName": "课程资料.pdf",
                  "fileType": "1",
                  "fileUrl": "https://example.invalid/course.pdf",
                  "fileExt": "pdf",
                  "fileSize": "102400",
                  "oldName": "课程资料.pdf",
                  "status": "0"
                },
                "allowed": "0"
              }
            }
          }
        },
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.course.write": "创建课程所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      },
      "TbCourseInfo": {
        "type": "object",
        "required": [
          "courseName",
          "organId"
        ],
        "anyOf": [
          {
            "required": [
              "ruleId"
            ],
            "description": "直接指定课程节次规则"
          },
          {
            "required": [
              "timeBegin"
            ],
            "description": "未指定 ruleId 时由计划开始时间匹配节次"
          }
        ],
        "properties": {
          "courseId": {
            "type": "string",
            "description": "课程 ID；创建时由服务端生成",
            "readOnly": true
          },
          "courseName": {
            "type": "string",
            "description": "课程名称"
          },
          "courseType": {
            "type": "string",
            "description": "课程类型：1 专递课堂，2 名师课堂，3 名校网络课堂，4 视频会议，5 直播间",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "courseDesc": {
            "type": "string",
            "description": "课程描述"
          },
          "courseCover": {
            "type": "string",
            "description": "课程封面业务标识或地址"
          },
          "provinceCode": {
            "type": "string",
            "description": "省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "市编码"
          },
          "cityName": {
            "type": "string",
            "description": "市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "区县名称"
          },
          "organId": {
            "type": "string",
            "description": "主讲学校或机构 ID；必须属于应用授权学校范围"
          },
          "organName": {
            "type": "string",
            "description": "主讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "主讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "主讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "主讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "主讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID；通常在进入课程后由服务端写入"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "主讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "主讲成员名称"
          },
          "teacherId": {
            "type": "string",
            "description": "排课教师 ID"
          },
          "teacherName": {
            "type": "string",
            "description": "排课教师名称"
          },
          "stageCode": {
            "type": "string",
            "description": "学段编码"
          },
          "stageName": {
            "type": "string",
            "description": "学段名称"
          },
          "gradeCode": {
            "type": "string",
            "description": "年级编码"
          },
          "gradeName": {
            "type": "string",
            "description": "年级名称"
          },
          "subjectCode": {
            "type": "string",
            "description": "学科编码"
          },
          "subjectName": {
            "type": "string",
            "description": "学科名称"
          },
          "timeBegin": {
            "type": "string",
            "description": "计划开始时间，格式 yyyy-MM-dd HH:mm:ss；ruleId 为空时用于匹配节次"
          },
          "timeEnd": {
            "type": "string",
            "description": "计划结束时间，格式 yyyy-MM-dd HH:mm:ss"
          },
          "actualStartTime": {
            "type": "string",
            "description": "实际开始时间，由服务端维护",
            "readOnly": true
          },
          "actualEndTime": {
            "type": "string",
            "description": "实际结束时间，由服务端维护",
            "readOnly": true
          },
          "liveTime": {
            "type": "integer",
            "description": "直播时长，单位秒，由服务端维护",
            "format": "int64",
            "readOnly": true
          },
          "streamType": {
            "type": "string",
            "description": "流媒体类型"
          },
          "isLive": {
            "type": "string",
            "description": "是否平台直播：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isRecord": {
            "type": "string",
            "description": "是否平台录制：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isQualityCourse": {
            "type": "string",
            "description": "是否优质课：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "peopleNumber": {
            "type": "integer",
            "description": "预计人数",
            "format": "int32"
          },
          "viewAuth": {
            "type": "string",
            "description": "观看权限：0 私密，1 公开，2 登录，3 密码",
            "enum": [
              "0",
              "1",
              "2",
              "3"
            ]
          },
          "coursePassword": {
            "type": "string",
            "description": "课程观看密码；viewAuth=3 时使用"
          },
          "isReview": {
            "type": "string",
            "description": "是否允许回看：1 是，2 否；创建时服务端初始化为 2",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "reviewAuth": {
            "type": "string",
            "description": "回看权限：1 公开，2 登录，3 密码",
            "enum": [
              "1",
              "2",
              "3"
            ]
          },
          "reviewPassword": {
            "type": "string",
            "description": "回看密码；reviewAuth=3 时使用"
          },
          "liveStatus": {
            "type": "string",
            "description": "直播状态：1 未开始，2 直播中，3 已结束；创建时服务端初始化为 1",
            "enum": [
              "1",
              "2",
              "3"
            ],
            "readOnly": true
          },
          "ruleId": {
            "type": "string",
            "description": "课程节次规则 ID；为空时服务端按 timeBegin 匹配"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "time": {
            "type": "integer",
            "description": "兼容计算字段",
            "format": "int64",
            "readOnly": true
          },
          "courseCoverUrl": {
            "type": "string",
            "description": "课程封面完整访问地址",
            "readOnly": true
          },
          "tbCourseAttendList": {
            "type": "array",
            "description": "听讲端列表，可不传或传空数组",
            "items": {
              "$ref": "#/components/schemas/TbCourseAttend"
            }
          },
          "tbResourceFile": {
            "allOf": [
              {
                "$ref": "#/components/schemas/TbResourceFile"
              }
            ],
            "description": "兼容附件对象；当前创建课程流程不保存该对象"
          },
          "attendOrganId": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构 ID",
            "readOnly": true
          },
          "attendOrganName": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构名称",
            "readOnly": true
          },
          "attendClassId": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级 ID",
            "readOnly": true
          },
          "attendClassName": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级名称",
            "readOnly": true
          },
          "isMainTeach": {
            "type": "string",
            "description": "是否主讲端：1 主讲端，2 听讲端",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "dpTeacherName": {
            "type": "string",
            "description": "兼容展示字段：点评教师名称",
            "readOnly": true
          },
          "allowed": {
            "type": "string",
            "description": "兼容控制字段：1 表示踢出后禁止再次加入",
            "enum": [
              "0",
              "1"
            ]
          }
        }
      },
      "TbCourseAttend": {
        "type": "object",
        "properties": {
          "attendId": {
            "type": "string",
            "description": "听讲记录 ID；创建时由服务端生成",
            "readOnly": true
          },
          "fkCourseId": {
            "type": "string",
            "description": "关联课程 ID；创建时由服务端写入",
            "readOnly": true
          },
          "courseType": {
            "type": "string",
            "description": "课程类型；创建时继承主课程",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ],
            "readOnly": true
          },
          "provinceCode": {
            "type": "string",
            "description": "听讲端省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "听讲端省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "听讲端市编码"
          },
          "cityName": {
            "type": "string",
            "description": "听讲端市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "听讲端区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "听讲端区县名称"
          },
          "organId": {
            "type": "string",
            "description": "听讲学校或机构 ID"
          },
          "organName": {
            "type": "string",
            "description": "听讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "听讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "听讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "听讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "听讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "听讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "听讲成员名称"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由主课程 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由主课程 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "distance": {
            "type": "number",
            "format": "double",
            "description": "兼容距离计算字段",
            "readOnly": true
          }
        }
      },
      "TbResourceFile": {
        "type": "object",
        "properties": {
          "pkId": {
            "type": "integer",
            "description": "附件主键",
            "format": "int64"
          },
          "resourceId": {
            "type": "string",
            "description": "资源主键"
          },
          "fileName": {
            "type": "string",
            "description": "文件名称"
          },
          "fileType": {
            "type": "string",
            "description": "类型：1 文档，2 图片，3 音视频，4 资料，5 盒子录制回放",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "fileUrl": {
            "type": "string",
            "description": "文件访问地址"
          },
          "hanFileId": {
            "type": "integer",
            "description": "Han 统一文件服务 ID",
            "format": "int64"
          },
          "fileExt": {
            "type": "string",
            "description": "文件扩展名"
          },
          "fileSize": {
            "type": "string",
            "description": "文件大小"
          },
          "oldName": {
            "type": "string",
            "description": "原始文件名称"
          },
          "status": {
            "type": "string",
            "description": "状态"
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID"
          },
          "createName": {
            "type": "string",
            "description": "创建人名称"
          },
          "createTime": {
            "type": "string",
            "description": "创建时间"
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID"
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称"
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"courseName":"开放平台联调课程","courseType":"1","courseDesc":"联调完成后请按业务流程清理","courseCover":"COURSE-COVER-001","provinceCode":"500000","provinceName":"重庆市","cityCode":"500100","cityName":"重庆市","countyCode":"500103","countyName":"渝中区","organId":"9026081001","organName":"示例学校","classId":"CLASS-001","className":"示例主讲教室","placeId":"PLACE-001","placeName":"示例主讲场所","memberId":"PRESENTER-001","memberName":"示例主讲人","teacherId":"TEACHER-001","teacherName":"示例教师","stageCode":"2","stageName":"初中","gradeCode":"7","gradeName":"七年级","subjectCode":"01","subjectName":"语文","timeBegin":"2026-08-26 09:00:00","timeEnd":"2026-08-26 09:40:00","streamType":"1","isLive":"1","isRecord":"2","isQualityCourse":"2","peopleNumber":30,"viewAuth":"1","coursePassword":"","reviewAuth":"1","reviewPassword":"","ruleId":"RULE-001","tbCourseAttendList":[{"provinceCode":"500000","provinceName":"重庆市","cityCode":"500100","cityName":"重庆市","countyCode":"500103","countyName":"渝中区","organId":"9026081001","organName":"示例学校","classId":"CLASS-002","className":"示例听讲教室","placeId":"PLACE-002","placeName":"示例听讲场所","memberId":"LISTENER-001","memberName":"示例听讲成员"}],"tbResourceFile":{"resourceId":"RESOURCE-001","fileName":"课程资料.pdf","fileType":"1","fileUrl":"https://example.invalid/course.pdf","fileExt":"pdf","fileSize":"102400","oldName":"课程资料.pdf","status":"0"},"allowed":"0"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.course.save'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 开始课堂 (classroom.live.start)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 开始课堂",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/startClassroom": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "开始课堂",
        "description": "启动主讲课堂。liveType=1 为设备端，liveType=2 为网页端；参数均放在查询字符串中。",
        "operationId": "startClassroom",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.live.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "主讲成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "PRESENTER-001"
          },
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          },
          {
            "name": "name",
            "in": "query",
            "required": true,
            "description": "主讲成员显示名称",
            "schema": {
              "type": "string"
            },
            "example": "示例主讲人"
          },
          {
            "name": "liveType",
            "in": "query",
            "required": true,
            "description": "接入类型：1 设备端，2 网页端",
            "schema": {
              "type": "string",
              "enum": [
                "1",
                "2"
              ]
            },
            "example": "2"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "oneOf": [
                        {
                          "type": "string",
                          "description": "liveType=2 时返回 WebRTC 进入地址"
                        },
                        {
                          "type": "object",
                          "description": "liveType=1 时返回设备接入参数",
                          "properties": {
                            "token": {
                              "type": "string",
                              "description": "视频平台临时 Token"
                            },
                            "url": {
                              "type": "string",
                              "description": "视频平台信令地址"
                            },
                            "roomId": {
                              "type": "string",
                              "description": "视频房间 ID"
                            },
                            "role": {
                              "type": "string",
                              "description": "成员角色：1 主讲，2 听讲",
                              "enum": [
                                "1",
                                "2"
                              ]
                            },
                            "courseId": {
                              "type": "string",
                              "description": "课程 ID；设备听讲成功时返回"
                            },
                            "memberId": {
                              "type": "string",
                              "description": "主讲成员 ID；设备听讲成功时返回"
                            }
                          }
                        }
                      ]
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": "wss://video.example.invalid/webrtc/?token=<video-token>",
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.live.control": "开始课堂所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"memberId":"PRESENTER-001","roomId":"ROOM-TEST-001","name":"示例主讲人","liveType":"2"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":"wss://video.example.invalid/webrtc/?token=<video-token>","timestamp":0}'
WHERE resource_row.resource_code = 'classroom.live.start'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 加入课堂 (classroom.live.join)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 加入课堂",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/joinClassroom": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "加入课堂",
        "description": "听讲成员加入课堂。liveType=1 为设备端，liveType=2 为网页端；参数均放在查询字符串中。",
        "operationId": "joinClassroom",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.live.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "听讲成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "LISTENER-001"
          },
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          },
          {
            "name": "name",
            "in": "query",
            "required": true,
            "description": "听讲成员显示名称",
            "schema": {
              "type": "string"
            },
            "example": "示例听讲成员"
          },
          {
            "name": "liveType",
            "in": "query",
            "required": true,
            "description": "接入类型：1 设备端，2 网页端",
            "schema": {
              "type": "string",
              "enum": [
                "1",
                "2"
              ]
            },
            "example": "2"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "oneOf": [
                        {
                          "type": "string",
                          "description": "liveType=2 时返回 WebRTC 进入地址"
                        },
                        {
                          "type": "object",
                          "description": "liveType=1 时返回设备接入参数",
                          "properties": {
                            "token": {
                              "type": "string",
                              "description": "视频平台临时 Token"
                            },
                            "url": {
                              "type": "string",
                              "description": "视频平台信令地址"
                            },
                            "roomId": {
                              "type": "string",
                              "description": "视频房间 ID"
                            },
                            "role": {
                              "type": "string",
                              "description": "成员角色：1 主讲，2 听讲",
                              "enum": [
                                "1",
                                "2"
                              ]
                            },
                            "courseId": {
                              "type": "string",
                              "description": "课程 ID；设备听讲成功时返回"
                            },
                            "memberId": {
                              "type": "string",
                              "description": "主讲成员 ID；设备听讲成功时返回"
                            }
                          }
                        }
                      ]
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": "wss://video.example.invalid/webrtc/?token=<video-token>",
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.live.control": "加入课堂所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"memberId":"LISTENER-001","roomId":"ROOM-TEST-001","name":"示例听讲成员","liveType":"2"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":"wss://video.example.invalid/webrtc/?token=<video-token>","timestamp":0}'
WHERE resource_row.resource_code = 'classroom.live.join'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 进入课程 (classroom.live.enter)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 进入课程",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/enterCourse": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "进入课程",
        "description": "按课程 ID 进入课程；若课程尚无视频房间，服务端先创建并回写房间信息。",
        "operationId": "enterCourse",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.live.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "courseId",
            "in": "query",
            "required": true,
            "description": "课程 ID",
            "schema": {
              "type": "string"
            },
            "example": "COURSE-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "$ref": "#/components/schemas/TbCourseInfo"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "courseId": "COURSE-TEST-001",
                    "courseName": "开放平台示例课程",
                    "courseType": "1",
                    "courseDesc": "用于接口联调的示例课程",
                    "organId": "9026081001",
                    "organName": "示例学校",
                    "classId": "CLASS-001",
                    "className": "示例主讲教室",
                    "roomId": "ROOM-TEST-001",
                    "roomName": "开放平台示例课程的直播",
                    "memberId": "PRESENTER-001",
                    "memberName": "示例主讲人",
                    "teacherId": "TEACHER-001",
                    "teacherName": "示例教师",
                    "stageCode": "2",
                    "stageName": "初中",
                    "gradeCode": "7",
                    "gradeName": "七年级",
                    "subjectCode": "01",
                    "subjectName": "语文",
                    "timeBegin": "2026-08-26 09:00:00",
                    "timeEnd": "2026-08-26 09:40:00",
                    "streamType": "1",
                    "isLive": "1",
                    "isRecord": "2",
                    "isQualityCourse": "2",
                    "peopleNumber": 30,
                    "viewAuth": "1",
                    "reviewAuth": "1",
                    "liveStatus": "1",
                    "ruleId": "RULE-001",
                    "status": "0",
                    "tbCourseAttendList": [
                      {
                        "attendId": "ATTEND-001",
                        "fkCourseId": "COURSE-TEST-001",
                        "courseType": "1",
                        "organId": "9026081001",
                        "organName": "示例学校",
                        "classId": "CLASS-002",
                        "className": "示例听讲教室",
                        "roomId": "ROOM-TEST-001",
                        "roomName": "开放平台示例课程的直播",
                        "memberId": "LISTENER-001",
                        "memberName": "示例听讲成员",
                        "status": "0"
                      }
                    ]
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.live.control": "进入课程所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      },
      "TbCourseInfo": {
        "type": "object",
        "required": [
          "courseName",
          "organId"
        ],
        "anyOf": [
          {
            "required": [
              "ruleId"
            ],
            "description": "直接指定课程节次规则"
          },
          {
            "required": [
              "timeBegin"
            ],
            "description": "未指定 ruleId 时由计划开始时间匹配节次"
          }
        ],
        "properties": {
          "courseId": {
            "type": "string",
            "description": "课程 ID；创建时由服务端生成",
            "readOnly": true
          },
          "courseName": {
            "type": "string",
            "description": "课程名称"
          },
          "courseType": {
            "type": "string",
            "description": "课程类型：1 专递课堂，2 名师课堂，3 名校网络课堂，4 视频会议，5 直播间",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "courseDesc": {
            "type": "string",
            "description": "课程描述"
          },
          "courseCover": {
            "type": "string",
            "description": "课程封面业务标识或地址"
          },
          "provinceCode": {
            "type": "string",
            "description": "省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "市编码"
          },
          "cityName": {
            "type": "string",
            "description": "市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "区县名称"
          },
          "organId": {
            "type": "string",
            "description": "主讲学校或机构 ID；必须属于应用授权学校范围"
          },
          "organName": {
            "type": "string",
            "description": "主讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "主讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "主讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "主讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "主讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID；通常在进入课程后由服务端写入"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "主讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "主讲成员名称"
          },
          "teacherId": {
            "type": "string",
            "description": "排课教师 ID"
          },
          "teacherName": {
            "type": "string",
            "description": "排课教师名称"
          },
          "stageCode": {
            "type": "string",
            "description": "学段编码"
          },
          "stageName": {
            "type": "string",
            "description": "学段名称"
          },
          "gradeCode": {
            "type": "string",
            "description": "年级编码"
          },
          "gradeName": {
            "type": "string",
            "description": "年级名称"
          },
          "subjectCode": {
            "type": "string",
            "description": "学科编码"
          },
          "subjectName": {
            "type": "string",
            "description": "学科名称"
          },
          "timeBegin": {
            "type": "string",
            "description": "计划开始时间，格式 yyyy-MM-dd HH:mm:ss；ruleId 为空时用于匹配节次"
          },
          "timeEnd": {
            "type": "string",
            "description": "计划结束时间，格式 yyyy-MM-dd HH:mm:ss"
          },
          "actualStartTime": {
            "type": "string",
            "description": "实际开始时间，由服务端维护",
            "readOnly": true
          },
          "actualEndTime": {
            "type": "string",
            "description": "实际结束时间，由服务端维护",
            "readOnly": true
          },
          "liveTime": {
            "type": "integer",
            "description": "直播时长，单位秒，由服务端维护",
            "format": "int64",
            "readOnly": true
          },
          "streamType": {
            "type": "string",
            "description": "流媒体类型"
          },
          "isLive": {
            "type": "string",
            "description": "是否平台直播：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isRecord": {
            "type": "string",
            "description": "是否平台录制：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "isQualityCourse": {
            "type": "string",
            "description": "是否优质课：1 是，2 否",
            "enum": [
              "1",
              "2"
            ]
          },
          "peopleNumber": {
            "type": "integer",
            "description": "预计人数",
            "format": "int32"
          },
          "viewAuth": {
            "type": "string",
            "description": "观看权限：0 私密，1 公开，2 登录，3 密码",
            "enum": [
              "0",
              "1",
              "2",
              "3"
            ]
          },
          "coursePassword": {
            "type": "string",
            "description": "课程观看密码；viewAuth=3 时使用"
          },
          "isReview": {
            "type": "string",
            "description": "是否允许回看：1 是，2 否；创建时服务端初始化为 2",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "reviewAuth": {
            "type": "string",
            "description": "回看权限：1 公开，2 登录，3 密码",
            "enum": [
              "1",
              "2",
              "3"
            ]
          },
          "reviewPassword": {
            "type": "string",
            "description": "回看密码；reviewAuth=3 时使用"
          },
          "liveStatus": {
            "type": "string",
            "description": "直播状态：1 未开始，2 直播中，3 已结束；创建时服务端初始化为 1",
            "enum": [
              "1",
              "2",
              "3"
            ],
            "readOnly": true
          },
          "ruleId": {
            "type": "string",
            "description": "课程节次规则 ID；为空时服务端按 timeBegin 匹配"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "time": {
            "type": "integer",
            "description": "兼容计算字段",
            "format": "int64",
            "readOnly": true
          },
          "courseCoverUrl": {
            "type": "string",
            "description": "课程封面完整访问地址",
            "readOnly": true
          },
          "tbCourseAttendList": {
            "type": "array",
            "description": "听讲端列表，可不传或传空数组",
            "items": {
              "$ref": "#/components/schemas/TbCourseAttend"
            }
          },
          "tbResourceFile": {
            "allOf": [
              {
                "$ref": "#/components/schemas/TbResourceFile"
              }
            ],
            "description": "兼容附件对象；当前创建课程流程不保存该对象"
          },
          "attendOrganId": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构 ID",
            "readOnly": true
          },
          "attendOrganName": {
            "type": "string",
            "description": "兼容展示字段：听讲学校或机构名称",
            "readOnly": true
          },
          "attendClassId": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级 ID",
            "readOnly": true
          },
          "attendClassName": {
            "type": "string",
            "description": "兼容展示字段：听讲教室或班级名称",
            "readOnly": true
          },
          "isMainTeach": {
            "type": "string",
            "description": "是否主讲端：1 主讲端，2 听讲端",
            "enum": [
              "1",
              "2"
            ],
            "readOnly": true
          },
          "dpTeacherName": {
            "type": "string",
            "description": "兼容展示字段：点评教师名称",
            "readOnly": true
          },
          "allowed": {
            "type": "string",
            "description": "兼容控制字段：1 表示踢出后禁止再次加入",
            "enum": [
              "0",
              "1"
            ]
          }
        }
      },
      "TbCourseAttend": {
        "type": "object",
        "properties": {
          "attendId": {
            "type": "string",
            "description": "听讲记录 ID；创建时由服务端生成",
            "readOnly": true
          },
          "fkCourseId": {
            "type": "string",
            "description": "关联课程 ID；创建时由服务端写入",
            "readOnly": true
          },
          "courseType": {
            "type": "string",
            "description": "课程类型；创建时继承主课程",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ],
            "readOnly": true
          },
          "provinceCode": {
            "type": "string",
            "description": "听讲端省编码"
          },
          "provinceName": {
            "type": "string",
            "description": "听讲端省名称"
          },
          "cityCode": {
            "type": "string",
            "description": "听讲端市编码"
          },
          "cityName": {
            "type": "string",
            "description": "听讲端市名称"
          },
          "countyCode": {
            "type": "string",
            "description": "听讲端区县编码"
          },
          "countyName": {
            "type": "string",
            "description": "听讲端区县名称"
          },
          "organId": {
            "type": "string",
            "description": "听讲学校或机构 ID"
          },
          "organName": {
            "type": "string",
            "description": "听讲学校或机构名称"
          },
          "classId": {
            "type": "string",
            "description": "听讲教室或班级 ID"
          },
          "className": {
            "type": "string",
            "description": "听讲教室或班级名称"
          },
          "placeId": {
            "type": "string",
            "description": "听讲场所 ID"
          },
          "placeName": {
            "type": "string",
            "description": "听讲场所名称"
          },
          "roomId": {
            "type": "string",
            "description": "视频房间 ID"
          },
          "roomName": {
            "type": "string",
            "description": "视频房间名称"
          },
          "memberId": {
            "type": "string",
            "description": "听讲成员 ID，可为设备或个人身份"
          },
          "memberName": {
            "type": "string",
            "description": "听讲成员名称"
          },
          "status": {
            "type": "string",
            "description": "删除状态：0 正常，1 删除；创建时服务端初始化为 0",
            "enum": [
              "0",
              "1"
            ],
            "readOnly": true
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID，由开放平台写入",
            "readOnly": true
          },
          "createName": {
            "type": "string",
            "description": "创建人名称，由开放平台写入",
            "readOnly": true
          },
          "createTime": {
            "type": "string",
            "description": "创建时间，由服务端写入",
            "readOnly": true
          },
          "createUnitId": {
            "type": "string",
            "description": "创建单位 ID，由主课程 organId 写入",
            "readOnly": true
          },
          "createUnitName": {
            "type": "string",
            "description": "创建单位名称，由主课程 organName 写入",
            "readOnly": true
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID",
            "readOnly": true
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称",
            "readOnly": true
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间",
            "readOnly": true
          },
          "updateUnitId": {
            "type": "string",
            "description": "更新单位 ID",
            "readOnly": true
          },
          "updateUnitName": {
            "type": "string",
            "description": "更新单位名称",
            "readOnly": true
          },
          "distance": {
            "type": "number",
            "format": "double",
            "description": "兼容距离计算字段",
            "readOnly": true
          }
        }
      },
      "TbResourceFile": {
        "type": "object",
        "properties": {
          "pkId": {
            "type": "integer",
            "description": "附件主键",
            "format": "int64"
          },
          "resourceId": {
            "type": "string",
            "description": "资源主键"
          },
          "fileName": {
            "type": "string",
            "description": "文件名称"
          },
          "fileType": {
            "type": "string",
            "description": "类型：1 文档，2 图片，3 音视频，4 资料，5 盒子录制回放",
            "enum": [
              "1",
              "2",
              "3",
              "4",
              "5"
            ]
          },
          "fileUrl": {
            "type": "string",
            "description": "文件访问地址"
          },
          "hanFileId": {
            "type": "integer",
            "description": "Han 统一文件服务 ID",
            "format": "int64"
          },
          "fileExt": {
            "type": "string",
            "description": "文件扩展名"
          },
          "fileSize": {
            "type": "string",
            "description": "文件大小"
          },
          "oldName": {
            "type": "string",
            "description": "原始文件名称"
          },
          "status": {
            "type": "string",
            "description": "状态"
          },
          "createId": {
            "type": "string",
            "description": "创建人 ID"
          },
          "createName": {
            "type": "string",
            "description": "创建人名称"
          },
          "createTime": {
            "type": "string",
            "description": "创建时间"
          },
          "updateId": {
            "type": "string",
            "description": "更新人 ID"
          },
          "updateName": {
            "type": "string",
            "description": "更新人名称"
          },
          "updateTime": {
            "type": "string",
            "description": "更新时间"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"courseId":"COURSE-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"courseId":"COURSE-TEST-001","courseName":"开放平台示例课程","courseType":"1","courseDesc":"用于接口联调的示例课程","organId":"9026081001","organName":"示例学校","classId":"CLASS-001","className":"示例主讲教室","roomId":"ROOM-TEST-001","roomName":"开放平台示例课程的直播","memberId":"PRESENTER-001","memberName":"示例主讲人","teacherId":"TEACHER-001","teacherName":"示例教师","stageCode":"2","stageName":"初中","gradeCode":"7","gradeName":"七年级","subjectCode":"01","subjectName":"语文","timeBegin":"2026-08-26 09:00:00","timeEnd":"2026-08-26 09:40:00","streamType":"1","isLive":"1","isRecord":"2","isQualityCourse":"2","peopleNumber":30,"viewAuth":"1","reviewAuth":"1","liveStatus":"1","ruleId":"RULE-001","status":"0","tbCourseAttendList":[{"attendId":"ATTEND-001","fkCourseId":"COURSE-TEST-001","courseType":"1","organId":"9026081001","organName":"示例学校","classId":"CLASS-002","className":"示例听讲教室","roomId":"ROOM-TEST-001","roomName":"开放平台示例课程的直播","memberId":"LISTENER-001","memberName":"示例听讲成员","status":"0"}]},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.live.enter'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 开始录制 (classroom.record.start)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 开始录制",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/StartRecord": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "开始录制",
        "description": "开始主讲录制。该操作依赖视频录制能力和 Egress 服务。",
        "operationId": "startRecord",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.record.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.record.control": "开始录制所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"roomId":"ROOM-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.record.start'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 停止录制 (classroom.record.stop)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 停止录制",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/StopRecordByUUID": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "停止录制",
        "description": "停止主讲录制。该操作依赖已存在的录制任务和视频录制能力。",
        "operationId": "stopRecordByUUID",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.record.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.record.control": "停止录制所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"roomId":"ROOM-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.record.stop'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 断开课堂成员 (classroom.member.kick)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 断开课堂成员",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/kickPeople": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "断开课堂成员",
        "description": "断开指定成员。allowed=1 时成员被踢出后不可再次加入，其他值允许再次加入。",
        "operationId": "kickPeople",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.member.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          },
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "要断开的成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "LISTENER-001"
          },
          {
            "name": "allowed",
            "in": "query",
            "required": true,
            "description": "是否禁止再次加入：1 禁止，0 允许",
            "schema": {
              "type": "string",
              "enum": [
                "0",
                "1"
              ]
            },
            "example": "1"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.member.control": "断开课堂成员所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"roomId":"ROOM-TEST-001","memberId":"LISTENER-001","allowed":"1"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.member.kick'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 设置成员静音 (classroom.member.mute)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 设置成员静音",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/live/muteMember": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "设置成员静音",
        "description": "设置指定成员静音或解除静音。",
        "operationId": "muteMember",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.member.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          },
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "LISTENER-001"
          },
          {
            "name": "muted",
            "in": "query",
            "required": true,
            "description": "true 静音，false 解除静音",
            "schema": {
              "type": "boolean"
            },
            "example": true
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.member.control": "设置成员静音所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"roomId":"ROOM-TEST-001","memberId":"LISTENER-001","muted":true}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.member.mute'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 按编码查询设备 (classroom.device.read)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 按编码查询设备",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/common/getDeviceInfoByDeviceCode": {
      "get": {
        "tags": [
          "视频课堂"
        ],
        "summary": "按编码查询设备",
        "description": "从当前教育设备目录按设备编码查询，并转换为旧数字校园设备字段。",
        "operationId": "getDeviceInfoByDeviceCode",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.device.read"
            ]
          }
        ],
        "parameters": [
          {
            "name": "deviceCode",
            "in": "query",
            "required": true,
            "description": "设备编码",
            "schema": {
              "type": "string"
            },
            "example": "DEVICE-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "$ref": "#/components/schemas/LegacyDevice"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "pk_id": "10001",
                    "create_time": null,
                    "update_time": null,
                    "creator_id": null,
                    "updator_id": null,
                    "provice_code": null,
                    "creator_name": null,
                    "updator_name": null,
                    "device_name": "示例互动终端",
                    "device_code": "DEVICE-TEST-001",
                    "device_description": null,
                    "supplier_id": null,
                    "agr_id": null,
                    "device_type_name": "互动课堂终端",
                    "device_type": "classroom-terminal",
                    "device_status": "1",
                    "org_id": "9026081001",
                    "org_name": "示例学校",
                    "place_name": "示例教室",
                    "place_id": "CLASS-001",
                    "building_id": null,
                    "del_flag": null,
                    "supplier_name": null,
                    "agr_name": null,
                    "application_type_name": "三个课堂",
                    "application_type": "three-classroom",
                    "device_source": null,
                    "video_source": null,
                    "state": "1",
                    "channel_id": null,
                    "channel_name": null,
                    "sing_flag": null,
                    "other_info": null,
                    "channel_pk_id": null,
                    "time_text": null,
                    "code_flag": null,
                    "date": null,
                    "is_replayable": null,
                    "is_voiceable": null,
                    "is_rotatable": null,
                    "parameter_id": null,
                    "parameter": null
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.device.read": "按编码查询设备所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      },
      "LegacyDevice": {
        "type": "object",
        "properties": {
          "pk_id": {
            "type": "string",
            "nullable": true,
            "description": "设备主键"
          },
          "create_time": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "update_time": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "creator_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "updator_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "provice_code": {
            "type": "string",
            "nullable": true,
            "description": "省编码；保留旧接口拼写"
          },
          "creator_name": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "updator_name": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "device_name": {
            "type": "string",
            "nullable": true,
            "description": "设备名称"
          },
          "device_code": {
            "type": "string",
            "nullable": true,
            "description": "设备编码"
          },
          "device_description": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "supplier_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "agr_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "device_type_name": {
            "type": "string",
            "nullable": true,
            "description": "设备类型名称"
          },
          "device_type": {
            "type": "string",
            "nullable": true,
            "description": "设备类型"
          },
          "device_status": {
            "type": "string",
            "nullable": true,
            "description": "设备状态"
          },
          "org_id": {
            "type": "string",
            "nullable": true,
            "description": "学校或机构 ID"
          },
          "org_name": {
            "type": "string",
            "nullable": true,
            "description": "学校或机构名称"
          },
          "place_name": {
            "type": "string",
            "nullable": true,
            "description": "场所或教室名称"
          },
          "place_id": {
            "type": "string",
            "nullable": true,
            "description": "场所或教室 ID"
          },
          "building_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "del_flag": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "supplier_name": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "agr_name": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "application_type_name": {
            "type": "string",
            "nullable": true,
            "description": "应用类型名称，多个值以逗号分隔"
          },
          "application_type": {
            "type": "string",
            "nullable": true,
            "description": "应用类型编码，多个值以逗号分隔"
          },
          "device_source": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "video_source": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "state": {
            "type": "string",
            "nullable": true,
            "description": "设备状态兼容值"
          },
          "channel_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "channel_name": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "sing_flag": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "other_info": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "channel_pk_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "time_text": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "code_flag": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "date": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "is_replayable": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "is_voiceable": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "is_rotatable": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "parameter_id": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          },
          "parameter": {
            "type": "string",
            "nullable": true,
            "description": "旧设备目录兼容字段"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"deviceCode":"DEVICE-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"pk_id":"10001","create_time":null,"update_time":null,"creator_id":null,"updator_id":null,"provice_code":null,"creator_name":null,"updator_name":null,"device_name":"示例互动终端","device_code":"DEVICE-TEST-001","device_description":null,"supplier_id":null,"agr_id":null,"device_type_name":"互动课堂终端","device_type":"classroom-terminal","device_status":"1","org_id":"9026081001","org_name":"示例学校","place_name":"示例教室","place_id":"CLASS-001","building_id":null,"del_flag":null,"supplier_name":null,"agr_name":null,"application_type_name":"三个课堂","application_type":"three-classroom","device_source":null,"video_source":null,"state":"1","channel_id":null,"channel_name":null,"sing_flag":null,"other_info":null,"channel_pk_id":null,"time_text":null,"code_flag":null,"date":null,"is_replayable":null,"is_voiceable":null,"is_rotatable":null,"parameter_id":null,"parameter":null},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.device.read'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 订阅课堂事件 (classroom.event.subscribe)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 订阅课堂事件",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/event/eventSubscriptions": {
      "get": {
        "tags": [
          "视频课堂"
        ],
        "summary": "订阅课堂事件",
        "description": "读取并消费指定成员一分钟内的课堂事件。返回对象的键为旧事件代码。",
        "operationId": "eventSubscriptions",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.event.read"
            ]
          }
        ],
        "parameters": [
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "已关联课程的成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "LISTENER-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "description": "一分钟内事件映射；键为旧事件代码，如 elb",
                      "additionalProperties": {
                        "type": "object",
                        "properties": {
                          "timestamp": {
                            "type": "string",
                            "description": "事件产生时间戳，毫秒字符串"
                          },
                          "params": {
                            "type": "string",
                            "nullable": true,
                            "description": "事件附加参数；可能是下游 Result 的 JSON 字符串"
                          },
                          "roomId": {
                            "type": "array",
                            "items": {
                              "type": "string"
                            },
                            "description": "关联房间 ID 列表"
                          }
                        }
                      }
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": {
                    "elb": {
                      "timestamp": "1787706000000",
                      "params": null,
                      "roomId": [
                        "ROOM-TEST-001"
                      ]
                    }
                  },
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.event.read": "订阅课堂事件所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"memberId":"LISTENER-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":{"elb":{"timestamp":"1787706000000","params":null,"roomId":["ROOM-TEST-001"]}},"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.event.subscribe'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

-- 发送下课事件 (classroom.event.class-over)
UPDATE open_api_resource_version AS version_row
JOIN open_api_resource AS resource_row ON resource_row.id = version_row.resource_id
SET version_row.openapi_json = '{
  "openapi": "3.0.3",
  "info": {
    "title": "巴蜀云校开放平台 - 发送下课事件",
    "version": "v1",
    "description": "兼容原三个课堂输入参数和 Result 响应信封。先通过 OAuth2 client_credentials 获取 Access Token，再使用 Authorization: Bearer <access_token> 调用。"
  },
  "servers": [
    {
      "url": "https://www.lunengbashushuzhixuexiao.com",
      "description": "巴蜀云校开放平台生产地址"
    }
  ],
  "paths": {
    "/open/api/v1/classroom/event/addClassOverEvent": {
      "post": {
        "tags": [
          "视频课堂"
        ],
        "summary": "发送下课事件",
        "description": "向课程听讲端发送下课事件，并完成课程结束、录制停止等旧业务处理。",
        "operationId": "addClassOverEvent",
        "security": [
          {
            "OpenPlatformOAuth2": [
              "classroom.live.control"
            ]
          }
        ],
        "parameters": [
          {
            "name": "memberId",
            "in": "query",
            "required": true,
            "description": "发起下课的主讲成员 ID",
            "schema": {
              "type": "string"
            },
            "example": "PRESENTER-001"
          },
          {
            "name": "code",
            "in": "query",
            "required": true,
            "description": "旧课堂事件代码；下课使用 9",
            "schema": {
              "type": "integer",
              "format": "int32"
            },
            "example": 9
          },
          {
            "name": "roomId",
            "in": "query",
            "required": true,
            "description": "视频房间 ID",
            "schema": {
              "type": "string"
            },
            "example": "ROOM-TEST-001"
          }
        ],
        "responses": {
          "200": {
            "description": "旧三个课堂 Result 成功信封；HTTP 200 时仍须检查 success 和 code",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "required": [
                    "success",
                    "message",
                    "code",
                    "timestamp"
                  ],
                  "properties": {
                    "success": {
                      "type": "boolean",
                      "description": "业务是否成功"
                    },
                    "message": {
                      "type": "string",
                      "description": "业务消息"
                    },
                    "code": {
                      "type": "integer",
                      "format": "int32",
                      "description": "业务状态码，成功为 200"
                    },
                    "result": {
                      "type": "object",
                      "nullable": true,
                      "description": "成功时为空"
                    },
                    "timestamp": {
                      "type": "integer",
                      "format": "int64",
                      "description": "服务器时间戳，毫秒"
                    }
                  }
                },
                "example": {
                  "success": true,
                  "message": "操作成功！",
                  "code": 200,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "400": {
            "description": "请求参数错误",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "参数错误",
                  "code": 400,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "401": {
            "description": "Bearer Token 缺失、无效或已过期",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "缺少开放平台 Bearer Token",
                  "code": 401,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "403": {
            "description": "接口、Scope 或学校数据范围未授权",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "应用未获该学校的数据授权",
                  "code": 403,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "429": {
            "description": "触发接口限流",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "请求过于频繁，请稍后重试",
                  "code": 429,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          },
          "500": {
            "description": "业务处理或下游服务失败",
            "content": {
              "application/json": {
                "schema": {
                  "$ref": "#/components/schemas/LegacyErrorEnvelope"
                },
                "example": {
                  "success": false,
                  "message": "业务处理失败",
                  "code": 500,
                  "result": null,
                  "timestamp": 0
                }
              }
            }
          }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "OpenPlatformOAuth2": {
        "type": "oauth2",
        "description": "开放平台 OAuth2 客户端凭证模式；Access Token 以 Bearer 方式发送",
        "flows": {
          "clientCredentials": {
            "tokenUrl": "/open/oauth2/token",
            "scopes": {
              "classroom.live.control": "发送下课事件所需 Scope"
            }
          }
        }
      }
    },
    "schemas": {
      "LegacyErrorEnvelope": {
        "type": "object",
        "required": [
          "success",
          "message",
          "code",
          "timestamp"
        ],
        "properties": {
          "success": {
            "type": "boolean",
            "example": false
          },
          "message": {
            "type": "string",
            "description": "错误信息"
          },
          "code": {
            "type": "integer",
            "format": "int32",
            "description": "业务状态码"
          },
          "result": {
            "type": "object",
            "nullable": true,
            "description": "错误时通常为空"
          },
          "timestamp": {
            "type": "integer",
            "format": "int64",
            "description": "服务器时间戳，毫秒"
          }
        }
      }
    }
  }
}',
    version_row.request_example_json = '{"memberId":"PRESENTER-001","code":9,"roomId":"ROOM-TEST-001"}',
    version_row.response_examples_json = '{"success":true,"message":"操作成功！","code":200,"result":null,"timestamp":0}'
WHERE resource_row.resource_code = 'classroom.event.class-over'
  AND version_row.version = 'v1'
  AND version_row.status = 1
  AND version_row.del_flag = 0;

COMMIT;
