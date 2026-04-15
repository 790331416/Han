# Han Cloud Phase 1-4 变更日志

> 生成时间: 2026-03-03
> Maven 编译验证: ✅ BUILD SUCCESS (32 模块全部通过)

---

## 新增文件 (40 个)

### han-api-system
- `domain/TenantInitDto.java` — 租户初始化参数 DTO

### han-auth
- `domain/TenantSimpleVo.java` — 租户简要信息 VO（租户切换）

### han-common-log
- `aspect/OperLogAspect.java` — 操作日志 AOP 切面
- `config/OperLogAutoConfiguration.java` — 自动配置
- `domain/OperLogEvent.java` — 日志事件对象
- `service/IOperLogService.java` — 日志写入 SPI 接口
- `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### han-common-mybatis
- `annotation/DataPermission.java` — 数据权限注解
- `handler/HanDataPermissionHandler.java` — 数据权限处理器

### han-system (controller/admin)
- `ASysLoginLogController.java` — 登录日志 A 层
- `ASysOnlineController.java` — 在线用户 A 层
- `ASysOperLogController.java` — 操作日志 A 层
- `ASysProfileController.java` — 个人中心 A 层

### han-system (controller/base)
- `BSysPostController.java` — 岗位管理 B 层

### han-system (controller/inner)
- `TenantCleanupController.java` — 租户数据清理 I 层
- `TenantInitController.java` — 租户初始化 I 层

### han-system (converter)
- `SysPostConverter.java` — 岗位 MapStruct 转换器
- `SysUserApiConverter.java` — 用户 API VO MapStruct 转换器

### han-system (domain)
- `dto/ProfileDto.java` — 个人信息修改 DTO
- `dto/SysPostDto.java` — 岗位 DTO
- `po/SysLoginLogPo.java` — 登录日志 PO
- `po/SysOperLogPo.java` — 操作日志 PO
- `po/SysRoleDeptPo.java` — 角色部门关联 PO
- `query/SysOperLogQuery.java` — 操作日志查询
- `query/SysPostQuery.java` — 岗位查询
- `vo/UserExportVo.java` — 用户导出 VO (EasyExcel)
- `vo/UserImportVo.java` — 用户导入 VO (EasyExcel)

### han-system (mapper)
- `SysLoginLogMapper.java`
- `SysOperLogMapper.java`
- `SysRoleDeptMapper.java`

### han-system (service)
- `ISysLoginLogService.java` + `impl/SysLoginLogServiceImpl.java`
- `ISysOperLogService.java` + `impl/SysOperLogServiceImpl.java`
- `ISysPostService.java` + `impl/SysPostServiceImpl.java`

### han-ui (前端)
- `api/system/profile.ts` — 个人中心 API
- `views/system/role/authUser.vue` — 角色分配用户页面

### SQL
- `sql/upgrades/postgres/phase1_tenant.sql`
- `sql/upgrades/postgres/phase3_security.sql`
- `sql/upgrades/postgres/phase4_management.sql`

### 文档
- `doc/design-phase1-2-3.md` — Phase 1-4 详细设计文档

---

## 修改文件 (37 个)

### Maven POM
- `pom.xml` — 添加 easyexcel.version + dependencyManagement
- `han-modules/han-system/pom.xml` — 添加 EasyExcel + Redis 依赖

### han-api-system
- `SystemServiceClient.java` — 新增 tenantId 参数、cleanupTenantData、initTenantData、getUserTenants

### han-auth
- `AuthController.java` — 新增 myTenants、switchTenant 端点
- `IAuthService.java` — 新增 getMyTenants、switchTenant 方法
- `AuthServiceImpl.java` — 实现租户切换、登录失败锁定 (Redis 计数)

### han-system (controller/admin)
- `ASysDeptController.java` — 添加 @OperLog
- `ASysMenuController.java` — 添加 @OperLog
- `ASysPostController.java` — **重构为 AIB 架构** (继承 BSysPostController)
- `ASysRoleController.java` — 添加 @OperLog + 4 个 authUser 端点
- `ASysUserController.java` — 添加 @OperLog + 导入导出端点 + CurrentUserVO.tenantId 修复

### han-system (controller/inner)
- `ISysUserController.java` — getUserByUsername 增加 tenantId + getUserTenants + MapStruct 转换

### han-system (domain)
- `vo/UserVO.java` — 添加 postNames 字段

### han-system (service)
- `ISysRoleService.java` — 新增 4 个 authUser 方法
- `ISysUserService.java` — 新增 profile/importUsers 方法
- `SysRoleServiceImpl.java` — 实现 authUser 4 方法 + 添加 SysUserMapper
- `SysUserServiceImpl.java` — 实现 profile/importUsers 方法

### han-system (mapper XML)
- `SysUserMapper.xml` — PostgreSQL 兼容 ancestors 查询 + postNames 子查询

### han-tenant
- `TenantController.java` — 新增 remove 端点 + @OperLog
- `ITenantService.java` — 新增 deleteTenant 方法
- `TenantServiceImpl.java` — 实现 deleteTenant + initTenantData 调用

### han-ui (前端)
- `api/auth.ts` — 新增 myTenants、switchTenant API
- `api/system/role.ts` — 新增 authUser 4 个 API
- `api/system/user.ts` — 新增 profile、export/import API
- `layout/components/Navbar.vue` — 租户切换入口 + 弹窗
- `router/index.ts` — 添加 authUser 路由
- `views/login/index.vue` — 租户选择下拉
- `views/system/role/index.vue` — 分配用户按钮
- `views/system/tenant/index.vue` — 安全删除确认弹窗
- `views/system/user/index.vue` — 部门树侧栏 + 导入导出按钮

---

## 删除文件 (1 个)

- `ASysLogController.java` — 拆分为 ASysOperLogController + ASysLoginLogController + ASysOnlineController

---

## 功能覆盖

| Phase | 功能 | 状态 |
|-------|------|------|
| **1** | 租户隔离核心 (7项) | ✅ |
| **2** | 用户功能补全 (4项) | ✅ |
| **3** | 权限与安全 (4项) | ✅ |
| **4** | 管理优化 (5项) | ✅ |
| | **合计 20 项** | **✅** |
