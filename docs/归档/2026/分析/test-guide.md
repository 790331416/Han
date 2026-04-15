# Han Cloud 全功能页面测试指南

> **版本**：v2.0 | **日期**：2026-03-04 | **网关**：`http://localhost:9090` | **前端**：`http://localhost:3000`

---

## 〇、测试账号总览

| # | 账号 | 密码 | 所属租户 | 角色 | 说明 |
|---|------|------|---------|------|------|
| 1 | `admin` | `admin123` | 平台（tenant_id=1） | 超级管理员 | 拥有全部权限，可管理租户 |
| 2 | `han` | `admin123` | 平台（tenant_id=1） | 普通管理员 | 有部分系统管理权限 |
| 3 | `t1_admin` | `Admin@123` | 星辰科技 | 租户管理员 | 全功能套餐，拥有租户内全部权限 |
| 4 | `t2_admin` | `Admin@123` | 蓝海电商 | 租户管理员 | 基础套餐，只有用户/角色/部门/岗位/字典 |
| 5 | `t3_admin` | `Admin@123` | 微光传媒 | 租户管理员 | 最小套餐，只有用户/部门管理 |

> **密码说明**：`admin123` 是 reinit.sql 中内置用户的密码；`Admin@123` 是通过 API 创建租户时指定的管理员密码。
>
> **租户登录**：后端支持 `username` 在同一 `tenant_id` 内唯一。如果不同租户下存在同名用户，登录时需在 Body 中传 `tenantId` 字段指定租户。本测试中用户名均唯一（`t1_admin` / `t2_admin` / `t3_admin`），通常无需传 `tenantId`。

---

## 一、环境准备 & 数据初始化

### 1.1 确认服务运行

```bash
# 检查所有服务是否正常（Docker 环境）
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 或检查本地服务
# han-gateway: 9090, han-auth: 9200, han-system: 9201, han-tenant: 9401, han-job: 9501
```

### 1.2 清理旧测试数据（SQL）

> **目的**：只保留 reinit.sql 中的基础数据（admin/han 用户、默认菜单、默认字典等），清理掉之前 API 测试遗留的脏数据。

连接 PostgreSQL 后执行：

```sql
-- ==================== 清理日志 ====================
TRUNCATE TABLE sys_oper_log;
TRUNCATE TABLE sys_login_log;

-- ==================== 清理定时任务日志 ====================
TRUNCATE TABLE sys_job_log;

-- ==================== 清理测试创建的通知公告 ====================
DELETE FROM sys_notice WHERE id NOT IN (SELECT id FROM sys_notice LIMIT 0);
-- 或直接清空：
TRUNCATE TABLE sys_notice;

-- ==================== 清理非基础租户（保留 tenant_id=1 平台租户）====================
-- 先清理租户关联的用户数据
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id != 1);
DELETE FROM sys_user_post WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id != 1);
DELETE FROM sys_user WHERE tenant_id != 1;
DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id != 1);
DELETE FROM sys_role WHERE tenant_id != 1;
DELETE FROM sys_dept WHERE tenant_id != 1;
DELETE FROM sys_post WHERE tenant_id != 1;
DELETE FROM sys_dict_type WHERE tenant_id != 1;
DELETE FROM sys_dict_data WHERE tenant_id != 1;
DELETE FROM sys_config WHERE tenant_id != 1;

-- 清理租户本体（保留 id=1）
UPDATE sys_tenant SET deleted = 0 WHERE id = 1;
DELETE FROM sys_tenant WHERE id != 1 AND deleted = 0;
UPDATE sys_tenant SET deleted = 1 WHERE id != 1;

-- 清理测试套餐（保留 id=1 企业标准版）
DELETE FROM sys_tenant_package WHERE id != 1 AND deleted = 0;

-- 清理配额表
DELETE FROM sys_tenant_quota WHERE tenant_id != 1;

-- ==================== 重置 Redis ====================
-- 在 Redis CLI 中执行（可选，清理在线用户缓存）：
-- KEYS han:token:* 然后逐个 DEL，或：
-- FLUSHDB（慎用，会清除所有 Redis 数据）
```

### 1.3 通过 CURL 创建测试数据

> 以下所有 CURL 命令请在 **PowerShell** 中执行。如使用 bash/Git Bash，将 `\` 换行改为标准格式。

#### Step 1：登录获取 Token

```powershell
# 登录（如开启验证码，先临时关闭：参数配置中 sys.account.captchaEnabled 设为 false）
$login = Invoke-RestMethod -Uri "http://localhost:9090/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $login.data.accessToken
Write-Host "Token: $token"

# 后续所有请求使用此 Header
$headers = @{ "Authorization" = "Bearer $token" }
```

#### Step 2：创建 3 个租户套餐（不同权限级别）

```powershell
# --- 套餐A：全功能套餐（几乎所有菜单） ---
$pkgA = Invoke-RestMethod -Uri "http://localhost:9090/tenant/package" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  packageName = "全功能套餐"
  status = 0
  remark = "包含系统管理+监控的完整功能"
} | ConvertTo-Json)
Write-Host "全功能套餐 ID: $($pkgA.data)"

# 获取所有菜单ID（排除租户管理相关，因为租户管理是平台级功能）
$menus = Invoke-RestMethod -Uri "http://localhost:9090/system/menu/list" -Method GET -Headers $headers
# 收集菜单ID（排除租户管理目录 id=4 及其子菜单 400,401）
$allMenuIds = $menus.data | Where-Object { $_.id -ne 4 -and $_.id -ne 400 -and $_.id -ne 401 } | ForEach-Object { $_.id }
# 更新套餐A的菜单
Invoke-RestMethod -Uri "http://localhost:9090/tenant/package/menus/$($pkgA.data)" -Method POST -ContentType "application/json" -Headers $headers -Body ($allMenuIds | ConvertTo-Json)
Write-Host "全功能套餐菜单已设置: $($allMenuIds.Count) 个菜单"

# --- 套餐B：基础套餐（仅系统管理核心功能） ---
$pkgB = Invoke-RestMethod -Uri "http://localhost:9090/tenant/package" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  packageName = "基础套餐"
  status = 0
  remark = "仅包含用户/角色/部门/岗位/字典管理"
} | ConvertTo-Json)
Write-Host "基础套餐 ID: $($pkgB.data)"

# 基础套餐菜单：系统管理(1) + 用户(100) + 角色(101) + 部门(103) + 岗位(104) + 字典(105) + 各自的按钮权限
$basicMenuIds = @(1, 100, 101, 103, 104, 105,
  1001,1002,1003,1004,1005,1006,1007,
  1011,1012,1013,1014,1015,
  1031,1032,1033,1034,
  1041,1042,1043,1044,1045,
  1051,1052,1053,1054,1055)
Invoke-RestMethod -Uri "http://localhost:9090/tenant/package/menus/$($pkgB.data)" -Method POST -ContentType "application/json" -Headers $headers -Body ($basicMenuIds | ConvertTo-Json)
Write-Host "基础套餐菜单已设置: $($basicMenuIds.Count) 个菜单"

# --- 套餐C：最小套餐（仅用户和部门） ---
$pkgC = Invoke-RestMethod -Uri "http://localhost:9090/tenant/package" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  packageName = "最小套餐"
  status = 0
  remark = "仅用户管理+部门管理"
} | ConvertTo-Json)
Write-Host "最小套餐 ID: $($pkgC.data)"

$minMenuIds = @(1, 100, 103, 1001,1002,1003,1004, 1031,1032,1033,1034)
Invoke-RestMethod -Uri "http://localhost:9090/tenant/package/menus/$($pkgC.data)" -Method POST -ContentType "application/json" -Headers $headers -Body ($minMenuIds | ConvertTo-Json)
Write-Host "最小套餐菜单已设置: $($minMenuIds.Count) 个菜单"
```

#### Step 3：创建 3 个租户（三级差异化权限）

```powershell
# --- 租户1：星辰科技（全功能套餐） ---
$t1 = Invoke-RestMethod -Uri "http://localhost:9090/tenant" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  tenantName = "星辰科技"
  contactName = "张星辰"
  contactPhone = "13900001001"
  packageId = $pkgA.data
  userLimit = 50
  adminUsername = "t1_admin"
  adminPassword = "Admin@123"
} | ConvertTo-Json)
Write-Host "星辰科技租户 ID: $($t1.data)"

# --- 租户2：蓝海电商（基础套餐） ---
$t2 = Invoke-RestMethod -Uri "http://localhost:9090/tenant" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  tenantName = "蓝海电商"
  contactName = "李蓝海"
  contactPhone = "13900002002"
  packageId = $pkgB.data
  userLimit = 20
  adminUsername = "t2_admin"
  adminPassword = "Admin@123"
} | ConvertTo-Json)
Write-Host "蓝海电商租户 ID: $($t2.data)"

# --- 租户3：微光传媒（最小套餐） ---
$t3 = Invoke-RestMethod -Uri "http://localhost:9090/tenant" -Method POST -ContentType "application/json" -Headers $headers -Body (@{
  tenantName = "微光传媒"
  contactName = "王微光"
  contactPhone = "13900003003"
  packageId = $pkgC.data
  userLimit = 10
  adminUsername = "t3_admin"
  adminPassword = "Admin@123"
} | ConvertTo-Json)
Write-Host "微光传媒租户 ID: $($t3.data)"
```

#### Step 4：为平台创建额外测试数据

```powershell
# --- 创建一些岗位 ---
Invoke-RestMethod -Uri "http://localhost:9090/system/post" -Method POST -ContentType "application/json" -Headers $headers -Body '{"postName":"测试工程师","postCode":"tester","postSort":5,"status":0}'
Invoke-RestMethod -Uri "http://localhost:9090/system/post" -Method POST -ContentType "application/json" -Headers $headers -Body '{"postName":"运维工程师","postCode":"ops","postSort":6,"status":0}'

# --- 创建一些通知公告 ---
Invoke-RestMethod -Uri "http://localhost:9090/system/notice/add" -Method POST -ContentType "application/json" -Headers $headers -Body '{"noticeTitle":"系统升级公告","noticeType":"2","noticeContent":"<p>系统将于本周五凌晨2:00-4:00进行升级维护</p>","status":0}'
Invoke-RestMethod -Uri "http://localhost:9090/system/notice/add" -Method POST -ContentType "application/json" -Headers $headers -Body '{"noticeTitle":"新功能上线通知","noticeType":"1","noticeContent":"<p>仪表盘已支持权限过滤显示和快捷入口排序功能</p>","status":0}'
Invoke-RestMethod -Uri "http://localhost:9090/system/notice/add" -Method POST -ContentType "application/json" -Headers $headers -Body '{"noticeTitle":"安全提醒","noticeType":"1","noticeContent":"<p>请定期修改密码，确保账号安全</p>","status":0}'

# --- 创建字典类型和数据 ---
Invoke-RestMethod -Uri "http://localhost:9090/system/dict/type" -Method POST -ContentType "application/json" -Headers $headers -Body '{"dictName":"任务优先级","dictType":"task_priority","status":0,"remark":"任务优先级列表"}'
Invoke-RestMethod -Uri "http://localhost:9090/system/dict/data" -Method POST -ContentType "application/json" -Headers $headers -Body '{"dictType":"task_priority","dictLabel":"高","dictValue":"high","dictSort":1,"status":0}'
Invoke-RestMethod -Uri "http://localhost:9090/system/dict/data" -Method POST -ContentType "application/json" -Headers $headers -Body '{"dictType":"task_priority","dictLabel":"中","dictValue":"medium","dictSort":2,"status":0}'
Invoke-RestMethod -Uri "http://localhost:9090/system/dict/data" -Method POST -ContentType "application/json" -Headers $headers -Body '{"dictType":"task_priority","dictLabel":"低","dictValue":"low","dictSort":3,"status":0}'

Write-Host "`n========== 测试数据创建完成 =========="
Write-Host "平台管理员: admin / admin123"
Write-Host "星辰科技(全功能): t1_admin / Admin@123"
Write-Host "蓝海电商(基础): t2_admin / Admin@123"
Write-Host "微光传媒(最小): t3_admin / Admin@123"
```

### 1.4 验证数据创建成功

```powershell
# 验证租户列表
Invoke-RestMethod -Uri "http://localhost:9090/tenant/list" -Method GET -Headers $headers | ConvertTo-Json -Depth 3

# 验证套餐列表
Invoke-RestMethod -Uri "http://localhost:9090/tenant/package/list?pageNum=1&pageSize=10" -Method GET -Headers $headers | ConvertTo-Json -Depth 3

# 验证租户管理员可以登录
$t1Login = Invoke-RestMethod -Uri "http://localhost:9090/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"t1_admin","password":"Admin@123"}'
Write-Host "星辰科技登录: $($t1Login.code) - $($t1Login.msg)"
```

---

## 二、测试 Phase 1 — 平台管理员测试（admin / admin123）

> **目标**：以超级管理员身份测试所有页面功能，确认全部功能正常。

### 2.1 登录 & 首页

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 打开 `http://localhost:3000`，输入 `admin / admin123` | 登录成功，跳转到 Dashboard | |
| 2 | 观察 Dashboard 统计卡片 | 显示用户总数、角色数量、部门数量、岗位数量、在线用户、字典类型、通知公告共 7 张卡片（jobCount 跨服务暂不统计，不显示） | |
| 3 | 观察快捷入口区域 | 显示全部 12 个快捷入口：用户管理、角色管理、部门管理、菜单管理、岗位管理、字典管理、通知公告、参数配置、任务调度、操作日志、在线用户、系统监控 | |
| 4 | 点击快捷入口"用户管理" | 跳转到用户管理页面 | |
| 5 | 返回 Dashboard | 快捷入口排序变化（刚点击的"用户管理"排到前面） | |
| 6 | 观察最近登录/最近操作表格 | 有刚才的登录记录和之前创建数据的操作记录 | |
| 7 | 检查左侧菜单 | 系统管理、系统监控、系统工具、租户管理等目录都可见 | |

### 2.2 用户管理（系统管理 → 用户管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入用户管理页面 | 表格显示 admin、han 两个用户 | |
| 2 | 搜索用户名 `han` | 只显示 han 用户 | |
| 3 | 点击"重置" | 恢复显示全部用户 | |
| 4 | 点击"新增" | 弹窗打开，部门树、角色列表、岗位列表正常加载 | |
| 5 | 填写：用户名 `testuser`，昵称 `测试用户`，密码 `Test@12345`，选择部门"研发部门"，角色选"普通管理员"，岗位选"开发工程师" | 提交成功，列表刷新出现 testuser | |
| 6 | 点击 testuser 的"编辑" | 回显所有字段：部门=研发部门，角色=普通管理员，岗位=开发工程师 | |
| 7 | 修改昵称为 `测试用户改`，提交 | 成功，列表显示新昵称 | |
| 8 | 点击 testuser 的"重置密码" | 输入 `NewPwd@123`，提示重置成功 | |
| 9 | 切换 testuser 状态为"停用" | 确认对话框 → 确认 → 状态变为停用 | |
| 10 | 切换 testuser 状态为"正常" | 状态恢复正常 | |
| 11 | 删除 testuser | 确认删除 → 提示删除成功 → 列表刷新 | |

### 2.3 角色管理（系统管理 → 角色管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入角色管理页面 | 表格显示超级管理员、普通管理员、部门管理员、普通用户 4 个角色 | |
| 2 | 超级管理员行的编辑/删除按钮 | 置灰不可点击 | |
| 3 | 点击"新增角色" | 弹窗打开，菜单权限树正常加载 | |
| 4 | 填写：角色名 `审计员`，权限字符 `auditor`，勾选"系统监控"下所有菜单（在线用户、操作日志、登录日志） | 提交成功 | |
| 5 | 点击"审计员"的"编辑" | 回显角色信息，菜单树中"系统监控"下的菜单已勾选 | |
| 6 | 增加勾选"服务监控"，提交 | 成功 | |
| 7 | 搜索角色名 `审计` | 只显示审计员角色 | |
| 8 | 切换"审计员"状态为停用 | 成功 | |
| 9 | 切换回正常 | 成功 | |
| 10 | 删除"审计员" | 成功 | |

### 2.4 菜单管理（系统管理 → 菜单管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入菜单管理页面 | 树形表格正常显示，包含 M(目录)、C(菜单)、F(按钮) 三类 | |
| 2 | 点击"折叠" | 所有子节点收起 | |
| 3 | 点击"展开" | 所有子节点展开 | |
| 4 | 搜索"用户" | 过滤显示包含"用户"的菜单项 | |
| 5 | 点击系统管理行的"新增" | 弹窗中上级菜单自动设为"系统管理" | |
| 6 | 新增一个测试菜单：名称 `测试页面`，类型"菜单"，路由 `test-page`，组件 `system/test/index`，权限 `system:test:list` | 提交成功 | |
| 7 | 编辑刚创建的"测试页面" | 回显所有字段 | |
| 8 | 删除"测试页面" | 确认后删除成功 | |

### 2.5 部门管理（系统管理 → 部门管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入部门管理页面 | 树形表格显示 han科技 → 研发部门(研发一组/二组)、产品部门、运营部门 | |
| 2 | 点击"新增" | 弹窗打开，上级部门树可选 | |
| 3 | 在"运营部门"下新增子部门：`市场推广组` | 提交成功，运营部门展开显示新子部门 | |
| 4 | 编辑"市场推广组"，修改名称为 `品牌推广组` | 提交成功 | |
| 5 | 删除"品牌推广组" | 成功 | |
| 6 | 尝试删除"研发部门"（有子部门） | 提示不允许删除，有下级部门 | |

### 2.6 岗位管理（系统管理 → 岗位管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入岗位管理页面 | 表格显示董事长、技术总监、项目经理、开发工程师、测试工程师、运维工程师 | |
| 2 | 搜索"工程师" | 过滤显示测试工程师和运维工程师 | |
| 3 | 新增岗位：名称 `UI设计师`，编码 `ui_designer`，排序 7 | 提交成功 | |
| 4 | 编辑"UI设计师"，修改排序为 8 | 成功 | |
| 5 | 删除"UI设计师" | 成功 | |

### 2.7 字典管理（系统管理 → 字典管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入字典管理页面 | 表格显示用户性别、系统开关、任务优先级等字典类型 | |
| 2 | 点击"任务优先级"行的操作进入字典数据 | 显示高/中/低三条数据 | |
| 3 | 新增字典数据：标签 `紧急`，值 `urgent`，排序 0 | 提交成功 | |
| 4 | 编辑"紧急"，修改排序为 -1 | 成功 | |
| 5 | 删除"紧急" | 成功 | |
| 6 | 返回字典类型列表，新增类型：名称 `测试状态`，类型 `test_status` | 成功 | |
| 7 | 删除"测试状态" | 成功 | |

### 2.8 通知公告（系统管理 → 通知公告）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入通知公告页面 | 表格显示之前创建的 3 条公告/通知 | |
| 2 | 搜索标题"升级" | 过滤显示"系统升级公告" | |
| 3 | 新增通知：标题 `测试通知`，类型"通知"，内容随意 | 提交成功 | |
| 4 | 编辑"测试通知"，修改标题 | 成功 | |
| 5 | 删除"测试通知" | 成功 | |

### 2.8a 参数配置（系统管理 → 参数设置）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入参数设置页面 | 表格显示主框架皮肤、初始密码、侧边栏主题、验证码开关、注册开关、黑名单等 6 条配置 | |
| 2 | 搜索"密码" | 过滤显示"用户管理-账号初始密码" | |
| 3 | 点击"初始密码"的"编辑" | 回显配置名、键名、键值、内置标识 | |
| 4 | 新增参数：名称 `测试参数`，键名 `sys.test.param`，键值 `test123` | 提交成功 | |
| 5 | 删除"测试参数" | 成功 | |

### 2.8b 客户端管理（系统管理 → 客户端管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入客户端管理页面 | 表格显示 PC后台管理端、App移动端、H5移动端、微信小程序、微信公众号、开放API 共 6 个客户端 | |
| 2 | 查看各客户端的 Token 过期时间、刷新过期时间、最大在线数 | 数据与 reinit.sql 一致 | |
| 3 | 新增客户端：key `test_client`，secret `test_secret`，类型"pc" | 提交成功 | |
| 4 | 编辑"test_client"，修改 Token 过期时间 | 成功 | |
| 5 | 删除"test_client" | 成功 | |

### 2.9 在线用户（系统监控 → 在线用户）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入在线用户页面 | 至少显示当前登录的 admin 用户 | |
| 2 | 搜索用户名 `admin` | 过滤显示 admin 的在线记录 | |
| 3 | 查看列数据 | 显示 tokenId、用户名、昵称、IP、客户端类型、登录时间 | |

### 2.10 操作日志（系统监控 → 操作日志）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入操作日志页面 | 表格显示之前操作产生的日志记录 | |
| 2 | 分页翻页 | 数据正确加载 | |
| 3 | 点击某条日志查看详情 | 弹窗显示请求方法、URL、参数、结果等 | |

### 2.11 登录日志（系统监控 → 登录日志）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入登录日志页面 | 表格显示登录记录（包含初始化时的登录和刚才的登录） | |
| 2 | 状态列 | 成功显示绿色标签，失败显示红色标签 | |

### 2.12 服务器监控（系统监控 → 服务监控）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入服务监控页面 | 显示 CPU、内存、JVM、磁盘等监控信息 | |

### 2.13 定时任务（任务调度）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入定时任务页面 | 显示"系统监控"和"数据同步"两个预置任务 | |
| 2 | 点击某任务的"编辑" | 弹窗回显任务信息（名称、Cron、处理器等） | |
| 3 | 新增任务：名称 `测试任务`，Cron `0 0/10 * * * ?`，处理器选一个 | 提交成功 | |
| 4 | 切换"测试任务"状态为"运行" | 成功 | |
| 5 | 点击"立即执行" | 提示已触发 | |
| 6 | 查看任务日志 | 有执行记录 | |
| 7 | 切换"测试任务"状态为"暂停" → 删除 | 成功 | |

### 2.14 租户套餐管理（租户管理 → 套餐管理）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入套餐管理页面 | 显示企业标准版、全功能套餐、基础套餐、最小套餐 4 个套餐 | |
| 2 | 点击"全功能套餐"查看菜单 | 菜单树中大部分菜单已勾选（排除租户管理） | |
| 3 | 点击"基础套餐"查看菜单 | 只有用户/角色/部门/岗位/字典相关菜单被勾选 | |
| 4 | 点击"最小套餐"查看菜单 | 只有用户/部门相关菜单被勾选 | |

### 2.15 租户管理（租户管理 → 租户列表）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入租户列表页面 | 显示超级管理租户、星辰科技、蓝海电商、微光传媒 4 个租户 | |
| 2 | 查看各租户套餐列 | 星辰=全功能，蓝海=基础，微光=最小 | |
| 3 | 点击"星辰科技"编辑 | 回显租户信息：联系人、电话、套餐、用户限额等 | |
| 4 | 搜索"蓝海" | 过滤显示蓝海电商 | |

### 2.16 个人中心

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 点击右上角头像 → "个人中心" | 进入个人中心页面 | |
| 2 | 查看基本信息 | 显示用户名、昵称、手机、邮箱等 | |
| 3 | 修改昵称为"超级管理员改" | 提交成功 | |
| 4 | 修改密码：旧密码 `admin123`，新密码 `Admin@1234` | 成功（**测试后改回来**） | |
| 5 | 用新密码重新登录 | 成功 | |
| 6 | 改回旧密码 `admin123` | 成功 | |

### 2.17 退出登录

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 点击右上角 → "退出登录" | 跳转回登录页 | |
| 2 | 访问 `/dashboard` | 自动重定向到登录页（未登录状态） | |

---

## 三、测试 Phase 2 — 租户1 全功能测试（t1_admin / Admin@123）

> **目标**：验证全功能套餐租户拥有除租户管理外的所有功能。

### 3.1 登录 & Dashboard

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用 `t1_admin / Admin@123` 登录（如提示用户不存在，需在 Body 中加 `tenantId`） | 登录成功 | |
| 2 | 观察左侧菜单 | 有"系统管理"、"系统监控"等菜单，**没有**"租户管理" | |
| 3 | 观察 Dashboard 统计卡片 | 显示用户总数(1)、部门数量等，数值为本租户的数据 | |
| 4 | 观察快捷入口 | 显示 t1_admin 有权限的快捷入口，**没有**租户相关入口 | |

### 3.2 用户管理（租户数据隔离验证）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入用户管理 | 只显示 `t1_admin` 一个用户（本租户数据） | |
| 2 | **不应看到** admin、han 等平台用户 | 列表中不包含其他租户的用户 | |
| 3 | 新增用户：`t1_user1`，昵称 `星辰员工`，密码 `User@123` | 提交成功 | |
| 4 | 列表现在显示 2 个用户 | 数据正确 | |

### 3.3 角色管理

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入角色管理 | 显示本租户的角色（创建租户时自动生成的管理员角色） | |
| 2 | 新增角色：`星辰员工`，权限字符 `t1_staff` | 菜单树中显示全功能套餐包含的菜单（无租户管理菜单） | |
| 3 | 勾选用户管理相关权限，提交 | 成功 | |

### 3.4 部门管理

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入部门管理 | 显示本租户的部门树（创建租户时自动生成的默认部门） | |
| 2 | 新增子部门：`技术部` | 成功 | |

### 3.5 其他页面快速验证

| 页面 | 预期 | ✅/❌ |
|------|------|-------|
| 岗位管理 | 可正常进入，显示本租户岗位 | |
| 字典管理 | 可正常进入，可新增/编辑字典 | |
| 通知公告 | 可正常进入 | |
| 在线用户 | 可正常进入，显示本租户在线用户 | |
| 操作日志 | 可正常进入，显示本租户操作日志 | |
| 登录日志 | 可正常进入 | |
| 服务监控 | 可正常进入 | |

---

## 四、测试 Phase 3 — 租户2 基础套餐测试（t2_admin / Admin@123）

> **目标**：验证基础套餐租户只能看到用户/角色/部门/岗位/字典管理。

### 4.1 登录 & 菜单验证

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用 `t2_admin / Admin@123` 登录（如需指定租户：Body 加 `tenantId`） | 登录成功 | |
| 2 | 观察左侧菜单 | **只有**"系统管理"，且系统管理下**只有**用户管理、角色管理、部门管理、岗位管理、字典管理 | |
| 3 | **不应看到**：菜单管理、参数设置、通知公告、系统监控、租户管理 | 这些菜单不存在 | |

### 4.2 Dashboard 差异化

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 查看统计卡片 | 只显示有权限的模块统计（用户、角色、部门、岗位、字典），**不显示**在线用户、通知公告 | |
| 2 | 查看快捷入口 | 只有用户管理、角色管理、部门管理、岗位管理、字典管理 | |
| 3 | **不应显示**最近登录/最近操作表格 | 因为没有 `monitor:loginlog:list` 和 `monitor:operlog:list` 权限 | |

### 4.3 功能边界验证

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入用户管理 | 只显示 t2_admin 一个用户 | |
| 2 | 新增用户 `t2_user1` | 成功 | |
| 3 | 进入角色管理，新增角色 | 菜单权限树**只显示**基础套餐的菜单（无监控相关） | |
| 4 | 手动在浏览器地址栏输入 `/system/notice` | 无法访问或显示无权限提示 | |
| 5 | 手动输入 `/system/online` | 无法访问 | |

---

## 五、测试 Phase 4 — 租户3 最小套餐测试（t3_admin / Admin@123）

> **目标**：验证最小套餐租户只能看到用户管理和部门管理。

### 5.1 登录 & 菜单验证

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用 `t3_admin / Admin@123` 登录（如需指定租户：Body 加 `tenantId`） | 登录成功 | |
| 2 | 观察左侧菜单 | **只有**"系统管理"，下面**只有**用户管理、部门管理 | |
| 3 | **不应看到**：角色管理、岗位管理、字典管理、菜单管理、通知公告、系统监控等 | 全部不存在 | |

### 5.2 Dashboard 差异化（与租户2对比）

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 查看统计卡片 | **只显示**用户总数和部门数量 2 张卡片（或再加上有权限的其他项） | |
| 2 | 查看快捷入口 | **只有**用户管理、部门管理 2 个入口 | |
| 3 | 没有最近登录/操作日志表格 | 正确隐藏 | |

### 5.3 功能边界

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 进入用户管理 | 只有 t3_admin | |
| 2 | 新增用户 `t3_user1` | 成功，但新增弹窗中角色列表为空（因为没有角色管理权限/无角色数据） | |
| 3 | 进入部门管理 | 正常显示 | |
| 4 | 手动访问 `/system/role` | 无法访问 | |
| 5 | 手动访问 `/system/post` | 无法访问 | |
| 6 | 手动访问 `/system/dict` | 无法访问 | |

---

## 六、测试 Phase 5 — 跨租户数据隔离验证

> **目标**：确认不同租户之间的数据完全隔离。

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 以 `t1_admin` 登录，记下用户列表中的用户数 | 假设为 N1 | |
| 2 | 以 `t2_admin` 登录，查看用户列表 | 看不到 t1 租户的用户 | |
| 3 | 以 `t3_admin` 登录，查看用户列表 | 看不到 t1、t2 租户的用户 | |
| 4 | 以 `admin` 登录，查看用户列表 | 只看到平台(tenant_id=1)的用户 | |
| 5 | 以 `t1_admin` 创建的部门 | t2_admin 的部门列表中看不到 | |
| 6 | 以 `t2_admin` 创建的角色 | t1_admin 的角色列表中看不到 | |

---

## 七、测试 Phase 6 — 权限联动端到端验证

> **目标**：在租户1内创建限权用户，验证菜单和按钮权限生效。

### 7.1 在租户1内创建限权角色

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 以 `t1_admin` 登录 | 成功 | |
| 2 | 角色管理 → 新增：角色名 `只读角色`，权限字符 `readonly` | | |
| 3 | 菜单权限树中只勾选：用户管理(100) → 用户查询(1001)；部门管理(103) → 部门查询(1031) | 只给"查询"权限，不给增删改 | |
| 4 | 提交成功 | | |

### 7.2 创建限权用户

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用户管理 → 新增：用户名 `t1_readonly`，密码 `Read@123`，角色选"只读角色" | 成功 | |
| 2 | 退出 t1_admin 登录 | | |

### 7.3 以限权用户登录验证

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用 `t1_readonly / Read@123` 登录 | 成功 | |
| 2 | 左侧菜单 | 只有系统管理 → 用户管理、部门管理 | |
| 3 | 进入用户管理 | 能看到用户列表 | |
| 4 | "新增"按钮 | **不显示**或**置灰**（因为没有 `system:user:add` 权限） | |
| 5 | 用户列表中操作列的"编辑"/"删除" | **不显示**或**置灰** | |
| 6 | 进入部门管理 | 能看到部门列表 | |
| 7 | "新增"按钮 | **不显示**或**置灰** | |
| 8 | Dashboard 统计卡片 | 只显示用户和部门的数量（有 list 权限） | |

---

## 八、测试 Phase 7 — 边界 & 异常测试

### 8.1 表单校验

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 新增用户不填用户名 | 提示"请输入用户名" | |
| 2 | 新增用户密码填 `123` | 提示密码格式不符 | |
| 3 | 新增角色不填名称 | 提示"请输入角色名称" | |
| 4 | 新增菜单不填名称 | 提示"请输入菜单名称" | |

### 8.2 唯一性校验

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 以 admin 登录，新增用户名为 `admin` | 后端返回"用户名已存在" | |
| 2 | 新增角色权限字符为已存在的值 | 返回错误提示 | |

### 8.3 删除保护

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 尝试删除超级管理员角色 | 按钮不可点击 | |
| 2 | 尝试删除有子部门的部门 | 后端提示"存在子部门不允许删除" | |
| 3 | 尝试删除已分配用户的角色 | 后端提示"角色已分配不允许删除" | |

### 8.4 Token 过期

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 登录后长时间不操作（或手动清除 localStorage 中的 token） | 下次操作时跳转到登录页 | |

### 8.5 分页

| 步骤 | 操作 | 预期结果 | ✅/❌ |
|------|------|---------|-------|
| 1 | 用户列表切换每页 10/20/50 条 | 正确刷新 | |
| 2 | 翻到第 2 页 | 数据正确加载 | |

---

## 九、测试 Phase 8 — 页面 UI 通用检查

> 以 admin 账号登录，逐一检查每个页面。

| 检查项 | 预期 | ✅/❌ |
|--------|------|-------|
| 侧边栏菜单高亮与当前页面一致 | 是 | |
| 表格加载时显示 loading 动画 | 是 | |
| 弹窗打开/关闭动画流畅 | 是 | |
| 操作成功提示（绿色 Message） | 是 | |
| 操作失败提示（红色 Message） | 是 | |
| 响应式布局：浏览器窗口缩小到 768px | 侧边栏折叠，内容区自适应 | |
| Dashboard 统计卡片响应式 | 窄屏时变为 2 列 | |
| Dashboard 中下部区域响应式 | ≤1024px 时变为单列 | |
| 表格列文字过长时 tooltip 显示 | 是 | |
| 空数据状态 | 表格显示"暂无数据"图标 | |

---

## 十、测试结果汇总

| Phase | 模块 | 测试项数 | 通过 | 失败 | 备注 |
|-------|------|---------|------|------|------|
| 1 | 环境准备 & 数据初始化 | 4 | | | |
| 2 | 平台管理员 - 登录 & Dashboard | 7 | | | |
| 2 | 平台管理员 - 用户管理 | 11 | | | |
| 2 | 平台管理员 - 角色管理 | 10 | | | |
| 2 | 平台管理员 - 菜单管理 | 8 | | | |
| 2 | 平台管理员 - 部门管理 | 6 | | | |
| 2 | 平台管理员 - 岗位管理 | 5 | | | |
| 2 | 平台管理员 - 字典管理 | 7 | | | |
| 2 | 平台管理员 - 通知公告 | 5 | | | |
| 2 | 平台管理员 - 参数配置 | 5 | | | |
| 2 | 平台管理员 - 客户端管理 | 5 | | | |
| 2 | 平台管理员 - 在线用户 | 3 | | | |
| 2 | 平台管理员 - 操作日志 | 3 | | | |
| 2 | 平台管理员 - 登录日志 | 2 | | | |
| 2 | 平台管理员 - 服务监控 | 1 | | | |
| 2 | 平台管理员 - 定时任务 | 7 | | | |
| 2 | 平台管理员 - 租户套餐 | 4 | | | |
| 2 | 平台管理员 - 租户管理 | 4 | | | |
| 2 | 平台管理员 - 个人中心 | 6 | | | |
| 2 | 平台管理员 - 退出登录 | 2 | | | |
| 3 | 租户1(全功能) - 登录&Dashboard | 4 | | | |
| 3 | 租户1(全功能) - 用户管理 | 4 | | | |
| 3 | 租户1(全功能) - 角色管理 | 3 | | | |
| 3 | 租户1(全功能) - 部门管理 | 2 | | | |
| 3 | 租户1(全功能) - 其他页面 | 7 | | | |
| 4 | 租户2(基础) - 菜单验证 | 3 | | | |
| 4 | 租户2(基础) - Dashboard差异化 | 3 | | | |
| 4 | 租户2(基础) - 功能边界 | 5 | | | |
| 5 | 租户3(最小) - 菜单验证 | 3 | | | |
| 5 | 租户3(最小) - Dashboard差异化 | 3 | | | |
| 5 | 租户3(最小) - 功能边界 | 6 | | | |
| 6 | 跨租户数据隔离 | 6 | | | |
| 7 | 权限联动(限权用户) | 11 | | | |
| 8 | 边界异常测试 | 10 | | | |
| 9 | UI通用检查 | 10 | | | |
| | **合计** | **~183** | | | |

---

## 附录：测试执行顺序建议

```
1. 执行 SQL 清理数据（1.2 节）
2. 执行 PowerShell 脚本创建测试数据（1.3 节）
3. 验证数据（1.4 节）
4. Phase 2：admin 全功能测试 → 逐个页面过
5. Phase 3：t1_admin 全功能租户测试
6. Phase 4：t2_admin 基础套餐测试（重点看菜单差异和 Dashboard 差异）
7. Phase 5：t3_admin 最小套餐测试（重点看权限裁剪）
8. Phase 6：跨租户隔离验证（快速切换 4 个账号对比）
9. Phase 7：权限联动（在 t1 内创建只读用户）
10. Phase 8：边界异常
11. Phase 9：UI 检查
```
