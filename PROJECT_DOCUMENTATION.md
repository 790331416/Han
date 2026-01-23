# XuMan Cloud 项目文档

## 项目概述

XuMan Cloud 是一个基于 Spring Boot 4.0 + Spring Cloud 2025 的企业级多租户微服务平台，采用前后端分离架构，提供完整的企业应用开发解决方案。

### 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | LTS版本 |
| Spring Boot | 4.0.2 | 基础框架 |
| Spring Cloud | 2025.0.1 | 微服务框架 |
| Spring Cloud Alibaba | 2025.0.0.0 | 阿里巴巴微服务套件 |
| MyBatis-Plus | 3.5.9 | ORM框架 |
| MySQL | 8.3.0 | 主数据库 |
| Redis | - | 缓存/分布式锁 |
| Flowable | 7.1.0 | 工作流引擎 |
| Quartz | Spring Boot内置 | 分布式任务调度 |
| Spring Authorization Server | 1.5.2 | OAuth2授权服务器 |

---

## 项目结构

```
xuman-cloud
├── xuman-common             # 公共模块
│   ├── xuman-common-core       # 核心工具类
│   ├── xuman-common-redis      # Redis封装
│   ├── xuman-common-security   # 安全模块
│   ├── xuman-common-web        # Web通用配置
│   ├── xuman-common-mybatis    # MyBatis配置
│   ├── xuman-common-datasource # 数据源配置
│   ├── xuman-common-tenant     # 多租户支持
│   ├── xuman-common-log        # 日志模块
│   └── xuman-common-doc        # 接口文档
├── xuman-starter            # 自动装配Starter
│   ├── xuman-starter-cache     # 缓存Starter
│   ├── xuman-starter-mq        # 消息队列Starter
│   ├── xuman-starter-storage   # 对象存储Starter
│   └── xuman-starter-lock      # 分布式锁Starter
├── xuman-api                # API接口定义(Feign)
│   ├── xuman-api-system        # 系统服务API
│   └── xuman-api-tenant        # 租户服务API
├── xuman-gateway            # 网关服务
├── xuman-auth               # 认证服务
├── xuman-modules            # 业务模块
│   ├── xuman-system            # 系统管理模块
│   ├── xuman-tenant            # 租户管理模块
│   ├── xuman-workflow          # 工作流模块
│   ├── xuman-job               # 定时任务模块
│   ├── xuman-open              # 开放平台模块
│   ├── xuman-gen               # 代码生成模块
│   └── xuman-file              # 文件服务模块
├── xuman-visual             # 监控可视化
└── xuman-ui                 # 前端项目(Vue3)
```

---

## 核心模块说明

### 1. 系统管理模块 (xuman-system)

提供基础的系统管理功能：

- **用户管理** - 用户CRUD、角色分配、部门分配
- **角色管理** - 角色CRUD、菜单权限分配、数据权限
- **菜单管理** - 菜单树管理、按钮权限
- **部门管理** - 组织架构树
- **岗位管理** - 岗位信息维护
- **字典管理** - 数据字典维护
- **参数配置** - 系统参数配置
- **操作日志** - 用户操作记录
- **登录日志** - 登录记录审计

### 2. 租户管理模块 (xuman-tenant)

支持SaaS多租户架构：

- **租户管理** - 租户创建、状态管理、有效期控制
- **套餐管理** - 租户功能套餐、菜单权限配置
- **隔离模式** - 支持逻辑隔离、物理隔离、混合隔离

隔离类型说明：
| 类型 | 说明 | 适用场景 |
|------|------|----------|
| LOGIC | 逻辑隔离 | 共享数据库，tenant_id字段区分 |
| PHYSICAL | 物理隔离 | 独立数据库 |
| HYBRID | 混合隔离 | 核心数据物理隔离，其他逻辑隔离 |

### 3. 工作流模块 (xuman-workflow)

基于 Flowable 7.1.0 的工作流引擎：

**核心功能：**
- **流程定义** - 流程部署、激活、挂起、删除
- **流程实例** - 流程启动、终止、挂起、激活
- **任务管理** - 任务查询、完成、转办、委派、撤回
- **历史记录** - 流程历史、任务历史查询

**扩展功能：**
- **流程分类** - 按业务分类管理流程
- **流程表单** - 动态表单设计
- **流程抄送** - 任务抄送通知
- **流程监控** - 运行时监控

**API接口：**
```
GET    /workflow/definition/list      # 流程定义列表
POST   /workflow/definition/deploy    # 部署流程
PUT    /workflow/definition/activate  # 激活流程
PUT    /workflow/definition/suspend   # 挂起流程
DELETE /workflow/definition/{id}      # 删除流程
GET    /workflow/definition/xml       # 获取流程XML
GET    /workflow/definition/diagram   # 获取流程图
```

### 4. 定时任务模块 (xuman-job)

基于 Spring Boot Quartz 的分布式任务调度（兼容 Spring Boot 4.0）：

**核心功能：**
- **任务管理** - 任务创建、修改、删除、暂停、恢复
- **任务执行** - 支持Cron表达式、立即执行、并发控制
- **执行日志** - 任务执行记录、耗时统计、异常追踪
- **集群支持** - 基于数据库的分布式调度

**任务类型：**
- **简单任务** - 无参数定时任务
- **参数任务** - 带参数定时任务
- **数据同步任务** - 定时同步数据
- **清理任务** - 定时清理过期数据

**示例任务Handler：**
```java
@Component("sampleTask")
public class SampleTaskHandler {
    
    // 无参任务 - 调用目标: sampleTask.execute
    public void execute() {
        // 业务逻辑
    }
    
    // 带参任务 - 调用目标: sampleTask.executeWithParam(参数值)
    public void executeWithParam(String param) {
        // 业务逻辑
    }
}
```

**API接口：**
```
GET    /job/list              # 任务列表(分页)
GET    /job/{jobId}           # 任务详情
POST   /job                   # 新增任务
PUT    /job                   # 修改任务
DELETE /job/{jobIds}          # 删除任务
PUT    /job/changeStatus      # 修改状态(暂停/恢复)
PUT    /job/run/{jobId}       # 立即执行一次
GET    /job/checkCron         # 校验Cron表达式
GET    /job/log/list          # 执行日志列表
DELETE /job/log/clean         # 清空日志
```

**配置说明：**
```yaml
spring:
  quartz:
    job-store-type: jdbc          # 数据库存储(集群模式)
    scheduler-name: XumanScheduler
    properties:
      org.quartz.jobStore.isClustered: true
      org.quartz.threadPool.threadCount: 10
```

### 5. 开放平台模块 (xuman-open)

提供第三方应用接入能力：

**OAuth2授权：**
- 授权码模式 (Authorization Code)
- 客户端凭证模式 (Client Credentials)
- PKCE增强 (移动端安全)

**SSO单点登录：**
- 统一登录入口
- Ticket验证机制
- 多应用会话同步

**应用管理：**
- 应用注册审核
- 密钥管理
- 权限范围(Scope)配置
- 回调地址白名单

**API端点：**
```
# OAuth2端点
GET  /oauth2/authorize           # 授权端点
POST /oauth2/token               # Token端点
POST /oauth2/revoke              # 撤销Token
POST /oauth2/introspect          # Token自省
GET  /oauth2/userinfo            # 用户信息(OIDC)

# SSO端点
GET  /sso/login                  # SSO登录入口
GET  /sso/logout                 # SSO登出
POST /sso/validate               # 验证Ticket
GET  /sso/check                  # 检查登录状态
```

---

## 数据库设计

### 数据库脚本

| 脚本文件 | 说明 |
|----------|------|
| xuman_system.sql | 系统管理表(用户、角色、菜单等) |
| xuman_data.sql | 初始化数据 |
| xuman_gen.sql | 代码生成器表 |
| xuman_job.sql | 定时任务表 |
| xuman_workflow.sql | 工作流扩展表 |
| xuman_open.sql | 开放平台表 |

### 核心表结构

**租户相关：**
- `sys_tenant` - 租户信息表
- `sys_tenant_package` - 租户套餐表
- `sys_tenant_package_menu` - 套餐菜单关联

**开放平台：**
- `open_app` - 应用信息表
- `open_authorization_code` - 授权码表
- `open_access_token` - 访问令牌表
- `open_refresh_token` - 刷新令牌表
- `open_user_authorization` - 用户授权记录
- `open_api_scope` - API权限范围

**工作流扩展：**
- `wf_category` - 流程分类表
- `wf_form` - 流程表单表
- `wf_instance_extend` - 实例扩展表
- `wf_copy` - 流程抄送表

---

## 服务端口规划

| 服务 | 端口 | 说明 |
|------|------|------|
| xuman-gateway | 8080 | 网关服务 |
| xuman-auth | 9200 | 认证服务 |
| xuman-system | 9201 | 系统服务 |
| xuman-tenant | 9202 | 租户服务 |
| xuman-workflow | 9203 | 工作流服务 |
| xuman-job | 9204 | 任务调度服务 |
| xuman-open | 9205 | 开放平台服务 |
| xuman-gen | 9206 | 代码生成服务 |
| xuman-file | 9207 | 文件服务 |

---

## 中间件依赖

| 中间件 | 版本要求 | 用途 |
|--------|----------|------|
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存/会话/分布式锁 |
| Nacos | 2.3+ | 服务注册/配置中心 |

---

## 安全机制

### 认证授权

- JWT Token认证
- RBAC权限模型
- 数据权限(本人/本部门/本部门及以下/全部/自定义)
- 按钮级权限控制

### 安全防护

- XSS过滤
- SQL注入防护
- 接口防重复提交
- 敏感数据加密存储
- 操作日志审计

---

## 版本信息

- **当前版本**: 1.0.0
- **最低Java版本**: 21
- **构建工具**: Maven 3.9+
