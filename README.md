# han Cloud - 企业级微服务平台

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.2-brightgreen.svg" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Spring%20Cloud-2025.1.0-blue.svg" alt="Spring Cloud"/>
  <img src="https://img.shields.io/badge/PostgreSQL-18.1-orange.svg" alt="PostgreSQL"/>
  <img src="https://img.shields.io/badge/Java-21-red.svg" alt="Java"/>
  <img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="License"/>
</p>

## 📖 项目简介

han Cloud 是一个基于 **Spring Boot 4.0 + Spring Cloud 2025** 的企业级多租户微服务平台，采用前后端分离架构，融合 **JobFlow** 任务调度架构理念，提供完整的企业应用开发解决方案。

### 🎯 核心特性

- 🚀 **微服务架构** - Spring Cloud 2025 + Nacos 服务治理
- 🏢 **多租户支持** - 逻辑隔离/物理隔离/混合隔离
- 🔄 **JobFlow 任务调度** - 全链路 TraceId + 真分片 + 云原生配置
- 🔐 **OAuth2 认证** - Spring Authorization Server 1.5.2
- 📊 **工作流引擎** - Flowable 7.2.0 集成
- ⚡ **Virtual Threads** - Java 21 虚拟线程全面启用
- 🌐 **声明式 HTTP 客户端** - @HttpExchange + @EnableHttpClients 自动注入
- 📡 **全链路追踪** - Micrometer Tracing + OpenTelemetry 自动集成
- 🛡️ **RFC 7807 错误响应** - ProblemDetail 标准化异常格式
- 📦 **Java Record** - 不可变值对象，替代传统 @Data VO
- 🎨 **现代前端** - Vue 3 + TypeScript + Vite
- 🔌 **可插拔组件** - 根据场景灵活选择中间件组合

---

## 🏗️ 架构设计

### 技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **核心框架** | Spring Boot | 4.0.2 | Virtual Threads + ProblemDetail RFC 7807 |
| | Spring Cloud | 2025.1.0 | 微服务框架 |
| | Spring Cloud Alibaba | 2025.1.0.0 | Nacos 3.x gRPC 长连接 |
| | Micrometer Tracing | BOM 管理 | OpenTelemetry 全链路追踪 |
| **数据存储** | PostgreSQL | 18.1 | 主数据库 |
| | Redis | 7 | 缓存/分布式锁 |
| | RustFS | 1.0.0 | 高性能对象存储 (S3 兼容) |
| | Elasticsearch | 8.x | 日志/搜索（可选） |
| **消息队列** | RabbitMQ | 3.x | 异步消息（可选） |
| | Kafka | 3.x | 大数据流（可选） |
| **服务治理** | Nacos | 3.1 | 注册中心/配置中心 |
| **任务调度** | JobFlow | 自研 | 分布式任务调度（基于Spring Boot集成） |
| **工作流** | Flowable | 7.2.0 | BPM 引擎 |
| **认证授权** | Spring Authorization Server | 1.5.2 | OAuth2/OIDC |
| **工具库** | Hutool | 5.8.36 | Java 工具集 |
| | MapStruct | 1.6.3 | 编译期对象映射 |
| | Lombok | 1.18.38 | 代码简化 |

### 项目结构

```
han-cloud/
├── han-common/              # 公共模块
│   ├── han-common-core          # 核心工具类
│   ├── han-common-redis         # Redis 封装
│   ├── han-common-security      # 安全模块
│   ├── han-common-mybatis       # MyBatis 配置
│   └── han-common-tenant        # 多租户支持
├── han-starter/             # 自动装配 Starter
│   ├── han-starter-cache        # 缓存 Starter
│   ├── han-starter-mq           # 消息队列 Starter
│   ├── han-starter-lock         # 分布式锁 Starter
│   └── han-starter-storage      # 对象存储 Starter
├── han-gateway/             # 网关服务
├── han-auth/                # 认证服务
├── han-api/                 # API接口定义
│   ├── han-api-system           # 系统服务接口
│   ├── han-api-tenant           # 租户服务接口
│   └── han-api-file             # 文件服务接口
├── han-modules/             # 业务模块
│   ├── han-system               # 系统管理
│   ├── han-tenant               # 租户管理
│   ├── han-workflow             # 工作流
│   ├── han-job                  # JobFlow 任务调度
│   ├── han-open                 # 开放平台
│   ├── han-gen                  # 代码生成
│   └── han-file                 # 文件服务
└── han-ui/                  # 前端项目 (Vue3)
```

---

## 🚀 快速开始

### 部署方案选择

根据您的使用场景，选择合适的部署规模：

| 规模 | 适用场景 | 硬件要求 | 默认中间件/服务 | 部署文件 |
|------|----------|----------|-------------------|----------|
| **小型（核心链路）** | 本地开发/功能验证/演示 | 2核4GB | PostgreSQL + Redis + Nacos + gateway/auth/system/job | `docker-compose-small.yml` |
| **中型（标准联调）** | 小团队/测试环境 | 4核8GB | `small` + RustFS + RabbitMQ + tenant/workflow/open/file | `docker-compose.yml` |
| **大型（完整能力）** | 完整业务联调/生产预演 | 8核16GB+ | `medium` + ai；Kafka/Elasticsearch/观测组件建议外挂 | `docker-compose-full.yml` |

### 部署能力矩阵

| Tier | 核心模块 | 扩展模块 | 典型能力 |
|------|----------|----------|----------|
| `small` | gateway、auth、system、job | - | 用户中心、公告通知、参数配置、基础监控 |
| `medium` | `small` 全部能力 | tenant、workflow、open、file、RustFS、RabbitMQ | 多租户、工作流、开放平台、文件服务 |
| `full` | `medium` 全部能力 | ai、MCP、Prompt、Agent、Chat、Token | AI 与增强能力；Kafka/Elasticsearch/观测组件按环境外挂 |

统一通过环境变量 `HAN_DEPLOY_TIER=small|medium|full` 控制后端运行时层级，通过 `HAN_INNER_AUTH_SECRET` 控制服务间内部鉴权密钥。前端会优先读取后端运行时能力接口 `/system/runtime/capabilities`，在登录页和侧边栏按真实部署层级降级展示。

更详细的矩阵说明见 [docs/capability-matrix.md](docs/capability-matrix.md)。

### 小型部署（推荐入门）

**适合**: 个人学习、功能演示、快速验证

```bash
# 1. 克隆项目
git clone <repository-url>
cd han

# 2. 启动小型环境
docker-compose -f docker-compose-small.yml up -d

# 3. 等待服务启动 (约 2-3 分钟)
docker-compose -f docker-compose-small.yml ps

# 4. 访问服务
# Nacos: http://localhost:8848/nacos (han/han@2026)
# 网关:  http://localhost:9090
```

**包含服务**:
- ✅ PostgreSQL (数据库)
- ✅ Redis (缓存 + 分布式锁)
- ✅ Nacos (服务注册 + 配置中心)
- ✅ Gateway (网关)
- ✅ Auth (认证服务)
- ✅ System (系统管理)
- ✅ Job (JobFlow 任务调度)

### 中型部署（推荐团队）

**适合**: 小团队开发、测试环境、完整功能验证

```bash
# 启动中型环境
docker-compose up -d

# 查看服务状态
docker-compose ps
```

**额外包含**:
- ✅ RustFS (对象存储)
- ✅ RabbitMQ (异步消息队列)
- ✅ Tenant (租户管理)
- ✅ Workflow (工作流)
- ✅ Open (开放平台)
- ✅ File (文件服务)
- ⚠️ Gen (代码生成器，当前建议按需单独启用)

### 大型部署（生产环境）

**适合**: 生产环境、高可用、大规模用户

详见 [Kubernetes 部署指南](docs/k8s-deployment.md)

**额外包含**:
- ✅ AI (AI 模块)
- ⚠️ Kafka (大数据流处理，建议外挂)
- ⚠️ Elasticsearch (日志聚合 + 全文搜索，建议外挂)
- ⚠️ Prometheus + Grafana / ELK (建议外挂)

---

## 📚 文档导航

| 文档 | 说明 | 位置 |
|------|------|------|
| **README.md** | 项目介绍与快速开始 | 根目录 |
| [能力矩阵](docs/capability-matrix.md) | 三档部署能力与默认服务矩阵 | docs/ |
| [部署指南](docs/deployment-guide.md) | 三档 compose、环境变量与验证步骤 | docs/ |
| [测试计划](docs/test-plan.md) | 三档部署与核心链路冒烟计划 | docs/ |
| [AIB架构完整实施指南](doc/AIB架构完整实施指南.md) | A/I/B 三层架构生产就绪完整方案 | doc/ |
| [项目开发文档](doc/项目开发文档.md) | 详细的开发文档与快速上手指南 | doc/ |
| [项目对接文档](doc/项目对接文档.md) | 开放平台接入与OAuth2授权指南 | doc/ |
| [项目部署文档](doc/项目部署文档.md) | Docker、K8s部署详解 | doc/ |
| [项目开发规范文档](doc/项目开发规范文档.md) | 强制性开发规范与代码审查清单 | doc/ |

---

## 🎯 JobFlow 核心特性

han Cloud 融合了 **JobFlow** 设计理念，将任务调度能力深度集成到微服务体系：

### ✨ 核心优势

| 特性 | 传统方案 (XXL-Job) | JobFlow 方案 | 优势 |
|------|------------------|-------------|------|
| **注册中心** | 自建注册中心 | ✅ 统一 Nacos | 架构一致 |
| **TraceId 追踪** | ❌ 无 | ✅ 全链路 MDC | 排查效率 ↑10倍 |
| **分片机制** | ⚠️ 建议式 | ✅ 强约束锁 | 数据安全 |
| **配置管理** | 独立后台 | ✅ Nacos Config | 动态调整 |
| **可观测性** | 分散日志 | ✅ 统一上下文 | 监控体验 ↑ |

### 使用示例

```java
@Component("orderSyncTask")
@RequiredArgsConstructor
public class OrderSyncTaskHandler {
    
    private final ShardStrategy shardStrategy;
    private final ShardExecutor shardExecutor;
    
    /**
     * 订单同步任务（支持分片）
     * 调用目标: orderSyncTask.sync(100000,10)
     * 参数: 总数据量,分片数
     */
    public void sync(String params) {
        String[] parts = params.split(",");
        long totalCount = Long.parseLong(parts[0]);
        int shardTotal = Integer.parseInt(parts[1]);
        
        // 计算分片
        List<ShardRange> ranges = shardStrategy.split(1L, totalCount, shardTotal);
        
        // 并行执行（带分布式锁保护）
        ranges.parallelStream().forEach(range -> {
            shardExecutor.executeWithLock(range, this::processOrders);
        });
    }
    
    private void processOrders(ShardRange range) {
        // 处理 [range.startId, range.endId] 范围的订单
        // TraceId 自动传递到所有日志
    }
}
```

**日志输出**:
```log
2026-01-28 10:00:00 [pool-1] [traceId=abc123] [jobId=5] [shardIndex=0] INFO  开始处理分片 0/10
2026-01-28 10:00:01 [pool-2] [traceId=abc123] [jobId=5] [shardIndex=1] INFO  开始处理分片 1/10
```

在 ELK 中搜索 `traceId:abc123` 即可看到完整执行链路！

---

## 🔌 可插拔组件设计

han Cloud 采用可插拔组件架构，根据部署规模灵活选择中间件：

### 组件依赖关系

```
核心组件 (必需)
├── PostgreSQL  - 主数据库
├── Redis       - 缓存 + 分布式锁
├── Nacos       - 服务注册 + 配置中心
└── RustFS      - 高性能对象存储 (S3 兼容)

标准组件 (中型部署)
└── RabbitMQ    - 异步消息队列

企业组件 (大型部署)
├── Kafka       - 大数据流处理
└── Elasticsearch - 日志聚合 + 搜索
```

### 自动适配机制

系统会自动检测可用组件并启用相应功能：

```yaml
# application.yml
spring:
  # RabbitMQ 存在则启用异步消息
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    
  # Elasticsearch 存在则启用日志聚合
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://localhost:9200}
    
  # Kafka 存在则启用流处理
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
```

**组件检测日志**:
```
✅ PostgreSQL detected  - Database ready
✅ Redis detected       - Cache & Lock enabled
✅ Nacos detected       - Service discovery ready
⚠️  RabbitMQ not found  - Async messaging disabled
⚠️  Kafka not found     - Stream processing disabled
⚠️  ES not found        - Log aggregation disabled
```

---

## 🛠️ 开发指南

### 环境要求

- **JDK**: 21+（必须，Virtual Threads 依赖）
- **Maven**: 3.9+
- **Docker**: 20.10+ (推荐)
- **Node.js**: 18+ (前端开发)

### 新版本特性速览

| 特性 | 说明 | 适用范围 |
|------|------|----------|
| **Virtual Threads** | `spring.threads.virtual.enabled=true` | han-auth、han-system 等 Servlet 服务 |
| **Java Record** | 只读 VO 使用 `record` 替代 `@Data` | 全部模块 |
| **Pattern Matching** | `if (x instanceof String s)` | 全部模块 |
| **Switch Expression** | `return switch(e) { case A -> ...; };` | 全部模块 |
| **Duration API** | `Duration.ofMinutes(30)` 替代魔法数字 | Redis 过期时间等 |
| **@HttpExchange** | 声明式 HTTP 客户端，替代 Feign | 跨服务调用 |
| **@EnableHttpClients** | 自动扫描注册 @HttpExchange 接口 | 启动类注解 |
| **ProblemDetail** | RFC 7807 标准化错误响应 | Servlet 服务 |
| **Micrometer Tracing** | OpenTelemetry 全链路追踪 | 通过 han-common-web 自动引入 |
| **@Builder** | Lombok Builder 替代 15+ 行 setter | LoginUser 等复杂对象 |

### 本地开发

```bash
# 1. 启动中间件（仅中间件，不启动业务服务）
docker-compose -f docker-compose-dev.yml up -d

# 2. 导入项目到 IDEA

# 3. 执行数据库脚本
# sql/postgres/init.sql

# 4. 配置 Nacos
# 在 Nacos 控制台创建配置: jobflow-scheduler.yml

# 5. 启动服务
# 右键运行 hanGatewayApplication
# 右键运行 hanAuthApplication
# 右键运行 hanSystemApplication

# 6. 访问
# http://localhost:8080
```

详见 [开发快速上手](DEVELOPER_QUICK_START.md)

---

## 📊 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Nacos | 8848 | 注册/配置中心 |
| RustFS | 9000, 9001 | 对象存储 (API/Console) |
| RabbitMQ | 5672, 15672 | 消息队列 (管理界面) |
| Kafka | 9092 | 流处理 |
| Elasticsearch | 9200 | 搜索引擎 |
| Gateway | 8080 | 网关 |
| Auth | 9200 | 认证 |
| System | 9201 | 系统管理 |
| Tenant | 9202 | 租户管理 |
| Workflow | 9203 | 工作流 |
| Job | 9204 | 任务调度 |
| Open | 9205 | 开放平台 |
| File | 9207 | 文件服务 |
| AI | 9208 | AI 服务 |

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📄 开源协议

本项目采用 [Apache 2.0](LICENSE) 开源协议

---

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Cloud](https://spring.io/projects/spring-cloud)
- [Nacos](https://nacos.io/)
- [Flowable](https://www.flowable.com/)
- [XXL-Job](https://www.xuxueli.com/xxl-job/) - JobFlow 设计灵感来源

---

## 📞 联系方式

- 项目主页: [GitHub](https://github.com/your-org/han-cloud)
- 问题反馈: [Issues](https://github.com/your-org/han-cloud/issues)
- 邮件: support@han.com

---

**开始使用 han Cloud，构建您的企业级微服务应用！** 🚀

## 会话累计摘要

### 2026-03-23 文档对齐与三档部署兼容整改

- 本轮主要目标：把仓库实现继续向文档承诺靠拢，先完成三档部署基线、运行时能力收口、安全边界加固、中文规则配置落地，并为后续系统闭环整改打基础。
- 已完成关键任务：修正 Maven 工作区构建配置，新增运行时能力接口并让前端读取部署层级与模块开关，对齐 `small / medium / full` 三档 compose 与文档能力矩阵，补齐内部调用签名与校验链路，网关剥离伪造内部头，登录失败锁改为 `tenantId + username` 维度，数据权限处理器改为返回真实部门范围，补齐 `job` 控制器权限注解，补齐 `open` 模块 OAuth2 和 SSO 端点的权限豁免与兼容路由，扩展网关开放平台白名单与路由兼容，新增牛马助手中文规则配置文档。
- 关键决策与解决方案：统一使用 `han.deploy.tier` 作为后端能力开关，运行时能力接口作为前端菜单与能力判断的数据源；开放平台同时兼容 `/oauth2`、`/sso` 与 `/open/oauth2`、`/open/sso` 路径，避免破坏现有入口；公开 OAuth2 和 SSO 端点采用 `@PermissionExempt` 配合网关白名单，已登录能力继续由下游 `HeaderAuthenticationFilter` 从 `Authorization` 头恢复登录态；本地编译阶段显式清空系统 `MAVEN_OPTS` 中的错误仓库覆盖项，保证工作区仓库配置生效。
- 使用技术栈/工具：Spring Boot 4、Spring Cloud Gateway、Spring Security、MyBatis-Plus、Vue 3、TypeScript、Maven、PowerShell、`apply_patch`、`rg`。
- 修改文件：`settings.xml`
- 修改文件：`settings.workspace.xml`
- 修改文件：`README.md`
- 修改文件：`docker-compose-small.yml`
- 修改文件：`docker-compose.yml`
- 修改文件：`docker-compose-full.yml`
- 修改文件：`docs/capability-matrix.md`
- 修改文件：`docs/deployment-guide.md`
- 修改文件：`docs/test-plan.md`
- 修改文件：`docs/niuma-assistant-rules.zh-cn.md`
- 修改文件：`doc/optimization-analysis.md`
- 修改文件：`han-common/han-common-core/pom.xml`
- 修改文件：`han-common/han-common-core/src/main/java/com/han/common/core/constant/Constants.java`
- 修改文件：`han-common/han-common-core/src/main/java/com/han/common/core/util/InnerAuthSignUtil.java`
- 修改文件：`han-common/han-common-mybatis/src/main/java/com/han/common/mybatis/handler/HanDataPermissionHandler.java`
- 修改文件：`han-common/han-common-security/src/main/java/com/han/common/security/config/SecurityWebMvcConfigurer.java`
- 修改文件：`han-common/han-common-security/src/main/java/com/han/common/security/interceptor/InnerAuthInterceptor.java`
- 修改文件：`han-gateway/src/main/java/com/han/gateway/filter/AuthFilter.java`
- 修改文件：`han-gateway/src/main/resources/application.yml`
- 修改文件：`han-gateway/src/main/resources/application-docker.yml`
- 修改文件：`han-api/han-api-system/src/main/java/com/han/api/system/SystemServiceClient.java`
- 修改文件：`han-auth/src/main/java/com/han/auth/service/impl/AuthServiceImpl.java`
- 修改文件：`han-modules/han-job/src/main/java/com/han/job/controller/SysJobController.java`
- 修改文件：`han-modules/han-job/src/main/java/com/han/job/controller/SysJobLogController.java`
- 修改文件：`han-modules/han-job/src/main/java/com/han/job/controller/JobFlowMonitorController.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/controller/OAuth2Controller.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/controller/SsoController.java`
- 修改文件：`han-modules/han-open/src/main/resources/application-docker.yml`
- 修改文件：`han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java`
- 修改文件：`han-ui/src/api/system/runtime.ts`
- 修改文件：`han-ui/src/layout/components/Sidebar.vue`
- 修改文件：`han-ui/src/router/index.ts`
- 修改文件：`han-ui/src/stores/app.ts`
- 修改文件：`han-ui/src/stores/user.ts`

### 2026-03-23 95 服务器部署验证

- 本轮主要目标：接入 `10.18.35.95` 远程测试环境，按既定 Docker 部署流程验证现有 `xzy0112` 镜像是否能跑通 Han Cloud 的中型部署链路，并记录镜像与文档实现差异。
- 已完成关键任务：在本机 Codex 配置中新增 `local95` MCP 服务器并确认会话可连接；远程确认 `95` 服务器存在 `/root/han-cloud` 与 `/opt/han/docker` 两套部署目录；识别服务器已缓存 `registry.cn-hangzhou.aliyuncs.com/xzy0112` 系列镜像；使用 `/opt/han/docker/docker-compose.yml` 成功拉起 `postgres`、`redis`、`nacos`、`rustfs`、`gateway`、`auth`、`system`、`tenant`、`job`、`open` 容器；定位并确认 `han-ui` 之前重启的直接原因是上游 `gateway` 未启动；验证 `auth/captcha` 可用并通过 Redis 读取验证码完成 `admin/admin123` 登录；验证 `/system/user/current`、`/system/menu/routers` 正常返回；验证 `/system/runtime/capabilities` 与 `/open/app/list` 在当前远程镜像中仍为 404。
- 关键决策与解决方案：远程验证阶段优先复用服务器现有 `xzy0112` 镜像，不额外拉取其他镜像；由于服务器未安装 `git`，本轮采用现有部署目录直接启动进行镜像级验证；登录闭环采用“网关取验证码 + Redis 取验证码值 + 登录接口”方式验证，避免前端视觉识别误差；将当前环境判定为“基础链路可启动，但镜像版本落后于本地整改代码”的状态，后续若要验证最新整改内容，需要把最新镜像重新推送或把最新代码同步到 `95` 服务器后重新构建部署。
- 使用技术栈/工具：MCP SSH、Docker Compose、Nginx、Spring Boot、Nacos、Redis、PostgreSQL、PowerShell、`apply_patch`、`curl`。
- 修改文件：`README.md`
- 修改文件：`C:\Users\79033\.codex\config.toml`

### 2026-03-23 95 服务器 Git 工作流接入

- 本轮主要目标：在 `10.18.35.95` 服务器补齐 Git 能力，建立“本地 push、服务器 pull、再做 Docker 部署验证”的固定流程，并把该流程写入仓库文档。
- 已完成关键任务：确认 `95` 服务器为 `CentOS 7`，使用 `yum` 安装 `git` 并验证版本；确认本地仓库 `origin` 为 `https://gitee.com/xzy0112/Han.git`，当前分支为 `master`；验证 `95` 服务器可执行 `git ls-remote` 读取 Gitee 仓库；在 `95` 服务器创建代码目录 `/opt/han/source/Han` 并完成仓库克隆；验证服务器侧 `git pull --ff-only origin master` 可正常执行；新增 95 服务器部署流程文档，固化“先装 git，再 push/pull，再 Docker 验证”的步骤。
- 关键决策与解决方案：后续固定使用 `95` 服务器代码目录 `/opt/han/source/Han`，部署目录 `/opt/han/docker`；镜像优先复用 `xzy0112` 仓库和服务器本地缓存镜像，不额外搜索其他镜像源；对于当前本地大量未提交混合改动，不直接盲目推送，后续按明确发布范围提交再推送，避免把未确认改动一起发到远端。
- 使用技术栈/工具：MCP SSH、Git、Yum、Docker Compose、PowerShell、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/server-95-deploy-flow.md`

### 2026-03-23 Han UI Playwright 自动化脚手架接入

- 本轮主要目标：为 `han-ui` 接入可复用的 Playwright 自动化测试脚手架，满足前端提测规则中的 `lint/build/E2E` 要求，并打通本地前端联调 `95` 后端的执行链路。
- 已完成关键任务：新增 ESLint 9 flat config；补齐 `Playwright` 依赖、运行脚本、配置文件、环境变量模板和产物目录约定；新增登录、退出登录、通知中心三类基础 E2E 用例；补齐登录页、首页、顶部导航、通知铃铛等稳定 `data-testid` 钩子；修复 API 登录态注入失败问题，将注入逻辑改为 `browser context init script + localStorage 自检`；将默认 E2E 执行策略调整为串行优先，降低共享测试账号的并发踩踏风险；新增自动化执行说明文档，并在项目开发规范中补齐引用入口。
- 关键决策与解决方案：不简化任何现有业务能力，只补测试基础设施和页面测试钩子；Playwright 默认采用“本地前端 + 95 网关”模式，未设置 `PW_BASE_URL` 时自动拉起本地 Vite；默认 `PW_WORKERS=1`，需要并发时由环境变量显式开启；通知中心测试工具对旧环境的 `markAllRead` 请求方法差异做了兼容探测，用于区分“测试脚手架问题”和“95 旧镜像契约落后”；当前验证确认登录与退出登录链路已通过，通知中心用例在 `95` 旧后端镜像上仍暴露真实契约差异，需要更新远端 `gateway/auth/system/ui` 镜像后再做完整回归。
- 使用技术栈/工具：Vue 3、TypeScript、Pinia、Vite、ESLint 9、Playwright、PowerShell、`apply_patch`、`view_image`。
- 修改文件：`.gitignore`
- 修改文件：`README.md`
- 修改文件：`doc/项目开发规范文档.md`
- 修改文件：`docs/playwright-e2e.md`
- 修改文件：`han-ui/.env.e2e.example`
- 修改文件：`han-ui/package.json`
- 修改文件：`han-ui/eslint.config.js`
- 修改文件：`han-ui/playwright.config.ts`
- 修改文件：`han-ui/vite.build.config.ts`
- 修改文件：`han-ui/src/views/login/index.vue`
- 修改文件：`han-ui/src/layout/components/Navbar.vue`
- 修改文件：`han-ui/src/layout/components/NotifyBell.vue`
- 修改文件：`han-ui/src/views/dashboard/index.vue`
- 修改文件：`han-ui/tests/e2e/fixtures/test.ts`
- 修改文件：`han-ui/tests/e2e/specs/auth-login.spec.ts`
- 修改文件：`han-ui/tests/e2e/specs/auth-logout.spec.ts`
- 修改文件：`han-ui/tests/e2e/specs/notice-center.spec.ts`
- 修改文件：`han-ui/tests/e2e/utils/auth.ts`
- 修改文件：`han-ui/tests/e2e/utils/notice.ts`

### 2026-03-23 通知中心联调闭环与 XSS JSON 清洗修复

- 本轮主要目标：继续推进通知中心闭环，打通 `95` 服务器上的 `auth/system` 新镜像联调，并让 `han-ui` 的 Playwright 通知中心用例在真实 Docker 环境上回归通过。
- 已完成关键任务：定位 `95` 环境中 `han-system` 已升级但 `han-auth` 仍是旧镜像，补齐 `han-auth` 远端构建与容器重建后恢复登录链路；确认 `/system/notice/markAllRead` 在 `2026-03-23` 的 `95` 环境上返回 200；定位 `/system/notice/add` 的 500 根因是 `XssHttpServletRequestWrapper` 直接清洗整段 JSON 字符串，破坏了请求体结构；将 XSS 处理改为递归清洗 JSON 字符串字段并缓存请求体，保持防护能力不丢失；重新构建并部署 `han-system` 到 `95`；补强 `han-ui` 通知测试辅助，显式 JSON 序列化通知创建请求，并在触发防重复提交时等待一个窗口后重试一次；最终跑通完整 Playwright 回归，登录、退出、通知中心 4 条用例全部通过。
- 关键决策与解决方案：不删除 XSS 防护，只修正实现方式，确保富文本和 JSON 请求体同时兼容；远端部署阶段采取最小影响面策略，仅重建 `auth` 和 `system`，避免触碰其它业务服务；前端 E2E 不绕过真实后端规则，对“请勿重复提交”采用测试节奏适配而不是关闭服务端校验；联调模式固定为“本地最新 UI + `95` 后端网关”，这样既能验证当前代码，又能避开 `95` 旧前端镜像缺少测试钩子的干扰。
- 使用技术栈/工具：Spring Boot 4、Spring Security、Jackson 3、Jsoup、Redis、Docker Compose、Playwright、PowerShell、MCP SSH、Maven、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-common/han-common-security/src/main/java/com/han/common/security/filter/XssHttpServletRequestWrapper.java`
- 修改文件：`han-ui/tests/e2e/utils/notice.ts`

### 2026-03-24 OSS 配置闭环验收与中配前端入口对齐

- 本轮主要目标：继续推进 `OSS` 配置闭环，在 `95` 服务器上验证 `system/file` 新镜像、数据库活动配置和真实上传链路，同时把前端静态路由层级对齐到三档部署能力矩阵，并补一条 `OSS` 页面 Playwright 验证。
- 已完成关键任务：在 `95` 服务器完成 `han-system`、`han-file` 健康检查并确认 `/system/runtime/capabilities`、`/system/oss/config/list`、`/system/oss/config/active` 全部恢复为 `200`；确认运行时能力返回 `medium` 档模块集合与可选服务状态；通过执行仓库内 `sql/sys_oss_config.sql` 补齐远端现存数据库的 `sys_oss_config` 表；通过真实网关登录创建并启用一条 `RustFS` 数据库活动配置，完成 `/file/upload` 上传验收并拿到返回 URL；发现匿名访问返回的 `RustFS` 外链当前为 `403 AccessDenied`，说明“上传成功，但公共外链访问策略”仍需后续收口；修正前端静态路由层级，将 `job` 对齐到 `small`、`workflow` 对齐到 `medium`、`OSS 配置` 对齐到 `medium`；为侧边栏菜单和 `OSS` 页面补充稳定 `data-testid`；新增 `han-ui` 的 `OSS` 页面 Playwright 用例，并完成 `lint`、`build`、`Playwright` 实测通过。
- 关键决策与解决方案：不简化任何现有功能，优先把文档承诺和真实部署行为对齐；远端 `OSS` 烟测使用数据库活动配置优先链路，验证 `system -> file -> rustfs` 的真实闭环，而不是只看静态兜底配置；前端路由层级以运行时能力矩阵为准，避免“后端已支持但中配环境入口被静态路由误隐藏”；对 `RustFS` 外链 `403` 暂不做拍脑袋修复，先如实记录为剩余风险，避免在未确认权限策略前引入新的行为回退。
- 使用技术栈/工具：Docker Compose、PostgreSQL、Redis、RustFS、Spring Boot、Vue 3、Vite、Playwright、MCP SSH、PowerShell、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/router/index.ts`
- 修改文件：`han-ui/src/layout/components/Sidebar.vue`
- 修改文件：`han-ui/src/views/system/oss-config/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/oss-config.spec.ts`
