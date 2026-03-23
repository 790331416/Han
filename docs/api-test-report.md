# Han Cloud API 测试报告

> 生成日期：2026-03-04 | 网关：`http://localhost:9090` | 账号：`admin / admin123`
> 测试脚本：`docs/test_api.py` | **结果：81/81 全部通过 ✅**

---

## 一、修复记录

### 1. Job 模块编辑 Bug ✅

- **症状**：编辑定时任务返回 500
- **根因**：`JobDTO.setJobId()` 与 `@JsonUnwrapped` 冲突，`base` 被覆盖导致 `jobId=null`
- **修复**：删除 `JobDTO` 便捷方法，`updateJob()` 改用 `dto.getBase().getJobId()`

### 2. 数据库 Schema 修复 ✅

| 问题 | 修复 |
|------|------|
| `sys_job` 缺少 `tenant_id` 列 | `ALTER TABLE` 添加 + 更新 `init.sql` |
| `sys_job_log` 缺少 `tenant_id` 列 | 同上 |
| `sys_tenant_quota` 表不存在 | `CREATE TABLE` + 写入 `init.sql` |

---

## 二、完整测试结果（70/70 ✅）

### 1. 用户管理（10/10）

| 接口 | 路径 | 结果 |
|------|------|------|
| 当前用户 | `GET /system/user/current` | ✅ |
| 用户列表 | `GET /system/user/list` | ✅ |
| 新增用户 | `POST /system/user` | ✅ |
| 用户详情 | `GET /system/user/info/{id}` | ✅ |
| 编辑用户 | `POST /system/user/edit` | ✅ |
| 重置密码 | `POST /system/user/resetPwd` | ✅ |
| 停用用户 | `POST /system/user/changeStatus` (disable) | ✅ |
| 启用用户 | `POST /system/user/changeStatus` (enable) | ✅ |
| 简单列表 | `GET /system/user/simple-list` | ✅ |
| 删除用户 | `POST /system/user/remove/{id}` | ✅ |

### 2. 角色管理（8/8）

| 接口 | 路径 | 结果 |
|------|------|------|
| 角色列表 | `GET /system/role/list` | ✅ |
| 全量角色 | `GET /system/role/all` | ✅ |
| 新增角色 | `POST /system/role` | ✅ |
| 角色详情 | `GET /system/role/info/{id}` | ✅ |
| 角色菜单IDs | `GET /system/role/menuIds/{id}` | ✅ |
| 编辑角色 | `POST /system/role/edit` | ✅ |
| 修改状态 | `POST /system/role/changeStatus` | ✅ |
| 删除角色 | `POST /system/role/remove/{id}` | ✅ |

### 3. 菜单管理（7/7）

| 接口 | 路径 | 结果 |
|------|------|------|
| 动态路由 | `GET /system/menu/routers` | ✅ (8 routes) |
| 菜单列表 | `GET /system/menu/list` | ✅ |
| 菜单树 | `GET /system/menu/tree` | ✅ |
| 新增菜单 | `POST /system/menu` | ✅ |
| 菜单详情 | `GET /system/menu/info/{id}` | ✅ |
| 编辑菜单 | `POST /system/menu/edit` | ✅ |
| 删除菜单 | `POST /system/menu/remove/{id}` | ✅ |

### 4. 部门管理（6/6）

| 接口 | 路径 | 结果 |
|------|------|------|
| 部门列表 | `GET /system/dept/list` | ✅ |
| 部门树 | `GET /system/dept/tree` | ✅ |
| 新增部门 | `POST /system/dept` | ✅ |
| 部门详情 | `GET /system/dept/info/{id}` | ✅ |
| 编辑部门 | `POST /system/dept/edit` | ✅ |
| 删除部门 | `POST /system/dept/remove/{id}` | ✅ |

### 5. 字典管理（10/10）

| 接口 | 路径 | 结果 |
|------|------|------|
| 类型列表 | `GET /system/dict/type/list` | ✅ |
| 全部类型 | `GET /system/dict/type/all` | ✅ |
| 新增类型 | `POST /system/dict/type` | ✅ |
| 类型详情 | `GET /system/dict/type/{id}` | ✅ |
| 编辑类型 | `POST /system/dict/type/edit` | ✅ |
| 删除类型 | `POST /system/dict/type/remove/{id}` | ✅ |
| 新增数据 | `POST /system/dict/data` | ✅ |
| 按类型查数据 | `GET /system/dict/data/type/{type}` | ✅ |
| 编辑数据 | `POST /system/dict/data/edit` | ✅ |
| 删除数据 | `POST /system/dict/data/remove/{id}` | ✅ |

### 6. 岗位管理（5/5）

| 接口 | 路径 | 结果 |
|------|------|------|
| 岗位列表 | `GET /system/post/list` | ✅ |
| 全量岗位 | `GET /system/post/all` | ✅ |
| 新增岗位 | `POST /system/post` | ✅ |
| 编辑岗位 | `POST /system/post/edit` | ✅ |
| 删除岗位 | `POST /system/post/remove/{id}` | ✅ |

### 7. 通知公告（5/5）

| 接口 | 路径 | 结果 |
|------|------|------|
| 公告列表 | `GET /system/notice/list` | ✅ |
| 新增公告 | `POST /system/notice/add` | ✅ |
| 公告详情 | `GET /system/notice/{id}` | ✅ |
| 编辑公告 | `POST /system/notice/edit` | ✅ |
| 删除公告 | `POST /system/notice/remove/{id}` | ✅ |

### 8. 日志与监控（5/5）

| 接口 | 路径 | 结果 |
|------|------|------|
| 操作日志 | `GET /system/operlog/list` | ✅ |
| 登录日志 | `GET /system/loginlog/list` | ✅ |
| 在线用户 | `GET /system/online/list` | ✅ |
| 仪表盘(旧4字段) | `GET /system/dashboard/stats` | ✅ (已升级) |
| 服务器监控 | `GET /system/monitor/server` | ✅ |

### 9. 租户套餐（7/7）

| 接口 | 路径 | 结果 |
|------|------|------|
| 套餐列表 | `GET /tenant/package/list` | ✅ |
| 全部有效 | `GET /tenant/package/all` | ✅ |
| 新增套餐 | `POST /tenant/package` | ✅ |
| 套餐详情 | `GET /tenant/package/{id}` | ✅ |
| 套餐菜单 | `GET /tenant/package/menus/{id}` | ✅ |
| 编辑套餐 | `POST /tenant/package/edit` | ✅ |
| 删除套餐 | `POST /tenant/package/remove/{id}` | ✅ |

### 10. 租户 & 配额（3/3）

| 接口 | 路径 | 结果 |
|------|------|------|
| 租户列表 | `GET /tenant/list` | ✅ |
| 租户配额 | `GET /tenant/quota/{id}` | ✅ |
| 有效租户 | `GET /tenant/all` | ✅ |

### 11. 定时任务（3/3）

| 接口 | 路径 | 结果 |
|------|------|------|
| 任务列表 | `GET /job/list` | ✅ |
| Cron校验 | `GET /job/checkCron` | ✅ |
| 任务日志 | `GET /job/log/list` | ✅ |

### 12. 个人中心（1/1）

| 接口 | 路径 | 结果 |
|------|------|------|
| 个人信息 | `GET /system/user/profile` | ✅ |

### 13. 角色授权用户（6/6）

| 接口 | 路径 | 结果 |
|------|------|------|
| 新增测试角色 | `POST /system/role` | ✅ |
| 新增测试用户 | `POST /system/user` | ✅ |
| 已授权用户 | `GET /system/role/authUser/list` | ✅ |
| 未授权用户 | `GET /system/role/authUser/unallocated` | ✅ |
| 批量授权 | `POST /system/role/authUser/selectAll` | ✅ |
| 取消授权 | `POST /system/role/authUser/cancel` | ✅ |

### 14. 在线用户 & 强制下线（3/3）

| 接口 | 路径 | 结果 |
|------|------|------|
| 在线列表(ipAddr) | `GET /system/online/list` | ✅ ipAddr=172.18.0.8 |
| ipAddr 已填充 | — | ✅ **已修复** |
| 强制下线 | `POST /system/online/forceLogout` | ✅ |

### 15. 用户导出（1/1）

| 接口 | 路径 | 结果 |
|------|------|------|
| 导出 xlsx | `GET /system/user/export` | ✅ 4548B |

### 16. 文件上传（1/1）

| 接口 | 路径 | 结果 |
|------|------|------|
| 文件上传 | `POST /file/upload` | ✅ 上传到 RustFS |

---

## 三、修复总结

| 问题 | 根因 | 修复 |
|------|------|------|
| Job 编辑 500 | `JobDTO.setJobId()` 与 `@JsonUnwrapped` 冲突 | 删除便捷方法 + MapStruct `@Mapping` |
| `sys_job`/`sys_job_log` 缺 `tenant_id` | init.sql 未加租户列 | 更新 init.sql + ALTER TABLE |
| `sys_tenant_quota` 不存在 | init.sql 缺表 | 更新 init.sql + CREATE TABLE |
| 在线用户 `ipAddr=null` | `buildLoginUser()` 未调用 `getClientIp()` | 添加 `.loginIp(getClientIp())` |

---

## 四、遗留问题

1. **用户导入** — 需 multipart 上传 xlsx，未纳入自动化
2. **操作日志/登录日志清空** — `POST /system/operlog/clean` 等破坏性操作未测试
3. **仪表盘 API 已升级（待重测）** — `GET /system/dashboard/stats` 响应从 4 字段扩展为 10 字段：
   - 新增：`roleCount`, `onlineCount`, `dictCount`, `noticeCount`, `jobCount`, `recentLogins`, `recentOperLogs`
   - 所有 count 字段类型 `Integer|null`，null 表示无权限（前端据此隐藏卡片）
   - `recentLogins` / `recentOperLogs` 为最近 5 条日志列表
   - 需在多租户/多角色场景下重新测试权限过滤逻辑
   - 详见 `api-endpoints.md` 第 13 节
