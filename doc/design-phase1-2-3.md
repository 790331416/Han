# Han Cloud 用户 & 多租户功能补全 — 详细设计方案

> **文档版本**: v1.0  
> **创建日期**: 2026-03-03  
> **适用范围**: Phase 1 / Phase 2 / Phase 3

---

## 现有基础设施评估（重要）

经过代码分析，以下基础设施**已就绪**，无需重建：

| 组件 | 文件 | 状态 |
|------|------|------|
| 实体继承链 `BaseEntity → TenantEntity → BizEntity` | `han-common-mybatis` | ✅ `tenantId` 字段已在 `TenantEntity` 中 |
| `HanTenantLineHandler` 租户 SQL 过滤器 | `han-common-mybatis/handler/` | ✅ 自动追加 `WHERE tenant_id = ?` |
| `TenantLineInnerInterceptor` 拦截器注册 | `MybatisPlusConfig` | ✅ 条件开关 `tenant.enable=true` |
| `TenantProperties` 配置（排除表列表） | `han-common-mybatis/config/` | ✅ 默认排除 `sys_menu`, `sys_tenant` 等 |
| `TenantHelper` 忽略租户过滤工具 | `han-common-mybatis/helper/` | ✅ `TenantHelper.ignore(Runnable)` |
| `HanMetaObjectHandler` 自动填充 `tenantId` | `han-common-mybatis/handler/` | ✅ INSERT 时从 SecurityContext 获取 |
| `TenantContextHolder` (TTL) | `han-common-tenant` | ✅ 线程上下文 |
| `@IgnoreTenant` 注解 | `han-common-tenant` | ✅ 方法/类级别 |
| `SecurityContext` 接口 | `han-common-core` | ✅ 含 `getTenantId()` |
| `LoginDTO.tenantId` | `han-auth` | ✅ 登录请求已含租户ID |
| `LoginUser.tenantId` | `han-common-security` | ✅ 缓存到 Redis |
| `SysUserPo extends BizEntity` | `han-system` | ✅ 继承 `tenantId` |

**结论**：租户隔离核心框架**已基本就绪**，Phase 1 的工作量大幅减少，主要是**打通链路**和**补齐缺失环节**。

---

# Phase 1 — 租户隔离核心（打通链路）

## 1.1 问题诊断：当前链路断点

```
登录流程:
[前端] → LoginDTO(tenantId) → AuthServiceImpl.login()
    → systemServiceClient.getUserByUsername(username)  ← ⚠️ 未按租户过滤查用户
    → buildLoginUser(user, clientType, permissions)    ← ✅ tenantId 已传入
    → Redis 缓存 LoginUser                            ← ✅ tenantId 已缓存

请求流程:
[Gateway] → 解析 Token → 转发请求(X-User-Id header)
    → SecurityContextHolder → SecurityContext.getTenantId()  ← ⚠️ 需确认实现
    → HanTenantLineHandler.getTenantId()                     ← ✅ 从 SecurityContext 取
    → SQL 自动追加 WHERE tenant_id = ?                        ← ✅ 拦截器已就绪
```

### 断点清单

| # | 断点 | 位置 | 影响 |
|---|------|------|------|
| **B1** | 登录查用户未按租户隔离 | `ISysUserController.getUserByUsername()` | 不同租户同名用户冲突 |
| **B2** | SecurityContext 实现中 tenantId 来源不明 | `han-common-security` | 拦截器无法获取 tenantId |
| **B3** | Gateway 未传递 tenantId Header | `han-gateway` | 下游服务无法感知租户 |
| **B4** | 前端登录页无租户选择 | `han-ui/views/login/` | 无法指定登录哪个租户 |
| **B5** | 新增租户无初始化流程 | `TenantServiceImpl` | 创建租户后无管理员/角色/部门 |
| **B6** | 租户删除接口缺失 | `TenantController` | 无法安全删除租户 |
| **B7** | CurrentUserVO.tenantId 写死 null | `ASysUserController.getCurrentUserInfo()` | 前端无法获取当前租户 |

---

## 1.2 修复方案

### Task 1.2.1 — 补通 SecurityContext → tenantId 链路

**目标**：确保 `SecurityContext.getTenantId()` 在请求链路中能正确返回当前租户ID。

**改动文件**：`han-common-security` 中 `SecurityContext` 的实现类

```java
// SecurityContextImpl.java (预期实现)
@Override
public Long getTenantId() {
    // 优先从 LoginUser 中获取（已存 Redis）
    LoginUser loginUser = SecurityContextHolder.getLoginUser();
    if (loginUser != null && loginUser.getTenantId() != null) {
        return loginUser.getTenantId();
    }
    // 其次从 TenantContextHolder 获取（Gateway 传递）
    return TenantContextHolder.getTenantId();
}
```

**前置条件**：需确认 `SecurityContextHolder.getLoginUser()` 实现。

---

### Task 1.2.2 — Gateway 传递 tenantId

**目标**：Gateway 解析 Token 后，将 tenantId 写入请求 Header 传递给下游。

**改动文件**：`han-gateway` 中的全局过滤器

```java
// Gateway Filter 中追加：
.header("X-Tenant-Id", String.valueOf(loginUser.getTenantId()))
```

**下游接收**：各服务的 `SecurityContextHolder` 或 Filter 从 `X-Tenant-Id` Header 读取并设置到 `TenantContextHolder`。

---

### Task 1.2.3 — 登录流程按租户查用户

**改动文件**：
1. `ISysUserController.getUserByUsername()` — 增加 tenantId 参数
2. `SystemServiceClient` 接口 — 增加 tenantId 参数
3. `AuthServiceImpl.login()` — 传递 tenantId

**方案**：

```java
// 方案A（推荐）：Inner 接口增加 tenantId 参数
@GetMapping("/user/info/{username}")
public R<UserVO> getUserByUsername(
    @PathVariable("username") String username,
    @RequestParam(value = "tenantId", required = false) Long tenantId) {
    
    LambdaQueryWrapper<SysUserPo> wrapper = new LambdaQueryWrapper<SysUserPo>()
        .eq(SysUserPo::getUsername, username);
    if (tenantId != null) {
        wrapper.eq(SysUserPo::getTenantId, tenantId);
    }
    // ...使用 TenantHelper.ignore() 跳过自动拦截，手动控制租户条件
}
```

**注意**：登录接口是 Inner 调用，此时用户尚未登录、SecurityContext 无 tenantId，因此**不能依赖自动拦截器**，需手动传参。

---

### Task 1.2.4 — 前端登录页增加租户选择

**改动文件**：`han-ui/src/views/login/index.vue`

**UI 方案**：

```
┌─────────────────────────────┐
│         Han Cloud            │
│                              │
│  租户: [下拉选择 / 手动输入]  │  ← 新增
│  用户名: [____________]      │
│  密码:   [____________]      │
│  验证码: [____] [图片]       │
│                              │
│      [ 登 录 ]               │
└─────────────────────────────┘
```

**逻辑**：
1. 页面加载时调用 `GET /tenant/all` 获取有效租户列表
2. 或根据当前域名调用 `GET /tenant/domain/{domain}` 自动识别
3. 将 `tenantId` 放入 `LoginDTO` 提交
4. 登录成功后 store 保存 tenantId

**域名自动识别优先**：如果当前域名能匹配到租户，自动选中并隐藏租户选择器。

---

### Task 1.2.5 — CurrentUserVO 补全 tenantId

**改动文件**：`ASysUserController.getCurrentUserInfo()`

```java
// 修改前
.tenantId(null)

// 修改后（从 LoginUser / SecurityContext 获取）
.tenantId(SecurityContextHolder.getTenantId())
```

---

### Task 1.2.6 — 租户初始化流程

**目标**：创建新租户时自动初始化基础数据。

**改动文件**：`TenantServiceImpl.insert()` 方法

**初始化清单**：

```
创建租户记录 (sys_tenant)
  ↓
创建默认部门 (sys_dept)  — "XX公司" 根部门
  ↓
创建默认角色 (sys_role)  — "租户管理员" 角色
  ↓  
分配套餐菜单给角色 (sys_role_menu) — 从套餐菜单复制
  ↓
创建管理员用户 (sys_user) — 使用 TenantDTO.adminUsername/adminPassword
  ↓
绑定用户-角色 (sys_user_role)
  ↓
绑定用户-部门 — 设置 deptId
```

**关键点**：
- 整个流程在一个事务中 `@Transactional`
- 使用 `TenantHelper.ignore()` 创建租户记录本身（sys_tenant 不按租户过滤）
- 创建子数据时需手动设置 `tenantId`（此时 SecurityContext 的租户是管理员的租户）

**跨服务调用**：租户模块（han-tenant）需通过 `SystemServiceClient` 调用 han-system 创建用户/角色/部门。需在 `han-api-system` 增加对应的 Inner 接口：

```java
@HttpExchange("/inner/system")
public interface SystemServiceClient {
    // ... 现有接口
    
    @PostExchange("/tenant/init")
    R<Void> initTenantData(@RequestBody TenantInitDTO dto);
}
```

`han-system` 的 Inner 层增加 `initTenantData` 接口，接收初始化参数并创建部门/角色/用户。

---

### Task 1.2.7 — 租户安全删除

**改动文件**：`TenantController` + `ITenantService`

```java
// TenantController
@RequiresPermission("tenant:remove")
@PostMapping("/remove/{tenantId}")
public R<Void> remove(@PathVariable Long tenantId) {
    tenantService.deleteTenant(tenantId);
    return R.ok();
}
```

**删除策略**：
```
1. 检查租户下是否有活跃用户 → 有则拒绝（或强制停用）
2. 清理用户-角色关联 (sys_user_role WHERE tenant_id = ?)
3. 清理用户-岗位关联 (sys_user_post WHERE tenant_id = ?)
4. 清理角色-菜单关联 (sys_role_menu WHERE tenant_id = ?)
5. 逻辑删除用户 (sys_user WHERE tenant_id = ?)
6. 逻辑删除角色 (sys_role WHERE tenant_id = ?)
7. 逻辑删除部门 (sys_dept WHERE tenant_id = ?)
8. 逻辑删除租户 (sys_tenant WHERE id = ?)
9. 清理 Redis 中该租户的所有登录 Token
```

**建议**：采用软删除 + 定时清理策略，避免误删。增加确认机制（输入租户名确认）。

---

## 1.3 数据库变更

```sql
-- 确认 sys_user 表已有 tenant_id 列（BizEntity 继承链应已创建）
-- 如未创建，执行：
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

-- 创建索引加速租户过滤
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_tenant ON sys_role(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_dept_tenant ON sys_dept(tenant_id);
```

---

## 1.4 验证清单

- [ ] 不同租户创建同名用户不冲突
- [ ] 租户A的用户登录后只能看到租户A的数据
- [ ] 超级管理员（租户ID=1）可以管理所有租户
- [ ] 新增租户后自动创建管理员/角色/部门
- [ ] 前端登录页可选择/自动识别租户
- [ ] 停用租户后该租户用户无法登录
- [ ] 过期租户自动拒绝登录

---

# Phase 2 — 用户功能补全

## 2.1 个人中心

### 2.1.1 后端接口设计

**新增文件**：`ASysProfileController.java`（或在 `ASysUserController` 中追加）

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 获取个人信息 | GET | `/system/user/profile` | 免权限 | 含角色名、部门名 |
| 修改个人信息 | POST | `/system/user/profile/edit` | 免权限 | 昵称/手机/邮箱/性别 |
| 修改密码 | POST | `/system/user/profile/updatePwd` | 免权限 | 旧密码 + 新密码 |
| 上传头像 | POST | `/system/user/profile/avatar` | 免权限 | 调用文件服务上传 |

**Service 层新增**：

```java
// ISysUserService 新增方法
void updateProfile(Long userId, ProfileDTO dto);   // 仅允许修改昵称/手机/邮箱/性别
void updatePassword(Long userId, String oldPwd, String newPwd);  // 校验旧密码
void updateAvatar(Long userId, String avatarUrl);
```

**ProfileDTO**（新增）：

```java
@Data
public class ProfileDTO {
    @Size(max = 30) private String nickname;
    @Size(max = 11) private String phone;
    @Email @Size(max = 50) private String email;
    private Integer sex;
}
```

### 2.1.2 前端页面设计

**文件**：`han-ui/src/views/user/profile/index.vue`

**布局**：
```
┌─────────────────────────────────────────────────┐
│                   个人中心                        │
├──────────────┬──────────────────────────────────┤
│              │                                    │
│   [头像]     │  基本信息                          │
│   点击修改   │  ┌───────────────────────────┐    │
│              │  │ 用户名:  admin (不可改)    │    │
│  角色: 管理员 │  │ 昵称:    [__________]     │    │
│  部门: 技术部 │  │ 手机:    [__________]     │    │
│  创建时间:    │  │ 邮箱:    [__________]     │    │
│  2026-01-01  │  │ 性别:    ○男 ○女 ○未知    │    │
│              │  │                           │    │
│              │  │    [ 保存修改 ]            │    │
│              │  └───────────────────────────┘    │
│              │                                    │
│              │  修改密码                          │
│              │  ┌───────────────────────────┐    │
│              │  │ 旧密码:  [__________]     │    │
│              │  │ 新密码:  [__________]     │    │
│              │  │ 确认:    [__________]     │    │
│              │  │    [ 修改密码 ]            │    │
│              │  └───────────────────────────┘    │
└──────────────┴──────────────────────────────────┘
```

---

## 2.2 用户导入 / 导出

### 2.2.1 后端接口设计

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 导出用户 | GET | `/system/user/export` | `system:user:export` | 返回 Excel 文件流 |
| 下载导入模板 | GET | `/system/user/importTemplate` | `system:user:import` | 空模板 |
| 导入用户 | POST | `/system/user/import` | `system:user:import` | 上传 Excel |

**技术选型**：Apache POI 或 EasyExcel（推荐 EasyExcel，更轻量）

**依赖**：在 `han-system/pom.xml` 添加

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>4.0.3</version>
</dependency>
```

**导入逻辑**：

```
1. 解析 Excel → List<UserImportDTO>
2. 逐行校验：
   - 用户名必填、长度、唯一性
   - 手机号/邮箱格式
   - 部门名称是否存在
   - 角色名称是否存在
3. 校验通过 → 批量插入（带事务）
4. 返回结果：成功数 + 失败行明细
```

**导出字段**：用户ID、用户名、昵称、部门、手机、邮箱、性别、状态、创建时间

### 2.2.2 前端交互

在用户列表工具栏增加导入/导出按钮：

```
[新增] [删除] [导入▼] [导出]
                 ├─ 导入用户
                 └─ 下载模板
```

导入弹窗：
- 上传区域（拖拽 / 点击选择 Excel）
- 是否覆盖已存在用户（勾选）
- 导入结果展示（成功 X 条，失败 Y 条 + 详情下载）

---

## 2.3 部门树筛选用户

### 2.3.1 页面布局改造

将用户管理页面改为**左右分栏**布局：

```
┌──────────┬──────────────────────────────────────┐
│ 部门树    │ 搜索: [用户名] [手机] [状态] [搜索]   │
│          │                                        │
│ ▶ 总公司  │ [新增] [删除] [导入] [导出]            │
│   ├ 技术部│ ┌────────────────────────────────────┐│
│   ├ 市场部│ │ ID │用户名│昵称│部门│手机│状态│操作 ││
│   ├ 运营部│ │  1 │admin │...│技术│... │ ✓  │编辑 ││
│   └ 财务部│ │... │      │   │   │    │    │     ││
│          │ └────────────────────────────────────┘│
│          │ 共 25 条  < 1 2 3 >                    │
└──────────┴──────────────────────────────────────┘
```

### 2.3.2 改动点

**后端**：
- `SysUserQuery` 增加 `deptId` 字段
- `selectUserPage` 查询时：如果传了 `deptId`，查询该部门及所有子部门的用户
- 利用已有的 `ISysDeptService.selectDeptAndChildIds(deptId)` 获取子部门ID列表

```java
// SysUserQuery 新增
private Long deptId;

// Service 查询逻辑
if (query.getDeptId() != null) {
    List<Long> deptIds = deptService.selectDeptAndChildIds(query.getDeptId());
    wrapper.in(SysUserPo::getDeptId, deptIds);
}
```

**前端**：
- 左侧加载部门树（复用 `getDeptTree` API）
- 点击部门节点 → 设置 `queryParams.deptId` → 重新查询
- 点击根节点或清除 → `deptId = undefined` → 查询全部

---

## 2.4 岗位管理重构 & 联动

### 2.4.1 后端重构

**问题**：`ASysPostController` 直接注入 `SysPostMapper`，违反 AIB 架构。

**方案**：
1. 新建 `ISysPostService` + `SysPostServiceImpl`
2. 新建 `BSysPostController`（B 层）
3. 改造 `ASysPostController` 继承 B 层

### 2.4.2 用户-岗位联动增强

**用户列表**：增加岗位名称列展示

```java
// UserVO 已有 postIds，需补充 postNames
private Set<String> postNames;
```

**编辑用户弹窗**：岗位多选下拉已有，需确保数据回显正确（加载 `listAllPosts` 选项）。

---

# Phase 3 — 权限与安全

## 3.1 数据权限

### 3.1.1 概念设计

数据权限控制**同一租户内**不同用户能看到的数据范围。

**5 种数据权限级别**：

| 级别 | 编码 | 说明 |
|------|------|------|
| 全部数据 | `1` | 可查看租户内所有数据 |
| 自定义部门 | `2` | 可查看指定部门的数据 |
| 本部门数据 | `3` | 仅本部门数据 |
| 本部门及下级 | `4` | 本部门 + 所有子部门 |
| 仅本人 | `5` | 只能看自己的数据 |

### 3.1.2 数据库设计

```sql
-- sys_role 表增加数据权限字段
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope CHAR(1) DEFAULT '1';

-- 角色-部门关联表（自定义部门权限时使用）
CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, dept_id)
);
```

### 3.1.3 后端实现

**核心：MyBatis-Plus DataPermissionInterceptor**

新增 `DataPermissionHandler`，在 SELECT 查询时自动追加数据范围条件：

```java
@Slf4j
@RequiredArgsConstructor
public class HanDataPermissionHandler implements DataPermissionHandler {

    private final SecurityContext securityContext;
    
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        // 1. 超级管理员跳过
        if (securityContext.isAdmin()) return where;
        
        // 2. 获取用户最大数据权限范围
        String dataScope = getDataScope();
        
        // 3. 根据 dataScope 构建 SQL 条件
        return switch (dataScope) {
            case "1" -> where;  // 全部数据，不追加条件
            case "2" -> appendCustomDepts(where);  // IN (自定义部门列表)
            case "3" -> appendDeptEquals(where);    // = 本部门ID
            case "4" -> appendDeptAndChildren(where); // IN (本部门+子部门)
            case "5" -> appendSelfOnly(where);      // create_by = 当前用户ID
            default -> where;
        };
    }
}
```

**使用方式**：在需要数据权限的 Mapper 方法上添加 `@DataPermission` 注解：

```java
@DataPermission
List<SysUserPo> selectUserList(@Param("query") SysUserQuery query);
```

### 3.1.4 Inner 层补全

```java
// ISysUserController 修复
@GetMapping("/user/datascope/depts")
public R<Set<Long>> getDataScopeDeptIds(@RequestParam("userId") Long userId) {
    // 查询用户所有角色的 data_scope
    // 如果有自定义部门权限，查询 sys_role_dept
    return R.ok(sysUserService.selectDataScopeDeptIds(userId));
}

@GetMapping("/user/roles")
public R<List<RoleVO>> getRolesByUserId(@RequestParam("userId") Long userId) {
    List<SysRolePo> roles = roleService.selectRolesByUserId(userId);
    return R.ok(roles.stream().map(this::toApiRoleVO).toList());
}

@GetMapping("/dept/{deptId}")
public R<DeptVO> getDeptById(@PathVariable("deptId") Long deptId) {
    SysDeptPo dept = deptService.selectDeptById(deptId);
    return R.ok(toApiDeptVO(dept));
}
```

### 3.1.5 前端改动

角色编辑弹窗增加「数据权限」Tab：

```
┌─ 基本信息 ─┬─ 菜单权限 ─┬─ 数据权限 ─┐
│                                        │
│  数据范围: [下拉选择]                    │
│    ○ 全部数据                           │
│    ○ 自定义部门                         │
│    ○ 本部门数据                         │
│    ○ 本部门及下级                       │
│    ○ 仅本人                            │
│                                        │
│  [部门树 - 仅"自定义部门"时显示]         │
│  ☑ 总公司                              │
│    ☑ 技术部                            │
│    ☐ 市场部                            │
│                                        │
└────────────────────────────────────────┘
```

---

## 3.2 密码策略 & 账号安全

### 3.2.1 密码策略

**配置化**（通过 sys_config 或 application.yml）：

```yaml
security:
  password:
    min-length: 8
    max-length: 32
    require-uppercase: true
    require-lowercase: true
    require-digit: true
    require-special: true
    expire-days: 90           # 密码过期天数（0=不过期）
    history-count: 5          # 不能与最近N次密码相同
```

**数据库**：

```sql
-- sys_user 增加密码相关字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS pwd_update_time TIMESTAMP;  -- 最后修改密码时间

-- 密码历史表（防止重复使用）
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password VARCHAR(200) NOT NULL,    -- 加密后的密码
    create_time TIMESTAMP NOT NULL,
    CONSTRAINT fk_pwd_history_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_pwd_history_user ON sys_password_history(user_id);
```

**后端逻辑**：
1. 修改密码时检查密码复杂度
2. 修改密码时检查历史密码
3. 登录时检查密码是否过期 → 过期则返回特殊状态码，前端跳转强制改密页面

### 3.2.2 登录失败锁定

```yaml
security:
  login:
    max-retry: 5              # 最大失败次数
    lock-duration: 30         # 锁定时长（分钟）
```

**实现**（Redis 计数）：

```java
// AuthServiceImpl 中
private void checkLoginRetry(String username) {
    String retryKey = CacheConstants.LOGIN_RETRY_KEY + username;
    String retryCount = redisTemplate.opsForValue().get(retryKey);
    int count = retryCount != null ? Integer.parseInt(retryCount) : 0;
    
    if (count >= maxRetry) {
        throw new BusinessException("账号已锁定，请" + lockDuration + "分钟后重试");
    }
}

private void recordLoginFail(String username, String message) {
    String retryKey = CacheConstants.LOGIN_RETRY_KEY + username;
    Long count = redisTemplate.opsForValue().increment(retryKey);
    if (count == 1) {
        redisTemplate.expire(retryKey, Duration.ofMinutes(lockDuration));
    }
    log.warn("用户[{}]登录失败(第{}次): {}", username, count, message);
}

// 登录成功后清除计数
private void clearLoginRetry(String username) {
    redisTemplate.delete(CacheConstants.LOGIN_RETRY_KEY + username);
}
```

### 3.2.3 首次登录强制改密

**逻辑**：
- `SysUserPo` 增加 `pwdUpdateTime` 字段
- 新建用户时 `pwdUpdateTime = null`
- 登录时检查：`pwdUpdateTime == null` → 返回 `code: 601`（需修改密码）
- 前端收到 601 → 弹出强制改密对话框

---

## 3.3 在线用户管理

### 3.3.1 后端接口设计

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 在线用户列表 | GET | `/system/online/list` | `system:online:list` | 从 Redis 扫描 |
| 强制下线 | POST | `/system/online/forceLogout` | `system:online:forceLogout` | 删除 Token |

**实现**：扫描 Redis 中 `login_user:*` 的 Key，解析 LoginUser JSON。

```java
@GetMapping("/list")
public R<List<OnlineUserVO>> list(
    @RequestParam(required = false) String username,
    @RequestParam(required = false) String ipAddr) {
    
    // 扫描 Redis keys: token:*
    Set<String> keys = redisTemplate.keys(CacheConstants.TOKEN_KEY + "*");
    List<OnlineUserVO> list = keys.stream()
        .map(key -> {
            String json = redisTemplate.opsForValue().get(key);
            LoginUser loginUser = XuJsonUtil.parseObject(json, LoginUser.class);
            return toOnlineUserVO(loginUser, key);
        })
        .filter(vo -> matchFilter(vo, username, ipAddr))
        .sorted(Comparator.comparing(OnlineUserVO::getLoginTime).reversed())
        .toList();
    
    return R.ok(list);
}
```

**OnlineUserVO**：

```java
@Builder
public record OnlineUserVO(
    String tokenId,
    Long userId,
    String username,
    String nickname,
    String deptName,
    String ipAddr,
    String browser,
    String os,
    String clientType,
    Long loginTime
) {}
```

### 3.3.2 前端页面

**路由**：取消 `/system/online` 路由的注释

```
┌──────────────────────────────────────────────┐
│ 在线用户                                      │
│                                                │
│ 搜索: [用户名] [IP地址] [搜索] [重置]          │
│                                                │
│ ┌──────────────────────────────────────────┐  │
│ │用户名│昵称│部门│IP│客户端│登录时间│操作    │  │
│ │admin │管理│技术│..│PC    │10:30  │[下线]  │  │
│ │user1 │张三│市场│..│APP   │09:15  │[下线]  │  │
│ └──────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

---

## 3.4 登录日志完善

### 3.4.1 现状

`AuthServiceImpl` 中有 TODO 注释：
```java
// TODO: 记录到登录日志表
// TODO: 实现登录失败次数限制
// TODO: 更新用户最后登录时间和IP
```

### 3.4.2 实现方案

**数据库表**（确认 sys_login_log 是否已建）：

```sql
CREATE TABLE IF NOT EXISTS sys_login_log (
    id          BIGINT PRIMARY KEY,
    username    VARCHAR(50),
    tenant_id   BIGINT,
    ip_addr     VARCHAR(128),
    status      SMALLINT,      -- 0成功 1失败
    message     VARCHAR(255),
    client_type VARCHAR(20),
    browser     VARCHAR(50),
    os          VARCHAR(50),
    login_time  TIMESTAMP NOT NULL
);
CREATE INDEX idx_login_log_time ON sys_login_log(login_time DESC);
CREATE INDEX idx_login_log_user ON sys_login_log(username);
```

**实现**：通过 RPC 调用 han-system 的 Inner 接口异步记录：

```java
// AuthServiceImpl 中
private void recordLoginSuccess(Long userId, String username, ClientType clientType) {
    // 异步记录，不影响登录响应
    CompletableFuture.runAsync(() -> {
        systemServiceClient.recordLoginLog(LoginLogDTO.builder()
            .username(username)
            .status(0)
            .message("登录成功")
            .clientType(clientType.getCode())
            .build());
    });
}
```

同时更新 `sys_user.login_ip` 和 `sys_user.login_time`。

---

# 附：工作量估算

| Phase | 任务 | 后端工时 | 前端工时 | 总计 |
|-------|------|---------|---------|------|
| **1** | 租户链路打通（B1-B3） | 2d | 0.5d | 2.5d |
| **1** | 前端登录租户选择（B4） | - | 1d | 1d |
| **1** | CurrentUserVO 修复（B5） | 0.5d | - | 0.5d |
| **1** | 租户初始化流程（B6） | 2d | - | 2d |
| **1** | 租户删除（B7） | 1d | 0.5d | 1.5d |
| **2** | 个人中心 | 1d | 1.5d | 2.5d |
| **2** | 用户导入导出 | 2d | 1d | 3d |
| **2** | 部门树筛选 | 0.5d | 1d | 1.5d |
| **2** | 岗位重构 + 联动 | 1d | 0.5d | 1.5d |
| **3** | 数据权限 | 3d | 1d | 4d |
| **3** | 密码策略 + 锁定 | 1.5d | 0.5d | 2d |
| **3** | 在线用户管理 | 1d | 1d | 2d |
| **3** | 登录日志完善 | 1d | - | 1d |
| | **总计** | **16.5d** | **8.5d** | **25d** |

---

# 附：文件改动清单

## Phase 1 涉及文件

### 后端新增
- `han-system/controller/inner/TenantInitController.java` — 租户初始化 Inner 接口
- `han-api-system/domain/TenantInitDTO.java` — 初始化参数

### 后端修改
- `han-common-security` — SecurityContext 实现确认 tenantId 来源
- `han-gateway` — 全局过滤器追加 X-Tenant-Id Header
- `han-system/controller/inner/ISysUserController.java` — getUserByUsername 增加 tenantId
- `han-api-system/SystemServiceClient.java` — 接口参数调整
- `han-auth/service/impl/AuthServiceImpl.java` — 登录传递 tenantId
- `han-system/controller/admin/ASysUserController.java` — CurrentUserVO 修复
- `han-tenant/controller/TenantController.java` — 增加 remove 接口
- `han-tenant/service/ITenantService.java` — 增加 deleteTenant、initTenant
- `han-tenant/service/impl/TenantServiceImpl.java` — 初始化/删除实现

### 前端修改
- `han-ui/src/views/login/index.vue` — 租户选择
- `han-ui/src/stores/user.ts` — 存储 tenantId
- `han-ui/src/api/system/tenant.ts` — 新增 listAllTenants

## Phase 2 涉及文件

### 后端新增
- `han-system/controller/admin/ASysProfileController.java`
- `han-system/domain/dto/ProfileDTO.java`
- `han-system/service/ISysPostService.java` + impl
- `han-system/controller/base/BSysPostController.java`

### 后端修改
- `han-system/service/ISysUserService.java` — 增加 profile 方法
- `han-system/domain/query/SysUserQuery.java` — 增加 deptId
- `han-system/controller/admin/ASysPostController.java` — 重构
- `han-system/controller/admin/ASysUserController.java` — 导入导出接口

### 前端新增/修改
- `han-ui/src/views/user/profile/index.vue` — 个人中心页面重写
- `han-ui/src/views/system/user/index.vue` — 左右分栏 + 导入导出按钮
- `han-ui/src/api/system/user.ts` — 新增 profile/import/export API

## Phase 3 涉及文件

### 后端新增
- `han-common-mybatis/handler/HanDataPermissionHandler.java`
- `han-common-mybatis/annotation/DataPermission.java`
- `han-system/domain/po/SysRoleDeptPo.java`
- `han-system/mapper/SysRoleDeptMapper.java`
- `han-system/domain/po/SysPasswordHistoryPo.java`
- `han-system/controller/admin/ASysOnlineController.java`
- `han-system/domain/vo/OnlineUserVO.java`

### 后端修改
- `han-common-mybatis/config/MybatisPlusConfig.java` — 注册 DataPermission 插件
- `han-system/domain/po/SysRolePo.java` — 增加 dataScope
- `han-system/domain/po/SysUserPo.java` — 增加 pwdUpdateTime
- `han-system/service/impl/SysRoleServiceImpl.java` — 数据权限保存
- `han-auth/service/impl/AuthServiceImpl.java` — 登录失败锁定 + 日志记录

### 前端新增/修改
- `han-ui/src/views/system/role/index.vue` — 数据权限 Tab
- `han-ui/src/views/system/online/index.vue` — 在线用户页面（新建）
- `han-ui/src/router/index.ts` — 取消 online 路由注释

### SQL
- `sql/upgrade/phase1_tenant.sql`
- `sql/upgrade/phase2_user.sql`
- `sql/upgrade/phase3_security.sql`

---

# Phase 4 — 管理优化

## 4.1 #12 角色分配用户

### 4.1.1 问题诊断

```
当前角色管理:
ASysRoleController → CRUD + 菜单分配 ← ✅ 已有
                   → 查看/分配/取消角色下用户 ← ❌ 缺失

已有基础:
- ISysRoleService.countUserByRoleId() ← 统计方法已有
- SysUserRolePo(userId, roleId) 关联表 ← 已存在
- SysUserRoleMapper extends BaseMapper ← 已有
- 前端 role/index.vue ← 无用户分配入口
```

### 4.1.2 后端接口设计

**在 `ASysRoleController` 中新增**：

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 角色已分配用户列表 | GET | `/system/role/authUser/list` | `system:role:list` | 分页，支持用户名/手机筛选 |
| 角色未分配用户列表 | GET | `/system/role/authUser/unallocated` | `system:role:list` | 排除已分配用户 |
| 批量授权用户 | POST | `/system/role/authUser/selectAll` | `system:role:edit` | body: `{roleId, userIds[]}` |
| 批量取消授权 | POST | `/system/role/authUser/cancel` | `system:role:edit` | body: `{roleId, userIds[]}` |

**Service 层新增方法**：

```java
// ISysRoleService
PageResult<SysUserDto> selectAllocatedUsers(Long roleId, SysUserQuery query);
PageResult<SysUserDto> selectUnallocatedUsers(Long roleId, SysUserQuery query);
void authUsers(Long roleId, List<Long> userIds);
void cancelAuthUsers(Long roleId, List<Long> userIds);
```

**Mapper 层**：`SysUserRoleMapper` 已有 BaseMapper，新增自定义 SQL 查询已分配/未分配用户列表（JOIN sys_user_role）。

### 4.1.3 前端页面设计

角色列表操作列增加「分配用户」按钮，点击后跳转独立页面 `role/authUser.vue`：

```
┌──────────────────────────────────────────────────────┐
│ ← 返回角色列表    角色「系统管理员」的用户分配        │
├──────────────────────────────────────────────────────┤
│ 搜索: [用户名] [手机号] [搜索] [重置]                │
│                                                       │
│ [添加用户]  [批量取消授权]                             │
│ ┌────────────────────────────────────────────────┐   │
│ │ ☐ │用户名│昵称│部门│手机│状态│操作              │   │
│ │ ☐ │admin │管理│技术│... │ ✓ │[取消授权]        │   │
│ │ ☐ │user1 │张三│市场│... │ ✓ │[取消授权]        │   │
│ └────────────────────────────────────────────────┘   │
│ 共 5 条  < 1 >                                       │
└──────────────────────────────────────────────────────┘
```

「添加用户」弹窗：展示未分配用户列表，支持多选 + 批量添加。

### 4.1.4 数据库变更

无变更（`sys_user_role` 表已存在）。

### 4.1.5 验证清单

- [ ] 角色列表显示「分配用户」按钮
- [ ] 已分配用户列表正确展示、支持搜索
- [ ] 未分配用户列表正确排除已授权用户
- [ ] 批量授权后用户列表刷新
- [ ] 批量取消后用户-角色关联删除
- [ ] 超级管理员角色(id=1)不允许取消授权

---

## 4.2 #4 租户安全删除

### 4.2.1 问题诊断

```
当前状态:
TenantController → add/edit/changeStatus/syncPackage ← ✅ 已有
                → delete/remove                      ← ❌ 缺失

ITenantService → 无 deleteTenant 方法

租户关联数据分布:
sys_user (tenant_id)
sys_role (tenant_id)
sys_dept (tenant_id)
sys_post (tenant_id)
sys_user_role (通过 user_id 间接关联)
sys_user_post (通过 user_id 间接关联)
sys_role_menu (通过 role_id 间接关联)
Redis: login_user:* (该租户用户的登录 Token)
```

### 4.2.2 后端接口设计

**TenantController 新增**：

```java
@RequiresPermission("tenant:remove")
@PostMapping("/remove/{tenantId}")
public R<Void> remove(@PathVariable Long tenantId) {
    tenantService.deleteTenant(tenantId);
    return R.ok();
}
```

### 4.2.3 删除流程（事务内）

```
1. 校验: 禁止删除平台租户(id=1)
2. 校验: 确认租户存在且未被逻辑删除
3. 清理 Redis: 扫描删除该租户所有用户的 login_user:* Token（强制全部下线）
4. 清理关联表（物理删除，使用 @IgnoreTenant 跳过拦截器）:
   ├── DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = ?)
   ├── DELETE FROM sys_user_post WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = ?)
   └── DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id = ?)
5. 逻辑删除业务数据:
   ├── UPDATE sys_user SET del_flag = 1 WHERE tenant_id = ?
   ├── UPDATE sys_role SET del_flag = 1 WHERE tenant_id = ?
   ├── UPDATE sys_dept SET del_flag = 1 WHERE tenant_id = ?
   └── UPDATE sys_post SET del_flag = 1 WHERE tenant_id = ?
6. 逻辑删除租户本体:
   └── UPDATE sys_tenant SET del_flag = 1 WHERE id = ?
```

### 4.2.4 跨服务调用

租户模块（han-tenant）通过 `SystemServiceClient` 调用 han-system 的 Inner 接口清理业务数据：

```java
// SystemServiceClient 新增
@PostExchange("/inner/system/tenant/cleanup")
R<Void> cleanupTenantData(@RequestParam Long tenantId);
```

han-system 新增 `TenantCleanupController`（Inner 层），执行步骤 4-5 的数据清理。

### 4.2.5 前端交互

租户列表操作列增加「删除」按钮（danger 样式）：
1. 点击后弹出确认框
2. 确认框要求**输入租户名称**（防误删）
3. 输入内容与租户名匹配后才允许提交
4. 提交后调用 `POST /tenant/remove/{tenantId}`

### 4.2.6 验证清单

- [ ] 平台租户(id=1)删除时返回错误
- [ ] 输入租户名不匹配时无法提交
- [ ] 删除后该租户用户全部强制下线（Redis Token 清除）
- [ ] 删除后该租户数据全部逻辑删除
- [ ] 删除后关联表物理清理完成

---

## 4.3 #16 租户切换

### 4.3.1 问题诊断

```
当前状态:
- LoginUser.tenantId 已有 ← ✅
- LoginDTO.tenantId 登录时传入 ← ✅
- sys_user 每用户只有一个 tenant_id ← 无多租户关联模型
- 无切换接口 ← ❌

数据模型:
同一用户名(如 admin)可在不同租户下创建独立账号:
  sys_user: id=1, username=admin, tenant_id=1  (平台管理员)
  sys_user: id=50, username=admin, tenant_id=2 (租户A管理员)
```

### 4.3.2 策略选择

**推荐策略**：同一用户名在不同租户是独立账号，切换 = 携带新 tenantId 重新鉴权。

- 优点：无需新建关联表，复用现有登录链路，权限天然隔离
- 缺点：需要用户在多个租户分别有账号

### 4.3.3 后端接口设计

| 接口 | 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|------|
| 我的租户列表 | GET | `/auth/myTenants` | 已登录 | 查询当前用户名在所有租户的账号 |
| 切换租户 | POST | `/auth/switchTenant` | 已登录 | 参数 `{tenantId}`，重新签发 Token |

**`/auth/myTenants` 实现**：

```java
@IgnoreTenant
public List<TenantSimpleVO> getMyTenants(String username) {
    // 查询 sys_user WHERE username = ? AND del_flag = 0
    // JOIN sys_tenant 获取租户名称和状态
    // 仅返回有效租户
    return tenantSimpleVOList;
}
```

**`/auth/switchTenant` 流程**：

```
1. 获取当前 LoginUser.username
2. @IgnoreTenant 查新租户下同名用户
3. 用户不存在 → 返回错误"您在该租户下无账号"
4. 租户已停用/过期 → 返回错误
5. 用户存在 → 重新构建 LoginUser（新 tenantId、权限、角色）
6. 删除旧 Token（Redis）
7. 签发新 Token
8. 返回 LoginVO（与登录接口格式一致）
```

**TenantSimpleVO**：

```java
@Builder
public record TenantSimpleVO(
    Long tenantId,
    String tenantName,
    Integer status,     // 0正常 1停用
    boolean isCurrent   // 是否当前租户
) {}
```

### 4.3.4 前端交互

**Navbar 右上角**增加租户标识：

```
┌────────────────────────────────────────────────────┐
│  logo  Han Cloud          [租户A ▼] [头像 admin ▼] │
└────────────────────────────────────────────────────┘
```

点击「租户A ▼」弹出租户切换弹窗：

```
┌───────────────────────┐
│    切换租户             │
│                        │
│  ● 租户A (当前)        │
│  ○ 租户B               │
│  ○ 平台管理            │
│                        │
│  [ 取消 ] [ 确认切换 ] │
└───────────────────────┘
```

选择目标租户 → 调用 `switchTenant` → store 更新 Token/用户信息 → 刷新页面重载菜单。

### 4.3.5 验证清单

- [ ] Navbar 正确显示当前租户名
- [ ] myTenants 正确返回用户在各租户的账号
- [ ] 切换到有账号的租户：Token 刷新、菜单重载
- [ ] 切换到无账号的租户：提示错误
- [ ] 切换到已停用租户：提示错误
- [ ] 切换后数据隔离正确（只看到新租户数据）

---

## 4.4 #18 操作日志补全

### 4.4.1 问题诊断

```
当前状态:
@OperLog 注解 ← ✅ 已定义（han-common-log），含 module/type/saveParams/saveResult
OperType 枚举 ← ✅ OTHER/INSERT/UPDATE/DELETE/SELECT/QUERY/EXPORT/IMPORT/GRANT/FORCE_LOGOUT/CLEAN
AOP 切面     ← ❌ 完全缺失（han-common-log 只有注解类）
SysOperLogPo ← ❌ 不存在
Mapper       ← ❌ 不存在
Service      ← ❌ 不存在
Controller   ← ⚠️ ASysLogController 所有方法返回空桩
前端页面      ← ✅ operlog/index.vue 已完成，但无数据
```

### 4.4.2 数据库设计

```sql
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT,
    module          VARCHAR(50),          -- 模块名称（@OperLog.module）
    oper_type       SMALLINT DEFAULT 0,   -- 操作类型（OperType 枚举序号）
    oper_name       VARCHAR(50),          -- 操作人员（SecurityContext.getNickname()）
    oper_user_id    BIGINT,               -- 操作人ID
    dept_name       VARCHAR(50),          -- 部门名称
    oper_url        VARCHAR(255),         -- 请求URL
    oper_ip         VARCHAR(128),         -- 操作IP（从 HttpServletRequest 获取）
    request_method  VARCHAR(10),          -- 请求方式 GET/POST
    oper_param      TEXT,                 -- 请求参数（JSON，@OperLog.saveParams 控制）
    json_result     TEXT,                 -- 返回结果（JSON，@OperLog.saveResult 控制）
    status          SMALLINT DEFAULT 0,   -- 0成功 1失败
    error_msg       TEXT,                 -- 异常信息
    cost_time       BIGINT DEFAULT 0,     -- 耗时(ms)
    oper_time       TIMESTAMP NOT NULL    -- 操作时间
);

CREATE INDEX idx_oper_log_time ON sys_oper_log(oper_time DESC);
CREATE INDEX idx_oper_log_tenant ON sys_oper_log(tenant_id);
CREATE INDEX idx_oper_log_user ON sys_oper_log(oper_name);
```

### 4.4.3 后端实现

**新增文件清单**：

| 文件 | 模块 | 说明 |
|------|------|------|
| `OperLogAspect.java` | han-common-log | AOP 切面，@Around 处理 @OperLog |
| `OperLogAutoConfiguration.java` | han-common-log | 自动配置，注册 Aspect Bean |
| `IOperLogService.java` | han-common-log | 日志写入接口（SPI 模式） |
| `SysOperLogPo.java` | han-system | 持久化对象 |
| `SysOperLogQuery.java` | han-system | 查询对象（module/operType/operName/status/时间范围） |
| `SysOperLogMapper.java` | han-system | Mapper 接口 |
| `ISysOperLogService.java` | han-system | 服务接口 |
| `SysOperLogServiceImpl.java` | han-system | 服务实现（同时实现 IOperLogService） |

**OperLogAspect 核心逻辑**：

```java
@Aspect
@RequiredArgsConstructor
public class OperLogAspect {

    private final IOperLogService operLogService;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            // 异步记录日志（不影响业务响应时间）
            recordLogAsync(joinPoint, operLog, result, error, costTime);
        }
    }
    
    private void recordLogAsync(ProceedingJoinPoint jp, OperLog operLog, 
                                Object result, Throwable error, long costTime) {
        // 从当前线程快照上下文（异步前）
        Long userId = SecurityContextHolder.getUserId();
        Long tenantId = SecurityContextHolder.getTenantId();
        String username = SecurityContextHolder.getUsername();
        HttpServletRequest request = getRequest();
        
        CompletableFuture.runAsync(() -> {
            OperLogEvent event = OperLogEvent.builder()
                .tenantId(tenantId)
                .module(operLog.module())
                .operType(operLog.type().ordinal())
                .operName(username)
                .operUserId(userId)
                .operUrl(request.getRequestURI())
                .operIp(IpUtil.getIpAddr(request))
                .requestMethod(request.getMethod())
                .operParam(operLog.saveParams() ? getParams(jp) : null)
                .jsonResult(operLog.saveResult() ? toJson(result) : null)
                .status(error == null ? 0 : 1)
                .errorMsg(error != null ? error.getMessage() : null)
                .costTime(costTime)
                .operTime(LocalDateTime.now())
                .build();
            operLogService.recordOperLog(event);
        });
    }
}
```

**SPI 模式**：`han-common-log` 定义 `IOperLogService` 接口，`han-system` 实现该接口，通过 Spring Bean 注入。这样 common-log 模块不依赖 system 模块。

### 4.4.4 Controller 改造

将 `ASysLogController` 操作日志部分的空桩替换为真实 Service 调用：

```java
// 改造前
@GetMapping("/system/operlog/list")
public R<PageResult<Object>> listOperLog(...) {
    return R.ok(new PageResult<>(List.of(), 0L));  // 空桩
}

// 改造后
@GetMapping("/system/operlog/list")
public R<PageResult<SysOperLogPo>> listOperLog(SysOperLogQuery query) {
    return R.ok(operLogService.selectPage(query));
}
```

### 4.4.5 批量添加 @OperLog 注解

在以下 Controller 的写操作方法上添加 `@OperLog`：

| Controller | 方法 | 注解 |
|-----------|------|------|
| `ASysUserController` | add/edit/remove/resetPwd | `@OperLog(module="用户管理", type=INSERT/UPDATE/DELETE)` |
| `ASysRoleController` | add/edit/remove/changeStatus | `@OperLog(module="角色管理", type=...)` |
| `ASysDeptController` | add/edit/remove | `@OperLog(module="部门管理", type=...)` |
| `ASysPostController` | add/edit/remove | `@OperLog(module="岗位管理", type=...)` |
| `ASysMenuController` | add/edit/remove | `@OperLog(module="菜单管理", type=...)` |
| `TenantController` | add/edit/changeStatus/remove | `@OperLog(module="租户管理", type=...)` |

### 4.4.6 验证清单

- [ ] 添加用户后，操作日志列表出现一条 INSERT 记录
- [ ] 日志详情包含请求参数和返回结果
- [ ] 操作失败时记录错误信息和失败状态
- [ ] 耗时统计正确
- [ ] 按模块/类型/操作人/状态/时间范围筛选正常
- [ ] 批量删除和清空功能正常
- [ ] 异步记录不影响业务接口响应时间

---

## 4.5 #19 岗位管理重构

### 4.5.1 问题诊断

```
当前状态:
ASysPostController:
  ├── 直接注入 SysPostMapper ← ❌ 违反 AIB 架构（应注入 Service）
  ├── 无 ISysPostService     ← ❌ 缺失 Service 层
  ├── 无 B 层 Controller     ← ❌ 缺失基础层
  ├── 直接用 PO 接收请求     ← ❌ 应使用 DTO
  ├── 无 Query 对象          ← ❌ 查询参数散落在方法参数中
  ├── 无输入校验             ← ❌ 无 @NotBlank/@Valid
  └── 删除无校验             ← ❌ 未检查用户关联

已有基础:
- SysPostPo extends BizEntity ← ✅ 继承链正确
- SysPostMapper extends BaseMapper ← ✅
- SysUserPostPo(userId, postId) 关联表 ← ✅
- SysUserPostMapper ← ✅
- 前端 post/index.vue ← ✅ UI 已完成
```

### 4.5.2 新建 Service 层

```java
// ISysPostService.java
public interface ISysPostService {
    PageResult<SysPostPo> selectPostPage(SysPostQuery query);
    List<SysPostPo> selectPostList(SysPostQuery query);
    SysPostPo selectPostById(Long postId);
    void insertPost(SysPostDto dto);
    void updatePost(SysPostDto dto);
    void deletePostById(Long postId);
    void deletePostByIds(List<Long> postIds);
    boolean checkPostNameUnique(String postName, Long postId);
    boolean checkPostCodeUnique(String postCode, Long postId);
    long countUserByPostId(Long postId);
}
```

### 4.5.3 新建 DTO + Query

```java
// SysPostDto.java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SysPostDto {
    private Long postId;
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过50个字符")
    private String postName;
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过64个字符")
    private String postCode;
    private Integer postSort;
    private Integer status;
    private String remark;
}

// SysPostQuery.java
@Data
public class SysPostQuery {
    private String postCode;
    private String postName;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
```

### 4.5.4 重构 Controller（AIB 三层）

**B 层**（新建）：

```java
// BSysPostController.java — 不加 @RestController
public class BSysPostController {
    @Autowired
    protected ISysPostService postService;
    
    protected String getNodeName() { return "岗位管理"; }
    
    public R<PageResult<SysPostPo>> list(SysPostQuery query) {
        return R.ok(postService.selectPostPage(query));
    }
    public R<List<SysPostPo>> listAll() { ... }
    public R<SysPostPo> getInfo(Long postId) { ... }
    public R<Void> add(SysPostDto dto) { ... }
    public R<Void> edit(SysPostDto dto) { ... }
    public R<Void> remove(Long postId) { ... }
}
```

**A 层**（重构）：

```java
@AdminAuth
@RestController("adminSysPostController")
@RequestMapping("/system/post")
public class ASysPostController extends BSysPostController {
    
    @Override
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('system:post:list')")
    public R<PageResult<SysPostPo>> list(SysPostQuery query) {
        return super.list(query);
    }
    
    @Override
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:post:add')")
    @OperLog(module = "岗位管理", type = OperLog.OperType.INSERT)
    public R<Void> add(@Valid @RequestBody SysPostDto dto) {
        return super.add(dto);
    }
    
    // ... edit/remove 同理
}
```

### 4.5.5 删除校验

```java
// SysPostServiceImpl.deletePostById()
public void deletePostById(Long postId) {
    long count = countUserByPostId(postId);
    if (count > 0) {
        throw new BusinessException("该岗位已分配" + count + "名用户，不能删除");
    }
    postMapper.deleteById(postId);
}
```

### 4.5.6 验证清单

- [ ] 岗位 CRUD 功能正常（前端无需改动）
- [ ] 新增岗位校验名称/编码唯一性
- [ ] 删除已分配用户的岗位时提示错误
- [ ] Controller 不再直接依赖 Mapper
- [ ] API 路径不变，前端兼容

---

# 附：Phase 4 工作量估算

| 子项 | 后端工时 | 前端工时 | 总计 |
|------|---------|---------|------|
| #12 角色分配用户 | 1.5d | 1.5d | 3d |
| #4 租户安全删除 | 2d | 0.5d | 2.5d |
| #16 租户切换 | 2d | 1d | 3d |
| #18 操作日志补全 | 3d | 0.5d | 3.5d |
| #19 岗位管理重构 | 1d | 0.5d | 1.5d |
| **Phase 4 合计** | **9.5d** | **4d** | **13.5d** |

# 附：全量工时汇总

| Phase | 后端 | 前端 | 总计 |
|-------|------|------|------|
| Phase 1 租户隔离核心 | 5.5d | 1.5d | 7d |
| Phase 2 用户功能补全 | 4.5d | 4d | 8.5d |
| Phase 3 权限与安全 | 6.5d | 2.5d | 9d |
| Phase 4 管理优化 | 9.5d | 4d | 13.5d |
| **总计** | **26d** | **12d** | **38d** |

# 附：Phase 4 文件改动清单

## 后端新增
- `han-common-log/aspect/OperLogAspect.java` — AOP 切面
- `han-common-log/config/OperLogAutoConfiguration.java` — 自动配置
- `han-common-log/service/IOperLogService.java` — 日志写入 SPI 接口
- `han-common-log/domain/OperLogEvent.java` — 日志事件对象
- `han-system/domain/po/SysOperLogPo.java` — 操作日志 PO
- `han-system/domain/query/SysOperLogQuery.java` — 查询对象
- `han-system/mapper/SysOperLogMapper.java`
- `han-system/service/ISysOperLogService.java` + `SysOperLogServiceImpl.java`
- `han-system/domain/dto/SysPostDto.java`
- `han-system/domain/query/SysPostQuery.java`
- `han-system/service/ISysPostService.java` + `SysPostServiceImpl.java`
- `han-system/controller/base/BSysPostController.java`
- `han-system/controller/inner/TenantCleanupController.java`

## 后端修改
- `ASysRoleController.java` — 增加 authUser 4个接口 + @OperLog
- `ISysRoleService.java` — 增加分配用户方法
- `SysRoleServiceImpl.java` — 实现分配用户
- `ASysLogController.java` — 操作日志空桩替换为真实调用
- `ASysPostController.java` — 重构为 AIB 架构
- `ASysUserController.java` — 添加 @OperLog
- `ASysDeptController.java` — 添加 @OperLog
- `ASysMenuController.java` — 添加 @OperLog
- `TenantController.java` — 增加 remove 接口 + @OperLog
- `ITenantService.java` — 增加 deleteTenant
- `TenantServiceImpl.java` — 实现安全删除
- `AuthController.java` — 增加 switchTenant / myTenants
- `IAuthService.java` — 增加切换方法
- `AuthServiceImpl.java` — 实现租户切换
- `SystemServiceClient.java` — 增加 cleanupTenantData

## 前端新增
- `views/system/role/authUser.vue` — 角色分配用户页面

## 前端修改
- `views/system/role/index.vue` — 增加「分配用户」按钮
- `views/system/tenant/index.vue` — 增加「删除」按钮 + 确认弹窗
- `layout/components/Navbar.vue` — 增加租户切换入口
- `api/system/role.ts` — 新增 authUser 相关 API
- `api/system/tenant.ts` — 新增 removeTenant API
- `api/auth.ts` — 新增 switchTenant / myTenants API
- `router/index.ts` — 添加 authUser 路由

## SQL
- `sql/upgrade/phase4_management.sql`

## 实施顺序（Phase 4 内部）

```
#19 岗位重构（无依赖，最简单）
  ↓
#18 操作日志补全（无依赖，可并行）
  ↓
#12 角色分配用户（依赖角色模块稳定）
  ↓
#4 租户安全删除（依赖 Phase 1 租户初始化完成）
  ↓
#16 租户切换（依赖 Phase 1 租户链路打通）
```
