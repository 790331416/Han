# Han Cloud 接口清单（前端参考）

> **通用规则**：
> - 网关地址：`http://localhost:9090`
> - 所有请求需携带 `Authorization: Bearer {accessToken}`（除认证接口外）
> - ID 为雪花 ID，前端类型 `string | number`，**禁止数学运算**
> - 仅使用 GET（查询）和 POST（写操作）
> - 统一响应：`{ code: 200, msg: "操作成功", data: T, success: true }`
> - 分页响应：`data: { rows: T[], total: number }`

---

## 1. 认证 `/auth`

| 接口 | 方法 | 路径 | 参数 | 响应 | 权限 |
|------|------|------|------|------|------|
| 获取验证码 | GET | `/auth/captcha` | 无 | `{ uuid, img(base64) }` | 无 |
| PC登录 | POST | `/auth/login` | Body: `{ username, password, code?, uuid? }` | `{ accessToken, refreshToken, expiresIn }` | 无 |
| App登录 | POST | `/auth/app/login` | 同上 | 同上 | 无 |
| 刷新Token | POST | `/auth/refresh` | Header: `X-Refresh-Token: {token}` | 同上 | 无 |
| 登出 | POST | `/auth/logout` | Header: `Authorization` | void | 已登录 |

**调用流程**：
```
GET /auth/captcha → 显示验证码图片
POST /auth/login { username, password, code, uuid } → 存 accessToken 到 Pinia
后续请求 → Header: Authorization: Bearer {accessToken}
Token过期 → POST /auth/refresh (Header: X-Refresh-Token)
登出 → POST /auth/logout
```

---

## 2. 用户管理 `/system/user`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 用户列表 | GET | `/system/user/list` | Query: `pageNum, pageSize, username?, phone?, status?, deptId?` | `system:user:list` |
| 用户详情 | GET | `/system/user/info/{userId}` | — | `system:user:query` |
| 新增用户 | POST | `/system/user` | Body: `{ username, nickname, password, deptId, phone?, email?, sex?, status, roleIds[], postIds[] }` | `system:user:add` |
| 编辑用户 | POST | `/system/user/edit` | Body: 同上 + `userId` | `system:user:edit` |
| 删除用户 | POST | `/system/user/remove/{userId}` | — | `system:user:remove` |
| 批量删除 | POST | `/system/user/remove` | Body: `[userId1, ...]` | `system:user:remove` |
| 重置密码 | POST | `/system/user/resetPwd` | Params: `userId, password` | `system:user:resetPwd` |
| 修改状态 | POST | `/system/user/changeStatus` | Params: `userId, status(0正常/1停用)` | `system:user:edit` |
| 当前用户 | GET | `/system/user/current` | — | 已登录 |
| 简单列表 | GET | `/system/user/simple-list` | — | 已登录 |
| 导出 | GET | `/system/user/export` | Query: 同列表 | `system:user:export` |
| 导入模板 | GET | `/system/user/importTemplate` | — | `system:user:import` |
| 导入 | POST | `/system/user/import` | FormData: `file, updateSupport?` | `system:user:import` |

**调用流程**：
```
页面加载 → GET /system/user/list?pageNum=1&pageSize=10
新增弹窗 → GET /system/role/all + GET /system/post/all + GET /system/dept/tree
            → POST /system/user
编辑 → GET /system/user/info/{userId} → POST /system/user/edit
重置密码 → POST /system/user/resetPwd?userId={id}&password={pwd}
导出 → GET /system/user/export → Blob 下载
导入 → GET /system/user/importTemplate → 下载模板
       POST /system/user/import → FormData 上传
```

---

## 3. 个人中心 `/system/user/profile`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 获取信息 | GET | `/system/user/profile` | — | 已登录 |
| 修改信息 | POST | `/system/user/profile/edit` | Body: `{ nickname, phone, email, sex }` | 已登录 |
| 修改密码 | POST | `/system/user/profile/password` | Body: `{ oldPassword, newPassword }` | 已登录 |
| 修改头像 | POST | `/system/user/profile/avatar` | Body: `{ avatar: "url" }` | 已登录 |

---

## 4. 角色管理 `/system/role`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 角色列表 | GET | `/system/role/list` | Query: `pageNum, pageSize, roleName?, roleKey?, status?` | `system:role:list` |
| 全量角色 | GET | `/system/role/all` | — | 已登录 |
| 角色详情 | GET | `/system/role/info/{roleId}` | — | `system:role:query` |
| 新增 | POST | `/system/role` | Body: `{ roleName, roleKey, roleSort, status, menuIds[], remark? }` | `system:role:add` |
| 编辑 | POST | `/system/role/edit` | Body: 同上 + `roleId` | `system:role:edit` |
| 删除 | POST | `/system/role/remove/{roleId}` | — | `system:role:remove` |
| 修改状态 | POST | `/system/role/changeStatus` | Params: `roleId, status` | `system:role:edit` |
| 角色菜单 | GET | `/system/role/menuIds/{roleId}` | — | 已登录 |
| 已授权用户 | GET | `/system/role/authUser/list` | Params: `roleId, username?, phone?, pageNum, pageSize` | `system:role:list` |
| 未授权用户 | GET | `/system/role/authUser/unallocated` | 同上 | `system:role:list` |
| 批量授权 | POST | `/system/role/authUser/selectAll` | Params: `roleId`, Body: `[userId1, ...]` | `system:role:edit` |
| 取消授权 | POST | `/system/role/authUser/cancel` | 同上 | `system:role:edit` |

**调用流程**：
```
角色列表 → GET /system/role/list
新增弹窗 → GET /system/menu/tree 加载菜单树
编辑 → GET /system/role/info/{roleId}
      + GET /system/role/menuIds/{roleId} 回显已选菜单
      → POST /system/role/edit
分配用户 → GET /system/role/authUser/list (已授权)
           GET /system/role/authUser/unallocated (未授权)
           POST /system/role/authUser/selectAll (授权)
           POST /system/role/authUser/cancel (取消)
```

---

## 5. 菜单管理 `/system/menu`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 动态路由 | GET | `/system/menu/routers` | — | 已登录 |
| 菜单列表 | GET | `/system/menu/list` | Query: `menuName?, status?` | `system:menu:list` |
| 菜单树 | GET | `/system/menu/tree` | — | 已登录 |
| 菜单详情 | GET | `/system/menu/info/{menuId}` | — | `system:menu:query` |
| 新增 | POST | `/system/menu` | Body: `{ menuName, parentId, orderNum, path, component, menuType(M/C/F), perms, icon }` | `system:menu:add` |
| 编辑 | POST | `/system/menu/edit` | Body: 同上 + `menuId` | `system:menu:edit` |
| 删除 | POST | `/system/menu/remove/{menuId}` | — | `system:menu:remove` |

**调用流程**：
```
登录后 → GET /system/menu/routers → 动态注册 Vue Router 路由
菜单管理 → GET /system/menu/list → 树形表格
menuType: M=目录 C=菜单 F=按钮(权限标识)
```

---

## 6. 部门管理 `/system/dept`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 部门列表 | GET | `/system/dept/list` | Query: `deptName?, status?` | `system:dept:list` |
| 部门树 | GET | `/system/dept/tree` | 同上 | `system:dept:list` |
| 部门详情 | GET | `/system/dept/info/{deptId}` | — | `system:dept:query` |
| 新增 | POST | `/system/dept` | Body: `{ parentId, deptName, orderNum, leaderId?, phone?, email?, status }` | `system:dept:add` |
| 编辑 | POST | `/system/dept/edit` | Body: 同上 + `deptId` | `system:dept:edit` |
| 删除 | POST | `/system/dept/remove/{deptId}` | — | `system:dept:remove` |

**调用流程**：
```
部门管理 → GET /system/dept/list → 树形表格
负责人选择 → GET /system/user/simple-list
新增 → POST /system/dept (parentId=上级部门ID)
```

---

## 7. 岗位管理 `/system/post`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 岗位列表 | GET | `/system/post/list` | Query: `pageNum, pageSize, postName?, postCode?, status?` | `system:post:list` |
| 全量岗位 | GET | `/system/post/all` | — | 已登录 |
| 岗位详情 | GET | `/system/post/{postId}` | — | `system:post:query` |
| 新增 | POST | `/system/post` | Body: `{ postName, postCode, postSort, status, remark? }` | `system:post:add` |
| 编辑 | POST | `/system/post/edit` | Body: 同上 + `postId` | `system:post:edit` |
| 删除 | POST | `/system/post/remove/{postId}` | — | `system:post:remove` |

---

## 8. 字典管理 `/system/dict`

### 字典类型

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 类型列表 | GET | `/system/dict/type/list` | Params: `pageNum, pageSize, dictName?, dictType?` | `system:dict:list` |
| 全部类型 | GET | `/system/dict/type/all` | — | 已登录 |
| 类型详情 | GET | `/system/dict/type/{dictId}` | — | `system:dict:query` |
| 新增 | POST | `/system/dict/type` | Body: `{ dictName, dictType, status, remark? }` | `system:dict:add` |
| 编辑 | POST | `/system/dict/type/edit` | Body: 同上 + `dictId` | `system:dict:edit` |
| 删除 | POST | `/system/dict/type/remove/{dictId}` | — | `system:dict:remove` |

### 字典数据

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 数据列表 | GET | `/system/dict/data/list` | Params: `pageNum, pageSize, dictType?` | `system:dict:list` |
| 按类型查 | GET | `/system/dict/data/type/{dictType}` | Path: dictType(字符串) | 已登录 |
| 数据详情 | GET | `/system/dict/data/{dictCode}` | — | `system:dict:query` |
| 新增 | POST | `/system/dict/data` | Body: `{ dictType, dictLabel, dictValue, dictSort, status }` | `system:dict:add` |
| 编辑 | POST | `/system/dict/data/edit` | Body: 同上 + `dictCode` | `system:dict:edit` |
| 删除 | POST | `/system/dict/data/remove/{dictCode}` | — | `system:dict:remove` |

**调用流程**：
```
字典管理 → GET /system/dict/type/list
点击类型 → GET /system/dict/data/list?dictType=xxx
业务下拉 → GET /system/dict/data/type/{dictType}
  建议缓存到 Pinia Store，避免重复请求
```

---

## 9. 通知公告 `/system/notice`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 列表 | GET | `/system/notice/list` | Params: `pageNum, pageSize, noticeTitle?, noticeType?, status?` | `system:notice:list` |
| 详情 | GET | `/system/notice/{noticeId}` | — | `system:notice:query` |
| 新增 | POST | `/system/notice/add` | Body: `{ noticeTitle, noticeType(1通知/2公告), noticeContent, status }` | `system:notice:add` |
| 编辑 | POST | `/system/notice/edit` | Body: 同上 + `noticeId` | `system:notice:edit` |
| 删除 | POST | `/system/notice/remove/{noticeId}` | — | `system:notice:remove` |
| 批量删除 | POST | `/system/notice/remove` | Body: `[noticeId1, ...]` | `system:notice:remove` |
| 最新通知 | GET | `/system/notice/latest` | Params: `limit?`，返回 `read/readTime` | 已登录 |
| 未读数 | GET | `/system/notice/unreadCount` | — | 已登录 |
| 标记已读 | POST | `/system/notice/markRead/{noticeId}` | — | 已登录 |
| 全部已读 | POST | `/system/notice/markAllRead` | — | 已登录 |
| SSE 推送 | GET | `/system/notice/sse` | SSE 长连接，浏览器使用 `?token=` 传递访问令牌 | 已登录 |

---

## 10. 在线用户 `/system/online`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 在线列表 | GET | `/system/online/list` | Params: `username?, ipAddr?` | `monitor:online:list` |
| 强制下线 | POST | `/system/online/forceLogout` | Body: `{ tokenId }` | `monitor:online:forceLogout` |

**响应字段**：`[{ tokenId, userId, username, nickname, ipAddr, clientType, loginTime }]`

---

## 11. 操作日志 `/system/operlog`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 列表 | GET | `/system/operlog/list` | Query对象: `pageNum, pageSize, module?, operType?` | `monitor:operlog:list` |
| 详情 | GET | `/system/operlog/{id}` | — | `monitor:operlog:list` |
| 批量删除 | POST | `/system/operlog/remove` | Body: `[id1, ...]` | `monitor:operlog:remove` |
| 清空 | POST | `/system/operlog/clean` | — | `monitor:operlog:remove` |

---

## 12. 登录日志 `/system/loginlog`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 列表 | GET | `/system/loginlog/list` | Params: `pageNum, pageSize` | `monitor:logininfor:list` |
| 批量删除 | POST | `/system/loginlog/remove` | Body: `[id1, ...]` | `monitor:logininfor:remove` |
| 清空 | POST | `/system/loginlog/clean` | — | `monitor:logininfor:remove` |

---

## 13. 仪表盘 `/system/dashboard`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 统计 | GET | `/system/dashboard/stats` | — | 已登录 |

**响应**（`DashboardStatsVO`）：

```json
{
  "userCount": 2,           // Integer|null — 需 system:user:list
  "roleCount": 4,           // Integer|null — 需 system:role:list
  "deptCount": 6,           // Integer|null — 需 system:dept:list
  "postCount": 4,           // Integer|null — 需 system:post:list
  "onlineCount": 1,         // Integer|null — 需 monitor:online:list（Redis key 扫描）
  "dictCount": 11,          // Integer|null — 需 system:dict:list
  "noticeCount": 3,         // Integer|null — 需 system:notice:list
  "jobCount": null,         // Integer|null — 需 job:list（跨服务暂不统计，始终 null）
  "recentLogins": [         // List|null — 需 system:loginlog:list，最近5条
    { "username": "admin", "ipAddr": "127.0.0.1", "status": 0, "message": "登录成功", "loginTime": "2026-03-04 10:00:00" }
  ],
  "recentOperLogs": [       // List|null — 需 system:operlog:list，最近5条
    { "module": "用户管理", "operName": "admin", "operIp": "127.0.0.1", "status": 0, "operTime": "2026-03-04 10:05:00" }
  ]
}
```

> **权限过滤规则**：每个字段在用户无对应权限时返回 `null`，前端据此隐藏对应统计卡片或日志模块。
> 超级管理员（`*:*:*`）可看到全部字段。

**前端使用**：
```
GET /system/dashboard/stats
→ data.userCount !== null → 显示"用户总数"卡片
→ data.recentLogins !== null → 显示"最近登录"表格
→ 快捷入口按权限过滤 + Pinia localStorage 点击量排序
```

---

## 14. 服务器监控 `/system/monitor`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 服务器信息 | GET | `/system/monitor/server` | — | `monitor:server:list` |
| 缓存信息 | GET | `/system/monitor/cache` | — | `monitor:cache:list` |

---

## 15. 定时任务 `/job`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 任务列表 | GET | `/job/list` | Query: `pageNum, pageSize, jobName?, jobGroup?, status?` | 已登录 |
| 任务详情 | GET | `/job/{jobId}` | — | 已登录 |
| 处理器列表 | GET | `/job/handlers` | — | 已登录 |
| 新增 | POST | `/job` | Body: `{ jobName, jobGroup, cronExpression, invokeTarget, misfirePolicy, concurrent, status }` | 已登录 |
| 编辑 | POST | `/job/edit` | Body: 同上 + `jobId` | 已登录 |
| 删除 | POST | `/job/remove/{jobId}` | — | 已登录 |
| 修改状态 | POST | `/job/changeStatus` | Params: `jobId, status(0正常/1暂停)` | 已登录 |
| 立即执行 | POST | `/job/run/{jobId}` | — | 已登录 |
| 校验Cron | GET | `/job/checkCron` | Params: `cronExpression` | 已登录 |

**调用流程**：
```
任务列表 → GET /job/list
新增 → GET /job/handlers 获取处理器 → POST /job
编辑 → GET /job/{jobId} 回填 → POST /job/edit
状态 → POST /job/changeStatus?jobId={id}&status={0|1}
执行 → POST /job/run/{jobId}
Cron校验 → GET /job/checkCron?cronExpression=xxx
```

---

## 16. 任务日志 `/job/log`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 列表 | GET | `/job/log/list` | Query: `pageNum, pageSize, jobName?, status?` | 已登录 |
| 详情 | GET | `/job/log/{jobLogId}` | — | 已登录 |
| 删除 | POST | `/job/log/remove/{jobLogIds}` | Path: 逗号分隔 | 已登录 |
| 清空 | POST | `/job/log/clean` | — | 已登录 |

---

## 17. 租户管理 `/tenant`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 租户列表 | GET | `/tenant/list` | Query: `tenantName?, contactName?, status?` | `tenant:list` |
| 有效租户 | GET | `/tenant/all` | — | 已登录 |
| 租户详情 | GET | `/tenant/{tenantId}` | — | `tenant:query` |
| 按域名查 | GET | `/tenant/domain/{domain}` | — | 已登录 |
| 新增 | POST | `/tenant` | Body: `{ tenantName, contactName, contactPhone, packageId, userLimit, adminUsername, adminPassword }` | `tenant:add` |
| 编辑 | POST | `/tenant/edit` | Body: TenantDTO（含 base 字段） | `tenant:edit` |
| 修改状态 | POST | `/tenant/changeStatus` | Params: `tenantId, status` | `tenant:edit` |
| 同步套餐 | POST | `/tenant/syncPackage` | Params: `tenantId, packageId` | `tenant:edit` |
| 删除 | POST | `/tenant/remove/{tenantId}` | — | `tenant:remove` |
| 有效性校验 | GET | `/tenant/check/{tenantId}` | — | 已登录 |
| 用户限额 | GET | `/tenant/checkUserLimit/{tenantId}` | — | 已登录 |
| 管理员ID | GET | `/tenant/adminUser` | Params: `tenantId` | `tenant:query` |

### 配额管理

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 查询配额 | GET | `/tenant/quota/{tenantId}` | — | `tenant:quota:query` |
| 设置配额 | POST | `/tenant/quota/edit` | Body: `{ tenantId, userLimit, storageLimit, apiLimit }` | `tenant:quota:edit` |

**调用流程**：
```
租户列表 → GET /tenant/list
新增 → GET /tenant/package/all 获取套餐列表 → POST /tenant
编辑 → GET /tenant/{tenantId} → POST /tenant/edit
配额 → GET /tenant/quota/{tenantId} → POST /tenant/quota/edit
```

---

## 18. 租户套餐 `/tenant/package`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 套餐列表 | GET | `/tenant/package/list` | Params: `packageName?, status?, pageNum, pageSize` | `tenant:package:list` |
| 全部有效 | GET | `/tenant/package/all` | — | 已登录 |
| 套餐详情 | GET | `/tenant/package/{packageId}` | — | `tenant:package:query` |
| 新增 | POST | `/tenant/package` | Body: `{ packageName, remark?, status }` | `tenant:package:add` |
| 编辑 | POST | `/tenant/package/edit` | Body: 同上 + `packageId` | `tenant:package:edit` |
| 删除 | POST | `/tenant/package/remove/{packageId}` | — | `tenant:package:remove` |
| 修改状态 | POST | `/tenant/package/changeStatus` | Params: `packageId, status` | `tenant:package:edit` |
| 查看菜单 | GET | `/tenant/package/menus/{packageId}` | — | `tenant:package:query` |
| 更新菜单 | POST | `/tenant/package/menus/{packageId}` | Body: `[menuId1, ...]` (Set) | `tenant:package:edit` |

**调用流程**：
```
套餐列表 → GET /tenant/package/list
新增/编辑 → GET /system/menu/tree 加载菜单树
             GET /tenant/package/menus/{id} 回显已选
             POST /tenant/package/menus/{id} 更新菜单
```

---

## 19. 文件上传 `/file`

| 接口 | 方法 | 路径 | 参数 | 权限 |
|------|------|------|------|------|
| 上传 | POST | `/file/upload` | FormData: `file` | 已登录 |

**响应**：`{ name, url }`
