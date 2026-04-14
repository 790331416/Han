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
| **大型（完整能力）** | 完整业务联调/生产预演 | 8核16GB+ | `medium` + ai + gen；Kafka/Elasticsearch/观测组件建议外挂 | `docker-compose-full.yml` |

### 部署能力矩阵

| Tier | 核心模块 | 扩展模块 | 典型能力 |
|------|----------|----------|----------|
| `small` | gateway、auth、system、job | - | 用户中心、公告通知、参数配置、基础监控 |
| `medium` | `small` 全部能力 | tenant、workflow、open、file、RustFS、RabbitMQ | 多租户、工作流、开放平台、文件服务 |
| `full` | `medium` 全部能力 | ai、gen、MCP、Prompt、Agent、Chat、Token | AI 与增强能力，含代码生成；Kafka/Elasticsearch/观测组件按环境外挂 |

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
- ⚠️ Gen (代码生成器，`full` compose 已预留服务位，默认使用 `HAN_GEN_IMAGE`，仍建议在已完成镜像构建后再启用)

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

### 2026-03-24 文件公开代理闭环与 95 远端重建验证

- 本轮主要目标：继续收口 `RustFS` 外链匿名访问 `403` 的问题，在不简化现有上传能力的前提下补齐文件公开访问代理，并在 `95` 服务器上完成真实重建与回归验证。
- 已完成关键任务：在 `han-starter-storage` 中补齐可定位活动配置和指定配置的运行时记录能力，为存储访问代理提供稳定配置来源；在 `han-file` 中新增 `FileStorageAccessService`，将上传与公开访问统一收敛到同一条服务链路；在 [FileController.java](D:/code/Han/han-modules/han-file/src/main/java/com/han/file/controller/FileController.java) 中新增 `GET /file/public/{locator}/{fileName}` 公开代理下载接口，并保持 `/file/upload` 返回结构不变，仅把 `url` 收口为系统可控的公开代理地址；在 [AuthFilter.java](D:/code/Han/han-gateway/src/main/java/com/han/gateway/filter/AuthFilter.java) 中将 `/file/public/` 加入白名单；补齐 [Constants.java](D:/code/Han/han-common/han-common-core/src/main/java/com/han/common/core/constant/Constants.java) 中网关透传所需的请求头常量；将改动提交并推送到 `codex/han-ui-remote-validate`，在 `95` 服务器使用本地 Maven Docker 镜像重建 `han-gateway`、`han-file`，并强制重建两个容器；最终验证 `9200` 登录、`9207/file/upload` 上传、`9207/file/public/...` 匿名访问全部返回 `200`，下载内容与上传内容一致。
- 关键决策与解决方案：不删除也不弱化现有上传链路，只是在上传返回的公开地址上增加平台内代理，避免直接暴露第三方对象存储匿名策略；代理实现按 `locator -> 配置记录 -> Provider` 的方式选择存储提供者，兼容数据库活动配置与兜底静态配置；远端构建继续复用 `xzy0112` 的 Maven 基础镜像和 `95` 本机 Docker 环境，不引入额外依赖源；同时确认 `95` 上当前重建后的 `gateway:9090` 对 `/auth/**`、`/system/**`、`/file/**` 仍返回网关层 `404`，这已记录为独立残留风险，不把它误记成文件公开代理失败。
- 使用技术栈/工具：Spring Boot 4、Spring Cloud Gateway、Starter Storage、RustFS、Docker Compose、Maven、MCP SSH、PowerShell、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-common/han-common-core/src/main/java/com/han/common/core/constant/Constants.java`
- 修改文件：`han-gateway/src/main/java/com/han/gateway/filter/AuthFilter.java`
- 修改文件：`han-modules/han-file/src/main/java/com/han/file/controller/FileController.java`
- 修改文件：`han-modules/han-file/src/main/java/com/han/file/service/FileStorageAccessService.java`
- 修改文件：`han-starter/han-starter-storage/src/main/java/com/han/starter/storage/config/StorageAutoConfiguration.java`
- 修改文件：`han-starter/han-starter-storage/src/main/java/com/han/starter/storage/config/StorageConfigRecord.java`
- 修改文件：`han-starter/han-starter-storage/src/main/java/com/han/starter/storage/config/StorageConfigRepository.java`
- 修改文件：`han-starter/han-starter-storage/src/main/java/com/han/starter/storage/config/StorageRuntimeConfig.java`
- 修改文件：`han-starter/han-starter-storage/src/main/java/com/han/starter/storage/config/JdbcStorageConfigRepository.java`

### 2026-03-24 开放平台闭环与网关统一入口恢复

- 本轮主要目标：继续推进 `open` 模块闭环，完成 `open app` 和 `OAuth2` 的真实验收，并修复 `95` 环境 `gateway:9090` 对 `auth/system/open/file` 路由失效的问题，让中配部署重新回到统一网关入口。
- 已完成关键任务：补齐 `open app` 分页、详情、创建、编辑、删除、重置密钥、状态切换接口，补齐前端所需查询字段与查询条件绑定；将 `OAuth2` 最小可用实现切换为“数据库中的应用配置 + 内存授权码/访问令牌/刷新令牌状态”的可运行方案；修复 `OAuth2Controller` 对 `response_type`、`client_id`、`redirect_uri`、`grant_type` 等下划线参数的显式绑定；修复 `han-open` Docker 配置中 Redis/Nacos 配置失效问题并完成远端重建；在 `95` 环境真实跑通 `open app` CRUD、`authorize -> token -> userinfo -> introspect -> refresh -> revoke` 全链路；定位 `gateway:9090` 统一返回 `404` 的根因为 `Spring Cloud Gateway 5.0.0` 配置前缀升级且旧镜像仍携带错误的通用 `application-docker.yml`；恢复 `han-gateway` 的源码内静态路由配置并在 Docker 配置中补齐 `workflow` 路由；在 `95` 环境重建 `han-gateway` 后，真实验证 `9090/auth/captcha`、`9090/system/runtime/capabilities`、登录后 `9090/open/app/list`、`9090/file/upload`、匿名 `9090/file/public/...` 全部成功。
- 关键决策与解决方案：开放平台先按“最小可用生产版”收口，不删除任何现有能力，仅把原本空心或占位实现补成真实可运行链路；`OAuth2` 应用合法性、状态、回调地址全部收敛到 `open_app` 表校验，令牌状态先以内存方式托管，保证当前部署能工作；网关路由不再依赖远端遗留的 `Nacos han-gateway.yml` 旧前缀配置，统一以源码内 `application.yml` 和 `application-docker.yml` 为准，并使用 `spring.cloud.gateway.server.webflux.routes` 作为唯一合法前缀；`95` 环境后续部署验收以 `9090` 为唯一入口优先，避免再次出现“服务能直连但网关失效”的假阳性结果。
- 使用技术栈/工具：Spring Boot 4、Spring Cloud Gateway 5、Spring Security、MyBatis-Plus、Docker Compose、Nacos、Redis、PostgreSQL、MCP SSH、PowerShell、Maven、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/gateway-route-alignment.md`
- 修改文件：`han-gateway/src/main/resources/application.yml`
- 修改文件：`han-gateway/src/main/resources/application-docker.yml`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/controller/OpenAppController.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/controller/OAuth2Controller.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/domain/dto/OpenAppStatusUpdateRequest.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/domain/query/OpenAppQuery.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/domain/vo/OpenAppVO.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/mapper/OpenAppMapper.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/service/IOpenAppService.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/service/impl/OpenAppServiceImpl.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/service/impl/OAuth2ServiceImpl.java`
- 修改文件：`han-modules/han-open/src/main/resources/application-docker.yml`

### 2026-03-24 租户模块契约收口与 95 中配回归

- 本轮主要目标：继续推进 `tenant` 模块闭环，把租户列表、租户套餐、全部有效租户和管理员查询等接口收口到文档与前端页面期望的结构，并在 `95` 服务器中配环境完成真实回归。
- 已完成关键任务：将 `/tenant/list` 从普通数组返回改为标准分页结构，补齐 `pageNum/pageSize/pages/total/rows`；为租户列表、租户详情、全部有效租户补齐 `packageName`、`userCount` 字段，并用 `@JsonIgnoreProperties` 去掉租户实体中无意义的继承 `tenantId` 序列化噪音；修复 `TenantServiceImpl`，补齐套餐名映射、用户数统计、套餐校验、角色菜单同步和安全删除异常边界；补齐租户套餐列表、详情、全部有效套餐中的 `tenantCount` 统计，修复分页元数据缺失问题；为租户套餐删除增加后端保护，拦截“默认套餐删除”和“仍有关联租户的套餐删除”；新增租户专用内部客户端 `SystemClient` 与 `ITenantQueryController`，独立提供 `/inner/system/tenant/adminUser` 能力，避免把其他混合中的 system 改动带入本轮提交；本地完成 `han-system + han-tenant` 联合编译通过；将代码推送到 `codex/han-ui-remote-validate` 后，在 `95` 服务器完成源码拉取、Maven 打包、`han-system` 与 `han-tenant` 镜像重建、Docker Compose 强制重建；最终通过 `9090` 网关真实验证 `/tenant/list`、`/tenant/package/list`、`/tenant/all`、`/tenant/listAllValid`、`/tenant/adminUser`、`/tenant/package/remove/{id}`。
- 关键决策与解决方案：不删除任何现有功能，只补齐文档承诺的字段、分页结构和保护逻辑；针对 `adminUser` 能力，采用“新增独立 inner controller + 新增独立 HttpExchange 客户端”的方式收口，避免误带 `TenantInitController` 和 `SystemServiceClient` 中尚未完成的混合改动；真实回归全部通过网关和登录态执行，不用假数据绕过鉴权；删除保护同时覆盖“平台默认套餐”与“已被租户占用的套餐”，确保前端拦截之外后端也有兜底。
- 使用技术栈/工具：Spring Boot 4、MyBatis-Plus、Spring HttpExchange、Docker Compose、Nacos、Redis、PostgreSQL、MCP SSH、PowerShell、Maven、`curl`、Python、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-api/han-api-system/src/main/java/com/han/api/system/SystemClient.java`
- 修改文件：`han-modules/han-system/src/main/java/com/han/system/controller/inner/ITenantQueryController.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/controller/TenantController.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/domain/dto/TenantDTO.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/domain/po/TenantPo.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/service/ITenantService.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/service/impl/TenantPackageServiceImpl.java`
- 修改文件：`han-modules/han-tenant/src/main/java/com/han/tenant/service/impl/TenantServiceImpl.java`

### 2026-03-24 租户前端 Playwright 回归补齐

- 本轮主要目标：继续推进 `tenant` 模块闭环，把租户管理、租户套餐、租户配额三页接入稳定的 Playwright 自动化验证，并确保前端源码中的租户页具备可观测测试钩子。
- 已完成关键任务：为租户管理页补齐 `data-testid`，覆盖页面容器、表格、新增按钮和行级编辑/重置密码/删除按钮，同时修正搜索框占位文案；为租户套餐页补齐页面容器、表格、新增按钮、行级编辑/菜单/删除按钮以及菜单对话框测试钩子；为租户配额页补齐页面容器、租户选择器、三张配额卡片、保存按钮测试钩子，并将配额卡片中的图标文本改成普通 ASCII 缩写以符合仓库输出规范；新增 `han-ui/tests/e2e/utils/tenant.ts`，通过真实后端接口读取租户、套餐、有效租户和配额数据，避免测试写死；新增 `han-ui/tests/e2e/specs/tenant-pages.spec.ts`，覆盖租户列表、租户套餐、租户配额三条真实页面回归；修正 `.gitignore` 中对 `*.spec.ts` 的全局忽略，为 `han-ui/tests/e2e/specs/*.spec.ts` 增加白名单，确保新用例可以被版本管理。
- 关键决策与解决方案：不改动租户业务逻辑，只补最小测试可观测性和前端文案修正；E2E 全程使用“本地最新 `han-ui` + `95` 真实后端网关”的联调模式，通过 API 先读取真实数据，再到页面做断言，避免因测试数据硬编码导致脆弱回归；租户配额页不再使用装饰性 emoji 图标，改成 `USR/STO/API` 这类标准文本，兼顾规范约束与页面可读性；针对 `.gitignore` 误伤 Playwright 新用例的问题，只对白名单目录解禁，不影响仓库其他 `*.spec.ts` 忽略策略。
- 使用技术栈/工具：Vue 3、Element Plus、Vite、Playwright、PowerShell、ESLint、`vue-tsc`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`.gitignore`
- 修改文件：`han-ui/src/views/system/tenant/index.vue`
- 修改文件：`han-ui/src/views/system/tenant/package.vue`
- 修改文件：`han-ui/src/views/system/tenant/quota.vue`
- 修改文件：`han-ui/tests/e2e/utils/tenant.ts`
- 修改文件：`han-ui/tests/e2e/specs/tenant-pages.spec.ts`

### 2026-03-24 95 服务器 han-ui 远端部署与 SPA 路由回退修复

- 本轮主要目标：将租户前端回归的最新提交部署到 `95` 服务器的真实 `han-ui` 容器，并完成“远端前端 + 远端后端”的整体验证。
- 已完成关键任务：将租户前端回归提交 `caf2d4d` 推送后，拉取到 `95` 的 `/opt/han/source/Han-ui-validate-20260323`；发现 `95` 没有宿主机 `node/npm` 且无法直接拉官方 `node` 镜像，因此改用现成的 `registry.cn-hangzhou.aliyuncs.com/xzy0112/nginx:1.29-alpine-slim` 作为临时构建容器，在容器内 `apk add nodejs npm` 后完成 `npm install --legacy-peer-deps` 和 `npm run build`；在 `95` 上重建 `han-ui` 镜像并保留旧镜像与旧容器备份标签；首次远端 Playwright 失败后，定位到问题不是租户页面代码，而是 `han-ui/nginx.conf` 将 `/system/*`、`/tenant/*` 等前端直达路由误代理到网关，导致浏览器刷新时直接拿到后端 `401`；补齐 `nginx.conf` 中基于 `Accept: text/html` 的 SPA 回退逻辑，确保浏览器导航请求返回 `index.html`，接口请求仍按原路代理；继续修正 `han-ui/Dockerfile` 的 `HEALTHCHECK`，将探针目标从 `localhost` 改为 `127.0.0.1`，消除容器实际可用但健康状态持续 `unhealthy` 的假阴性；将修复提交推送后再次在 `95` 重建 `han-ui` 容器；最终通过 `curl -H 'Accept: text/html' http://127.0.0.1:3000/system/tenant` 验证直达租户路由已返回前端页面，并通过本地 Playwright 直连 `95:3000` 与 `95:9090` 跑通租户三页回归，结果 `3 passed`。
- 关键决策与解决方案：不改前端 API 契约、不引入新的接口前缀，只在 Nginx 层补“导航请求回前端、接口请求走网关”的最小闭环，避免影响现有请求路径；远端部署阶段保留多个 `han-ui-bak-*` 备份容器，同时为旧镜像打 `backup-*` 标签，保证随时可回滚；依赖安装冲突仅在远端构建时使用 `npm install --legacy-peer-deps`，不修改仓库依赖版本；将健康检查目标固定到 `127.0.0.1`，让容器状态与真实服务可用性保持一致；整体验证从“本地最新前端 + 95 后端”升级为“95 前端 + 95 后端”，把真实部署链路补成闭环。
- 使用技术栈/工具：Nginx、Docker、Alpine、Node.js、npm、Playwright、MCP SSH、PowerShell、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/Dockerfile`
- 修改文件：`han-ui/nginx.conf`

### 2026-03-24 开放平台编辑契约兼容与 95 远端闭环

- 本轮主要目标：继续推进 `open app` 闭环，修复开放平台编辑接口对 `appId` 契约不兼容的问题，并完成“本地最新代码 + 95 远端前后端”的最终真实回归。
- 已完成关键任务：定位 `95` 环境 `/open/app/edit` 失败的根因为后端当前仅接受扁平化后的 `id`，不接受前端与文档使用的 `appId`；通过远端直接接口探针确认“仅传 `appId` 失败、仅传 `id` 成功”；在 [OpenAppDTO.java](D:/code/Han/han-modules/han-open/src/main/java/com/han/open/domain/dto/OpenAppDTO.java) 中补齐 `appId` 与 `base.id` 的同步逻辑；在 [app.ts](D:/code/Han/han-ui/src/api/open/app.ts) 中让编辑请求同时携带 `appId` 与 `id`，兼容旧镜像与新镜像；在 [OpenAppController.java](D:/code/Han/han-modules/han-open/src/main/java/com/han/open/controller/OpenAppController.java) 中为编辑接口增加请求体兼容归一化逻辑，显式从 `requestBody` 中回填 `appId/id`，确保服务端原生支持文档契约；将修复先后提交为 `48e75f9`、`fedc0d9`、`4f14463` 并推送到 `codex/han-ui-remote-validate`；在 `95` 的 `/opt/han/source/Han-ui-validate-20260323` 完成源码拉取、`han-open` 容器化 Maven 打包、`han-open` 镜像重建、`han-open` 服务重建，以及 `han-ui` 远端镜像和容器切换；最终验证“仅传 `appId` 的 `/open/app/edit` 返回 `200`”，并通过 Playwright 直连 `95:3000` 与 `95:9090` 跑通 [open-app.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/open-app.spec.ts)，结果 `1 passed`。
- 关键决策与解决方案：不删除也不弱化任何既有开放平台能力，而是同时做“前端向后兼容 + 后端原生收口”双保险；前端保留现有 `appId` 契约不变，仅额外补发 `id` 兼容老镜像；后端不依赖全局 `ObjectMapper` Bean，改为控制器内使用轻量 `ObjectMapper` 做请求体兼容转换，避免引入新的 Spring Bean 依赖导致服务启动失败；`95` 上 `han-ui` 不是 compose 管理容器，本轮继续采用“旧容器改名保备份，再起新容器”的方式切换，保留回滚能力。
- 使用技术栈/工具：Spring Boot 4、Spring MVC、Jackson、Vue 3、Playwright、Docker、Maven、MCP SSH、PowerShell、Python、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/controller/OpenAppController.java`
- 修改文件：`han-modules/han-open/src/main/java/com/han/open/domain/dto/OpenAppDTO.java`
- 修改文件：`han-ui/src/api/open/app.ts`

### 2026-03-24 三档部署系统化验收

- 本轮主要目标：按文档口径对 `small / medium / full` 三档部署做一次系统化验收，确认 `95` 服务器上的真实兼容情况，并把通过项、失败项、阻塞项沉淀下来。
- 已完成关键任务：先检查 [capability-matrix.md](D:/code/Han/docs/capability-matrix.md)、[deployment-guide.md](D:/code/Han/docs/deployment-guide.md)、[server-95-deploy-flow.md](D:/code/Han/docs/server-95-deploy-flow.md) 与三份 Compose 文件；在 `95` 上为 `small` 生成隔离版 Compose，使用独立端口和容器名完成最小链路拉起；定位 `95` 部署目录 `sql/sys_oss_config.sql` 被放成目录，导致 PostgreSQL fresh deploy 初始化失败，并在隔离副本中临时改挂源码文件继续验收；完成 `small` 的能力接口、登录、当前用户、任务列表、任务日志接口回归；确认 [init.sql](D:/code/Han/sql/postgres/init.sql) 本身只建表不灌菜单，导致 `small` fresh deploy 后 `/system/menu/routers` 为空；完成 `medium` 现网环境 API 验收，验证 `/system/runtime/capabilities`、`/auth/login`、`/system/user/current`、`/tenant/list`、`/open/app/list`、`/system/oss/config/list`、`/system/notice/list`；确认 `workflow` 服务未运行且 `/workflow/definition/list` 返回 `503`，但运行时能力仍错误宣称 `workflow` 已启用；确认 `/system/notice/unreadCount` 返回业务码 `500`，错误为 `参数类型错误: noticeId`；本地提权执行 Playwright 直连 `95` 前后端，跑通 `auth-login`、`tenant-pages`、`oss-config`、`open-app`，并复现 `notice-center` 因 `markAllRead/unreadCount` 失败而挂掉，结果为 `7 passed, 1 failed`；确认 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-workflow:latest` 与 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest` 当前都返回 `manifest unknown`，因此 `full` 在 `2026-03-24` 无法真实拉起；新增三档验收报告 [tier-validation-report-20260324.md](D:/code/Han/docs/tier-validation-report-20260324.md)；最后清理了临时 `small` 隔离环境，不影响 `95` 现网 `medium`。
- 关键决策与解决方案：不改动任何业务能力，不删功能，只做真实验收和问题收口；`small` 采用隔离 Compose 而不是直接复用现网端口，避免冲掉 `95` 上正在使用的 `medium`；`full` 在镜像缺失前提下不做伪验收，而是明确记为发布物阻塞；前端验收继续坚持“远端真实页面 + 远端真实网关”口径，不用 mock；对 `95` 上的 `sys_oss_config.sql` 问题仅做隔离副本修正，不直接动在线部署目录，避免误伤主环境。
- 使用技术栈/工具：Docker Compose、MCP SSH、PowerShell、Playwright、Python、`curl`、PostgreSQL、Redis、Nacos、Spring Boot 4、Spring Cloud Gateway 5、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/tier-validation-report-20260324.md`

### 2026-03-24 通知中心路由冲突修复与 95 真实回归

- 本轮主要目标：修复 `medium` 验收中通知中心 `/system/notice/unreadCount`、`/system/notice/sse` 被 `/{noticeId}` 动态路由误匹配的问题，并在 `95` 服务器完成真实构建、部署和前端回归。
- 已完成关键任务：定位 [ASysNoticeController.java](D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/admin/ASysNoticeController.java) 中 `@GetMapping("/{noticeId}")` 会吞掉 `unreadCount`、`latest`、`sse` 等固定路径；将详情接口改为仅匹配数字 ID 的 `@GetMapping("/{noticeId:\\d+}")`，保证固定路径优先命中；由于 `95` 上远端源码仓库滞后，额外同步了通知中心依赖的 `SysNoticeReadPo`、`NoticeLatestVo`、`SysNoticeReadMapper`、`SseEmitterService` 以及新版 `SysNoticeMapper` 到 `/opt/han/source/Han-ui-validate-20260323`；在 `95` 上使用容器化 Maven 成功重新打包 `han-system`，并重建 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-system:latest` 与 `han-system` 容器；通过网关真实登录拿到 token 后，验证 `/system/notice/unreadCount` 返回 `200` 且数据为数字、`/system/notice/latest?limit=5` 返回 `200` 且数据结构正常、`/system/notice/sse?token=...` 返回 `event:connected` 事件流，不再出现 `noticeId` 参数类型错误；最后提权执行 Playwright，直连 `95:3000` 与 `95:9090` 重新跑通 [notice-center.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts)，结果 `1 passed`。
- 关键决策与解决方案：不简化通知能力，不移除任何现有端点，仅通过“数字路由约束”收口控制器匹配范围；鉴于 `95` 源码仓库落后于本地代码，本轮采用“最小必要文件同步 + 远端原地重建”的方式完成验证，避免把工作区中其他未确认混合改动一起带上；SSE 验证采用短时长连接只确认握手与首条事件流，既覆盖真实链路，又避免长连接阻塞验收脚本。
- 使用技术栈/工具：Spring Boot 4、Spring MVC、MyBatis、Docker、Docker Compose、MCP SSH、PowerShell、Maven、Playwright、Redis、`curl`、Python、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-modules/han-system/src/main/java/com/han/system/controller/admin/ASysNoticeController.java`

### 2026-03-24 三档验收收口与运行时能力回归

- 本轮主要目标：继续收口三档系统化验收中的剩余问题，补齐 `small` fresh deploy 的基线菜单初始化验证，修复 `medium` 运行时能力误报，并把前端侧边栏的运行时过滤固化成 Playwright 回归。
- 已完成关键任务：新增 [init-base-data.sql](D:/code/Han/sql/postgres/init-base-data.sql) 作为 PostgreSQL 基线菜单种子脚本，并更新三份 Compose 文件挂载该脚本；重写 [RuntimeCapabilityController.java](D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java)，改为基于 `DiscoveryClient` 判断真实注册服务，确保 `enabledModules` 和 `featureFlags` 与当前运行态一致；在前端 [index.ts](D:/code/Han/han-ui/src/router/index.ts) 为 `job / workflow / tenant / oss / open / ai` 补充 `module` 与 `feature` 元数据，并在 [Sidebar.vue](D:/code/Han/han-ui/src/layout/components/Sidebar.vue) 中加入按运行时能力过滤菜单的逻辑；本地完成 `han-system` 编译与 `han-ui` 构建通过；将相关变更提交为 `d1714a9` 并推送到 `codex/han-ui-remote-validate`，随后在 `95` 服务器拉取源码、重建 `han-system`，验证 `/system/runtime/capabilities` 现已返回 `workflow=false`、`ai=false` 且不再误报；使用隔离 PostgreSQL 容器挂载 [init.sql](D:/code/Han/sql/postgres/init.sql) 与 [init-base-data.sql](D:/code/Han/sql/postgres/init-base-data.sql) 完成首次初始化验证，确认 `sys_menu=37`、`sys_role_menu(role_id=1)=37`；新增 [runtime-sidebar.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts)，并通过 Playwright 直连 `95` 后端验证中配环境下 `workflow/ai` 菜单隐藏、`job/open/tenant/oss` 菜单保留。
- 关键决策与解决方案：不简化任何现有能力，只对验收缺口做“基线补齐 + 真实运行态对齐 + 自动化固化”；`small` 的 fresh deploy 问题不直接碰在线库，而是用隔离 PostgreSQL 容器验证初始化脚本，避免影响 `95` 现网；前端菜单过滤继续保留原有 tier 机制，但再叠加后端运行时能力，解决“环境里服务没起来，菜单却还暴露”的契约漂移；三档验收报告 [tier-validation-report-20260324.md](D:/code/Han/docs/tier-validation-report-20260324.md) 已重写为 UTF-8 中文并更新到最新状态。
- 使用技术栈/工具：Spring Boot 4、Spring Cloud Discovery、Vue 3、Vite、Playwright、Docker、PostgreSQL、MCP SSH、PowerShell、Maven、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/tier-validation-report-20260324.md`
- 修改文件：`docker-compose-small.yml`
- 修改文件：`docker-compose.yml`
- 修改文件：`docker-compose-full.yml`
- 修改文件：`sql/postgres/init-base-data.sql`
- 修改文件：`han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java`
- 修改文件：`han-ui/src/router/index.ts`
- 修改文件：`han-ui/src/layout/components/Sidebar.vue`
- 修改文件：`han-ui/tests/e2e/specs/runtime-sidebar.spec.ts`

### 2026-03-24 Workflow Docker 运行时闭环与 95 中配验收

- 本轮主要目标：继续推进 `medium` 档 `workflow` 闭环，补齐 Docker 运行时配置，完成 `95` 服务器上的真实部署、接口回归和前端页面联调，并把三档验收报告更新到最新结论。
- 已完成关键任务：为 [application-docker.yml](D:/code/Han/han-modules/han-workflow/src/main/resources/application-docker.yml) 新增 `DB / Redis / Nacos` 与 `Flowable schema` 的 Docker 运行时配置；将 `workflow` 相关修复提交并推送到 `codex/han-ui-remote-validate`，再在 `95` 服务器源码目录拉取最新分支；在 `95` 上使用容器化 Maven 重新打包 `han-workflow`，并重建 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-workflow:latest` 本地镜像；通过 Docker Compose 强制重建 `han-workflow` 容器，确认容器状态达到 `healthy`，`/actuator/health` 返回 `UP`，且不再错误连接 `localhost:6379`；验证 Flowable 首次建表成功，`act_%` 表数量为 `56`；通过网关真实登录后验证 `/workflow/definition/list?pageNum=1&pageSize=10` 返回 `200` 且数据结构正常；将 [runtime-sidebar.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts) 修正为从真实 `PW_API_URL` 读取运行时能力，并分别完成“本地最新前端 + 95 后端”与“95 前端 + 95 网关”的 Playwright 回归，结果均为 `1 passed`；同步重写 [tier-validation-report-20260324.md](D:/code/Han/docs/tier-validation-report-20260324.md)，将 `medium workflow` 状态更新为已通过，把 `full` 的主要阻塞收敛为 `han-ai:latest` 发布物缺失。
- 关键决策与解决方案：不删除也不弱化任何现有 `workflow` 能力，只补齐容器运行时环境，确保服务按文档承诺方式启动；前端验证继续坚持真实链路，不用 mock 能力接口，而是让测试脚本直接读取运行时能力后再断言菜单展示；对 `95` 环境采用“源码拉取 -> 容器化打包 -> 本地重建镜像 -> Compose 重启”的固定流程，和你要求的服务器部署流程保持一致；同时保留“后端动态菜单尚未完全覆盖 workflow”这一架构残留，不把它误记成 `workflow` 功能未通过。
- 使用技术栈/工具：Spring Boot 4、Flowable、Nacos、Redis、PostgreSQL、Docker Compose、Maven、Playwright、MCP SSH、PowerShell、Python、`curl`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/tier-validation-report-20260324.md`
- 修改文件：`han-modules/han-workflow/src/main/resources/application-docker.yml`
- 修改文件：`han-ui/tests/e2e/specs/runtime-sidebar.spec.ts`

### 2026-03-24 AI 模块一期骨架与基础接口落地

- 本轮主要目标：收口 `full` 档当前最大的真实阻塞，补齐仓库中缺失的 `han-ai` 后端模块骨架，并先落地文档与前端已接入的第一阶段基础接口：`AI 模型`、`知识库`、`MCP`、`Prompt 模板`、`Token 统计`。
- 已完成关键任务：确认仓库中原本不存在 [han-modules/han-ai](D:/code/Han/han-modules/han-ai) 模块，但网关路由、`docker-compose-full.yml`、运行时能力接口和前端 AI 菜单均已假定其存在；在 [han-modules/pom.xml](D:/code/Han/han-modules/pom.xml) 中注册 `han-ai` 子模块；新增 [han-ai/pom.xml](D:/code/Han/han-modules/han-ai/pom.xml)、[HanAiApplication.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/HanAiApplication.java)、[application.yml](D:/code/Han/han-modules/han-ai/src/main/resources/application.yml)、[application-docker.yml](D:/code/Han/han-modules/han-ai/src/main/resources/application-docker.yml) 和 [Dockerfile](D:/code/Han/han-modules/han-ai/Dockerfile)，让 `han-ai` 服务具备独立编译与 Docker 运行能力；补齐 `AI 模型 / 知识库 / 文档 / 段落 / MCP / Prompt` 对应的 PO、Query、Mapper、Service 与 Controller；实现 `/ai/model/*`、`/ai/kb/*`、`/ai/mcp/*`、`/ai/prompt/*`、`/ai/token/stats/*` 基础接口；知识库链路支持文档上传、落盘、段落切分、重建索引、命中测试和失败原因回显；新增 [AiAnalyticsMapper.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/mapper/AiAnalyticsMapper.java) 以支撑模型、用户、按日三种 Token 统计；补齐 [sql/han_ai.sql](D:/code/Han/sql/han_ai.sql) 中缺失的 `ai_prompt_template` 表和内置 Prompt 种子数据；本地执行 `mvn -gs settings.workspace.xml -Dmaven.repo.local=.m2/repository -pl han-modules/han-ai -am -DskipTests compile`，`2026-03-24` 编译通过。
- 关键决策与解决方案：不删改任何既有页面与路由，只把缺失的后端模块补出来；AI 表结构不强行套用通用 `BizEntity/TenantEntity`，而是按现有 `ai_*` 表的字段和主键单独建 PO，避免因为 `create_by/update_by` 字段类型不一致导致持久化扭曲；第一阶段不去伪造复杂的 `agent/workflow/chat/stream` 能力，而是先把文档与前端已明确依赖的基础接口做成真实可运行最小闭环；知识库命中测试先采用文本检索与段落索引，不虚报向量检索已完备；`pdf/docx` 目前允许上传并明确返回“暂未自动解析”的失败状态，不静默吞掉异常。
- 使用技术栈/工具：Spring Boot 4、Spring MVC、Spring Security、MyBatis-Plus、PostgreSQL、Maven、Docker、PowerShell、`rg`、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-modules/pom.xml`
- 修改文件：`sql/han_ai.sql`
- 修改文件：`han-modules/han-ai/pom.xml`
- 修改文件：`han-modules/han-ai/Dockerfile`
- 修改文件：`han-modules/han-ai/src/main/resources/application.yml`
- 修改文件：`han-modules/han-ai/src/main/resources/application-docker.yml`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/HanAiApplication.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/controller/AiModelController.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/controller/AiKnowledgeBaseController.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/controller/AiMcpController.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/controller/AiPromptController.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/controller/AiTokenStatsController.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiKnowledgeBaseServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiMcpServerServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiPromptTemplateServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiTokenStatsServiceImpl.java`

### 2026-03-25 Full 档 AI 二期部署与真实验收收口

- 本轮主要目标：继续推进 `full` 档部署验收，完成 AI 二期能力在 `95` 服务器上的真实部署、真实接口回归和真实前端 Playwright 页面回归，解除三档部署验收中 `full` 的核心阻塞。
- 已完成关键任务：确认 `95` 上 `han-ai` 与 `han-ui` 容器均为 `healthy`；将 `codex/han-ui-remote-validate` 最新提交 `192b528` 拉取到 `/opt/han/source/Han-ui-validate-20260323`；补齐并执行 `sql/ai_agent.sql` 初始化，完成 `ai_agent` 表落库；在 `95` 上使用容器化 Maven 重新打包 `han-ai`，重建 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest`，并通过 [docker-compose-full.yml](D:/code/Han/docker-compose-full.yml) 强制重建 `ai` 服务；在 `95` 上重新构建 `han-ui` 并以独立容器方式重新挂到 `docker_han-network`；通过真实网关接口验证 `/system/runtime/capabilities`、`/ai/model/all?modelType=LLM`、`/ai/agent/*`、`/ai/workflow/*`、`/ai/chat/*` 均可访问；本地提权执行 Playwright，直连 `95:3000` 与 `95:9090` 跑通 [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)，结果 `2 passed`。
- 关键决策与解决方案：不删改任何现有 AI 页面和路由，只补齐后端兼容逻辑与测试钩子；针对 `AI 聊天` 页面模型下拉为空的问题，在 [AiModelServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java) 中为 `selectAll(modelType)` 增加“启用模型优先，若无启用模型则回退全部模型”的兼容逻辑，避免历史种子数据全部禁用时前端无法发消息；同步将 [han_ai.sql](D:/code/Han/sql/han_ai.sql) 中默认 `DeepSeek Chat` 模型状态改为启用，并对 `95` 现网历史数据执行一次性补丁 `update ai_model set status='0' where model_name='DeepSeek Chat';`；前端验证坚持“真实浏览器 + 真实远端前后端”口径，不使用 mock；单独新增 UTF-8 报告 [full-tier-ai-validation-20260325.md](D:/code/Han/docs/full-tier-ai-validation-20260325.md) 落档本轮 `full` 验收结果。
- 使用技术栈/工具：Spring Boot 4、MyBatis-Plus、Vue 3、Playwright、Docker、Docker Compose、Maven、PostgreSQL、MCP SSH、PowerShell、Python、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java`
- 修改文件：`sql/han_ai.sql`
- 修改文件：`han-ui/src/views/ai/agent/index.vue`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
- 修改文件：`han-ui/src/views/ai/workflow/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-full.spec.ts`

### 2026-03-25 AI 对话流式与再生成链路稳定性回归

- 本轮主要目标：继续收口 `AI 对话` 的真实交互链路，在不简化任何现有功能的前提下，修复 `stream / regenerate / edit-regenerate` 在真实远端环境中的稳定性问题，并完成 `95` 服务器前后端一体化回归。
- 已完成关键任务：梳理 [AiChatController.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/controller/AiChatController.java) 与前端 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 的交互契约，确认 `stream`、`regenerate`、`edit-regenerate` 均已存在；新增 [ai-stream.ts](D:/code/Han/han-ui/src/utils/ai-stream.ts) 提取统一的 SSE 请求与解析逻辑，并兼容 `CRLF` 行结束符；为 AI 对话页补充消息节点与编辑、重新生成、停止生成相关测试钩子；重构前端发送消息与流式消费逻辑，复用统一流式工具，避免重复解析代码；定位 `95` 环境 `edit-regenerate` 失败根因并非后端能力缺失，而是前端对流式返回消息使用了临时 `messageId`，导致后续重新编辑时无法命中数据库中的真实消息；据此在 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 中新增当前会话消息同步逻辑，使流式结束后立即从后端回拉真实消息并刷新本地会话状态；扩展 [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)，新增覆盖“发送消息后重新生成”和“编辑用户消息后重新生成”的真实浏览器回归；将修复分别提交为 `f63eefc` 与 `3c3bc82` 并推送到 `codex/han-ui-remote-validate`；在 `95` 服务器源码目录拉取最新分支、重新构建 `han-ui` 镜像并重建独立前端容器；最终在真实 `95:3000 + 95:9090` 环境下重跑 Playwright，`3 passed`，覆盖 `AI 智能体/工作流页面`、`AI 对话发送消息`、`AI 对话重新生成与编辑后重新生成`。
- 关键决策与解决方案：不删减任何 AI 聊天能力，不绕开真实业务流程，也不把回归退化成假接口或只测首屏；流式解析层采用公共工具抽取，确保普通发送与再生成走同一套事件处理链；对 `edit-regenerate` 的修复选择“流式完成后主动与后端真实会话状态对齐”，而不是在前端伪造更复杂的 ID 映射，既保留现有交互体验，也避免再次出现临时消息与真实消息脱节；远端验证继续坚持“本地最新测试代码 + 95 最新前端镜像 + 95 真实后端”三层一致，不以单侧通过替代整体通过。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Docker、MCP SSH、PowerShell、Node.js、`eslint`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/utils/ai-stream.ts`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-full.spec.ts`

### 2026-03-25 AI 对话边界回归与 AI 管理页 Smoke 收口

- 本轮主要目标：继续向文档目标靠拢，在不简化任何现有功能的前提下，补齐 `AI 对话` 的边界自动化回归，覆盖 `stop`、`再次发送`、`刷新恢复当前会话`，并为 `知识库 / MCP / Prompt` 三个 AI 管理页接上稳定的 Playwright smoke；同时完成 `95` 服务器前端真实部署与远端整体验证。
- 已完成关键任务：在 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 为当前会话和模型选择补充轻量持久化，页面进入时优先恢复上次会话，若无持久化记录则回到最近一条会话；为 `AI 对话` 增加模型下拉测试钩子并优化停止生成后的滚动与状态恢复；为 [知识库页面](D:/code/Han/han-ui/src/views/ai/knowledge/index.vue)、[MCP 页面](D:/code/Han/han-ui/src/views/ai/mcp/index.vue)、[Prompt 页面](D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充稳定 `data-testid`；新增 [ai-chat-edge.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-chat-edge.spec.ts)，覆盖“停止生成后再次发送”和“刷新后恢复当前会话”；新增 [ai-admin-pages.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-admin-pages.spec.ts)，覆盖 `知识库 / MCP / Prompt` 三页的列表加载与创建入口 smoke；本地执行 `eslint`、`npm run build` 均通过；本地最新前端连 `95` 真实后端执行新增 Playwright 回归，结果 `5 passed`；将代码与文档以提交 `05f91b4` 推送到 `codex/han-ui-remote-validate`，随后在 `95` 服务器 `/opt/han/source/Han-ui-validate-20260323` 拉取最新代码、重建 `han-ui` 镜像并替换独立前端容器；最后以远端 `95:3000 + 95:9090` 再跑新增 Playwright 回归，结果仍为 `5 passed`。
- 关键决策与解决方案：不改变任何已有业务入口，也不删除欢迎页与新建会话能力，只在页面恢复阶段补一层“优先恢复当前、否则回到最近会话”的轻量状态恢复；`stop` 用例不再错误断言空输入状态下发送按钮必须可用，而是聚焦真实用户路径“停止后仍可继续发送下一条消息”；AI 管理页 smoke 不强行注入或篡改业务数据，而是以真实接口 `200`、页面入口可见、创建弹层可打开作为第一层保护网；为消除文档乱码风险，重写了 [full-tier-ai-validation-20260325.md](D:/code/Han/docs/full-tier-ai-validation-20260325.md) 为 UTF-8 正常中文版本，并补入本轮新增边界与 smoke 覆盖。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Docker、MCP SSH、PowerShell、Node.js、`eslint`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
- 修改文件：`han-ui/src/views/ai/knowledge/index.vue`
- 修改文件：`han-ui/src/views/ai/mcp/index.vue`
- 修改文件：`han-ui/src/views/ai/prompt/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-chat-edge.spec.ts`
- 修改文件：`han-ui/tests/e2e/specs/ai-admin-pages.spec.ts`

### 2026-03-25 AI 管理页深层交互回归补齐

- 本轮主要目标：继续向文档目标靠拢，在不简化任何现有功能的前提下，把 `知识库 / Prompt / MCP` 从基础 smoke 推进到真实交互回归，形成 `full` 档 AI 管理页更深一层的自动化保护网。
- 已完成关键任务：为 [index.vue](D:/code/Han/han-ui/src/views/ai/knowledge/index.vue) 补充知识库卡片动作、文档管理、上传、重建索引、命中测试相关测试钩子；为 [index.vue](D:/code/Han/han-ui/src/views/ai/mcp/index.vue) 补充刷新工具、查看工具与工具列表对话框测试钩子；为 [index.vue](D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充变量输入、渲染按钮、渲染结果测试钩子；新增 [ai-admin.ts](D:/code/Han/han-ui/tests/e2e/utils/ai-admin.ts) 作为 AI 管理页测试辅助，通过真实接口准备与清理数据；新增 [ai-knowledge-upload.txt](D:/code/Han/han-ui/tests/e2e/fixtures/files/ai-knowledge-upload.txt) 作为知识库上传样例；新增 [ai-admin-deep.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts)，覆盖“上传文档 -> 重新索引 -> 命中测试”“Prompt 变量渲染”“MCP 刷新工具 -> 查看工具列表”三条深层回归；本地执行 `eslint`、`vite build` 通过；本地最新前端连 `95` 真实后端执行 Playwright，结果 `3 passed`；将代码提交为 `3264431` 并推送到 `codex/han-ui-remote-validate`；在 `95` 服务器 `/opt/han/source/Han-ui-validate-20260323` 拉取最新代码、重建 `han-ui` 镜像并替换独立前端容器；最终以远端 `95:3000 + 95:9090` 重跑新增 Playwright 回归，结果 `3 passed`。
- 关键决策与解决方案：不为了测试方便去删减页面行为，也不把深层回归退化成只测接口；采用“API 稳定铺底 + 页面真实操作验证”的组合方式，既减少脏数据影响，又保留真实前端交互覆盖；知识库测试使用真实上传文件和真实命中测试，不用 mock 文档数据；Prompt 测试直接走真实预览与渲染接口；MCP 测试走真实刷新逻辑和工具列表对话框；远端验证继续坚持“95 前端 + 95 后端”整链路口径，而不是只用本地页面替代部署验证。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Docker、MCP SSH、PowerShell、Node.js、`eslint`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`
- 修改文件：`han-ui/src/views/ai/knowledge/index.vue`
- 修改文件：`han-ui/src/views/ai/mcp/index.vue`
- 修改文件：`han-ui/src/views/ai/prompt/index.vue`
- 修改文件：`han-ui/tests/e2e/fixtures/files/ai-knowledge-upload.txt`
- 修改文件：`han-ui/tests/e2e/specs/ai-admin-deep.spec.ts`
- 修改文件：`han-ui/tests/e2e/utils/ai-admin.ts`

### 2026-03-25 AI 管理页高级生命周期回归补齐

- 本轮主要目标：继续向文档目标靠拢，在不简化任何现有功能的前提下，把 AI 管理页回归继续推进到“多文档生命周期、Prompt 编辑更新、MCP 传输类型差异”这一层。
- 已完成关键任务：为 [index.vue](D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充编辑按钮、模板名称、模板内容、变量列表、提交按钮测试钩子；扩展 [ai-admin.ts](D:/code/Han/han-ui/tests/e2e/utils/ai-admin.ts)，补齐知识库统计字段、Prompt 模板补充字段，以及 MCP `stdio/sse` 差异化创建参数；新增 [ai-knowledge-upload-extra.txt](D:/code/Han/han-ui/tests/e2e/fixtures/files/ai-knowledge-upload-extra.txt) 第二份知识库上传样例；新增 [ai-admin-lifecycle.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts)，覆盖“多文档上传 -> 单文档删除 -> 统计回落”“Prompt 编辑 -> 保存 -> 预览 -> 渲染”“MCP `sse/stdio` 工具元数据差异”三条高级回归；本地执行 `eslint`、`vite build` 通过；本地最新前端连 `95` 真实后端执行新增 Playwright，结果 `3 passed`；将代码提交为 `71d0803` 并推送到 `codex/han-ui-remote-validate`；在 `95` 服务器 `/opt/han/source/Han-ui-validate-20260323` 拉取最新代码、重建 `han-ui` 镜像并替换独立前端容器；最终以远端 `95:3000 + 95:9090` 重跑新增 Playwright，结果 `3 passed`。
- 关键决策与解决方案：不删减任何页面行为，也不把高级回归退化成只测接口；知识库这条线直接用两份真实文件做生命周期验证，确保页面统计和后端真实状态同步；Prompt 这条线不只测新增和预览，而是把编辑保存后的再次渲染也纳入保护；MCP 这条线明确验证 `sse` 与 `stdio` 刷新后的工具元数据不同，不再只验证“能刷新”；远端验证继续坚持“95 前端 + 95 后端”整链路口径。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Docker、MCP SSH、PowerShell、Node.js、`eslint`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`
- 修改文件：`han-ui/src/views/ai/prompt/index.vue`
- 修改文件：`han-ui/tests/e2e/fixtures/files/ai-knowledge-upload-extra.txt`
- 修改文件：`han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts`
- 修改文件：`han-ui/tests/e2e/utils/ai-admin.ts`

### 2026-03-25 AI 管理页保护与传输回归补齐

- 本轮主要目标：继续向文档目标靠拢，在不简化任何现有功能的前提下，把 AI 管理页回归继续推进到“知识库整库删除、Prompt 内置模板删除保护、MCP streamable_http 传输类型”这一层。
- 已完成关键任务：为 [index.vue](D:/code/Han/han-ui/src/views/ai/knowledge/index.vue) 补充知识库卡片删除菜单测试钩子；为 [index.vue](D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充删除按钮测试钩子；扩展 [ai-admin.ts](D:/code/Han/han-ui/tests/e2e/utils/ai-admin.ts)，支持创建内置 Prompt 模板并增加删除保护探测接口；新增 [ai-admin-extended.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-admin-extended.spec.ts)，覆盖“知识库卡片删除后列表清空”“内置 Prompt 模板删除保护”“MCP streamable_http 工具元数据回归”三条新回归；本地执行 `eslint`、`vite build` 通过；本地最新前端连 `95` 真实后端执行新增 Playwright，结果 `3 passed`；将代码提交为 `7600142` 并推送到 `codex/han-ui-remote-validate`；在 `95` 服务器 `/opt/han/source/Han-ui-validate-20260323` 拉取最新代码、重新构建 `han-ui` 镜像并替换独立前端容器；最终以远端 `95:3000 + 95:9090` 重跑新增 Playwright，结果 `3 passed`，且容器状态恢复为 `healthy`。
- 关键决策与解决方案：不为了测试方便绕开页面真实交互，也不凭空扩展当前页面并不存在的“知识库批量删除”交互；知识库删除链路直接走卡片动作菜单，验证页面删除入口和后端真实状态一起回落；Prompt 删除保护不仅验证前端禁用态，也补一层真实接口删除失败校验，确保不是单纯把按钮灰掉；MCP 继续坚持按真实 transport type 构造数据，验证 `streamable_http` 刷新后工具元数据与 `sse/stdio` 不同；远端验证继续坚持“95 前端 + 95 后端”整链路口径。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Docker、MCP SSH、PowerShell、Node.js、`eslint`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`
- 修改文件：`han-ui/src/views/ai/knowledge/index.vue`
- 修改文件：`han-ui/src/views/ai/prompt/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-admin-extended.spec.ts`
- 修改文件：`han-ui/tests/e2e/utils/ai-admin.ts`

### 2026-03-25 AI 内置 Prompt 编辑保护远端收尾验证

- 本轮主要目标：继续推进 `full` 档 AI 模块验收收尾，在不简化任何现有功能的前提下，完成 `Prompt` 内置模板编辑保护的 `95` 远端真实部署验证，并把 AI 验收文档重写为 UTF-8 正常中文。
- 已完成关键任务：核对 `C:\Users\79033\.codex\skills\playwright\SKILL.md` 与 [full-tier-ai-validation-20260325.md](D:/code/Han/docs/full-tier-ai-validation-20260325.md) 等参考文档；确认 `95` 上 `han-ai` 已运行在提交 `85db33d`，并通过健康检查验证容器 `healthy`；识别并修正 `han-ui` 远端镜像重建只复制旧 `dist` 的问题，改为先用容器化 Node 在 `/opt/han/source/Han-ui-validate-20260323/han-ui` 执行 `npm ci --legacy-peer-deps && npm run build`，再重建并替换 `han-ui` 容器；清理临时 `han-ui-builder` 容器；本地提权执行 Playwright，直连 `95:3000` 与 `95:9090` 跑通 [ai-admin-extended.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-admin-extended.spec.ts)，结果 `4 passed`，新增覆盖“内置模板禁止编辑”；重写 [full-tier-ai-validation-20260325.md](D:/code/Han/docs/full-tier-ai-validation-20260325.md)，将 AI `full` 档验收结果统一整理为 UTF-8 正常中文，并补入本轮“页面禁编 + 接口拒编”双层保护结论。
- 关键决策与解决方案：不删减任何 AI 页面、接口和已有回归，只补保护和验证；确认 `han-ui` Dockerfile 仅复制 `dist` 后，采用“容器化前端构建 + 再重建 Nginx 镜像”的方式保证 `95` 上的前端代码真实生效，而不是误把旧产物当成最新验证；内置模板编辑保护坚持双层口径，既验证前端 [index.vue](D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 的禁用态，也验证后端 [AiPromptTemplateServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiPromptTemplateServiceImpl.java) 的拒绝态；遇到本地 Playwright `spawn EPERM` 时，不绕过真实浏览器，按规范改为提权执行，确保结果仍然来自真实浏览器自动化而不是降级替代。
- 使用技术栈/工具：Vue 3、TypeScript、Playwright、Docker、MCP SSH、PowerShell、Node.js、NPM、Maven、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`docs/full-tier-ai-validation-20260325.md`

### 2026-03-26 DashScope 真实连通修复与 95 标准发布链路校正

- 本轮主要目标：在不简化任何现有功能的前提下，把 AI 模型真实调用接到 `DashScope`，并严格纠正发布流程为“本地先 push，`95` 服务器 pull 代码后自行打包和构建镜像”，不再走本地 `jar` 旁路。
- 已完成关键任务：补齐 [AiModelCredentialResolver.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelCredentialResolver.java)，支持 `DASHSCOPE_API_KEY` 与多级环境变量优先解析；新增 [Sensitive.java](D:/code/Han/han-common/han-common-web/src/main/java/com/han/common/web/sensitive/Sensitive.java)、[SensitiveType.java](D:/code/Han/han-common/han-common-web/src/main/java/com/han/common/web/sensitive/SensitiveType.java)、[SensitiveSerializer.java](D:/code/Han/han-common/han-common-web/src/main/java/com/han/common/web/sensitive/SensitiveSerializer.java)，为模型 `apiKey` 提供掩码输出能力；调整 [AiModelPo.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/domain/po/AiModelPo.java)、[AiModelServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java)、[AiChatServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java)、[index.vue](D:/code/Han/han-ui/src/views/ai/model/index.vue)，实现“空白或掩码值不覆盖原始 Key、优先从环境变量读取真实 Key、模型测试走真实调用、对话链路优先真实模型、失败时保留原兜底回复”；更新 [docker-compose-full.yml](D:/code/Han/docker-compose-full.yml)，让 `ai` 服务支持 `DASHSCOPE_API_KEY` 注入；重写 [server-95-deploy-flow.md](D:/code/Han/docs/server-95-deploy-flow.md) 与 [deployment-guide.md](D:/code/Han/docs/deployment-guide.md)，明确 `95` 标准流程是“本地 push -> 95 pull -> 95 自己打包 -> 95 自己构镜像部署”，并新增 [settings.workspace.xml](D:/code/Han/settings.workspace.xml) 供 `95` 容器化 Maven 打包使用；在 `95` 上新建干净源码目录 `/opt/han/source/Han-dashscope-20260326`，验证代码已拉到提交 `17c6376`，并用容器化 Maven 成功完成服务端自打包；复现 `/ai/model/test/3` 失败后，在 `95` 上用独立 JDK 容器确认 `java.net.http.HttpClient` 对 `DashScope` 会稳定触发 `UnresolvedAddressException`，而 `HttpURLConnection` 同环境可返回 `401 invalid_api_key`，据此把 [AiOpenAiCompatibleClient.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiOpenAiCompatibleClient.java) 的底层实现从 `HttpClient` 切换为 `HttpURLConnection`，保留原有接口、错误语义和业务流转不变；本地使用 `JAVA_HOME + mvn --%` 完成 `han-ai` 编译通过，确认修复可构建。
- 关键决策与解决方案：不删减任何 AI 管理、测试和对话能力，也不把真实模型调用退化回假响应；部署链路坚决纠正为“代码先推远端，`95` 自己 pull 和打包”，后续不再采用上传本地 `jar` 的旁路方式；经过 `95` 上的对照实验，问题被定位为运行时 `JDK HttpClient` 与当前服务器环境的出站兼容异常，而不是 `DashScope key`、数据库 `baseUrl`、DNS 或 AI 业务代码本身，因此选择只替换底层 HTTP 执行器为 `HttpURLConnection`，最大程度保持现有接口契约、错误包装和后续 SSE/对话逻辑稳定；本地 Maven 自检同时规避了 `JAVA_HOME` 缺失和 `MAVEN_OPTS` 干扰本地仓库路径的问题，为后续同类构建提供了固定做法。
- 使用技术栈/工具：Java 21、Spring Boot、MyBatis-Plus、Vue 3、Docker、MCP SSH、PowerShell、Maven、`apply_patch`、`HttpURLConnection`、容器化 JDK/JShell。
- 修改文件：`README.md`
- 修改文件：`docker-compose-full.yml`
- 修改文件：`docs/deployment-guide.md`
- 修改文件：`docs/server-95-deploy-flow.md`
- 修改文件：`han-common/han-common-web/src/main/java/com/han/common/web/sensitive/Sensitive.java`
- 修改文件：`han-common/han-common-web/src/main/java/com/han/common/web/sensitive/SensitiveSerializer.java`
- 修改文件：`han-common/han-common-web/src/main/java/com/han/common/web/sensitive/SensitiveType.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/domain/po/AiModelPo.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelCredentialResolver.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiOpenAiCompatibleClient.java`
- 修改文件：`han-ui/src/views/ai/model/index.vue`
- 修改文件：`settings.workspace.xml`

### 2026-03-26 DashScope Docker DNS 抖动收口与 AI 模型管理真链路验收

- 本轮主要目标：在不简化任何现有功能的前提下，彻底收口 `95` 服务器上 `DashScope` 真实模型调用的 Docker DNS 抖动问题，并补齐 `AI 模型管理 + AI 对话` 的远端真实回归。
- 已完成关键任务：定位 `95` 上 `han-ai` 容器存在“系统层 `wget/getent` 可解析 `dashscope.aliyuncs.com`，但 JVM 侧偶发 `UnknownHostException`”的真实现象；验证仅加 `JAVA_OPTS` 的 `preferIPv4 + DNS cache` 参数可以改善但不能彻底消除抖动；更新 [han-modules/han-ai/Dockerfile](D:/code/Han/han-modules/han-ai/Dockerfile)、[docker-compose-full.yml](D:/code/Han/docker-compose-full.yml)、[docs/server-95-deploy-flow.md](D:/code/Han/docs/server-95-deploy-flow.md)、[docs/deployment-guide.md](D:/code/Han/docs/deployment-guide.md)，将 AI 容器的 IPv4/DNS 稳定参数固化进默认部署口径；在 [AiOpenAiCompatibleClient.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiOpenAiCompatibleClient.java) 增加“`HttpURLConnection` 命中 `UnknownHostException` 时，自动回退到容器内 `curl --ipv4` 执行同一 OpenAI-compatible 请求”的增强兜底；本地重新编译 `han-ai` 通过；将修复分两次提交并推送到 `codex/han-ui-remote-validate`，提交分别为 `10c72a5` 和 `33dc9b3`；严格按“本地 push -> 95 pull -> 95 容器化 Maven `clean package` -> 95 自己构镜像 -> 95 自己重启容器”链路完成远端部署；在 `95` 上用真实网关连续验证 `/ai/model/test/3` 和新建模型 `/ai/model/test/9` 共 `6` 次全部返回 `200`；清理临时调试模型；提权执行 Playwright，直连 `95:3000 + 95:9090` 跑通 [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts) 与 [ai-model.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts)，结果 `4 passed`。
- 关键决策与解决方案：不删除任何 AI 页面、接口、环境变量能力和真实调用逻辑，也不把问题简单掩盖成“偶发失败重试”；通过两轮 `95` 实测确认，单纯改 JVM 参数只能缓解、不能根治，真正稳妥的方案是在保留 Java 主链请求的前提下，为容器网络场景补一层系统级 `curl --ipv4` 回退，从而把 Docker DNS 抖动隔离在基础设施层之外；远端发布坚持使用 `clean package`，避免 Maven 增量编译误判导致新源码未真正进入 `jar`；浏览器回归继续坚持“远端前端 + 远端后端”的真实链路，不回退到本地 mock 或接口替代。
- 使用技术栈/工具：Java 21、Spring Boot、MyBatis-Plus、Vue 3、Playwright、Docker、MCP SSH、PowerShell、Maven、`apply_patch`、`curl --ipv4`、容器化构建。
- 修改文件：`README.md`
- 修改文件：`docker-compose-full.yml`
- 修改文件：`docs/deployment-guide.md`
- 修改文件：`docs/server-95-deploy-flow.md`
- 修改文件：`han-modules/han-ai/Dockerfile`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiOpenAiCompatibleClient.java`

### 2026-03-26 MaxKB 界面流程对标调研

- 本轮主要目标：在不简化任何现有功能的前提下，拉取 `MaxKB` 官方开源源码，拆解其界面组织方式、主流程设计和产品节奏，并与当前 `Han AI` 做结构化对比，为后续按批次吸收优点提供依据。
- 已完成关键任务：通过提权 `git clone` 将 `MaxKB` 官方源码拉取到独立研究目录 `D:\code\Han\_research\MaxKB-upstream`；确认本次对比源码提交为 `b0ba621e010e4347147bae337bcdbf103e5d4e9e`；完成对 `MaxKB` 管理端与聊天端双入口、应用首页、应用详情、知识库主页、知识库列表容器、模型主页、模型创建弹窗、聊天主组件、知识来源组件、执行详情组件、工作流画布等关键页面和路由的只读分析；同时回看 [index.ts](D:/code/Han/han-ui/src/router/index.ts)、[index.vue](D:/code/Han/han-ui/src/views/ai/model/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/knowledge/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/agent/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/workflow/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 作为 Han 侧对比基线；新增对标分析文档 [maxkb-han-ai-ui-flow-analysis-20260326.md](D:/code/Han/docs/maxkb-han-ai-ui-flow-analysis-20260326.md)，整理“应用优先、双入口、知识库产品化、模型供应商优先、对话可解释、工作流底座化”等可借鉴优点，并给出 Han AI 的第一批建议落地项。
- 关键决策与解决方案：不改动任何现有业务功能，也不在本轮直接开始大规模 UI 改版，而是先用“MaxKB 源码主流程拆解 + Han 现状对照”的方式把改造方向定清楚；MaxKB 源码放在独立研究目录，不混入当前仓库业务代码；对标重点放在信息架构和用户路径，而不是只抄样式；结论上优先建议 Han AI 学习 `MaxKB` 的“应用优先入口、应用详情分层、知识来源与执行解释、知识库工作台、供应商优先模型配置”，而不是优先照搬它更重的权限体系和超大节点数量。
- 使用技术栈/工具：Git、PowerShell、`rg`、`apply_patch`、官方开源仓库只读源码分析。
- 修改文件：`README.md`
- 修改文件：`docs/maxkb-han-ai-ui-flow-analysis-20260326.md`

### 2026-03-26 AI 应用优先入口第一阶段落地

- 本轮主要目标：在不简化任何现有功能的前提下，基于 [maxkb-han-ai-ui-flow-analysis-20260326.md](D:/code/Han/docs/maxkb-han-ai-ui-flow-analysis-20260326.md) 的结论，启动 `Han AI` 的第一段信息架构调整，让 `AI` 从“能力页平铺入口”开始向“应用优先入口”收拢。
- 已完成关键任务：调整 [index.ts](D:/code/Han/han-ui/src/router/index.ts)，将 `/ai` 默认入口从 `/ai/model` 改为 `/ai/application`，并新增 `AI应用` 子路由；新增 [index.vue](D:/code/Han/han-ui/src/views/ai/application/index.vue)，聚合展示“简单应用=智能体”“高级应用=工作流”，补齐应用总数、已发布数、快速创建、资源入口、统一卡片列表等首页能力；保持原有 [index.vue](D:/code/Han/han-ui/src/views/ai/agent/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/workflow/index.vue)、[index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 等页面全部保留不删不藏；为智能体和工作流页新增路由联动，支持从 `AI应用` 首页通过 `action=create` 直接打开原有创建弹窗，并支持通过 `action=chat` 直达原有调试对话弹窗；为智能体与工作流表单补充稳定测试钩子；新增 [ai-application.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-application.spec.ts)，覆盖“应用首页可见”和“从首页进入智能体/工作流创建”两条关键链路；本地执行 `npm run build` 通过，Playwright 执行新增回归结果 `2 passed`。
- 关键决策与解决方案：不做破坏式改版，不删除原有 `智能体/工作流/对话` 页面，也不强行把后台页面一次性改成 `MaxKB` 式复杂结构，而是先通过新增 `AI应用` 首页做“加法式重组”；首页只承担统一入口和统一视角，现阶段继续复用既有智能体/工作流页面作为第二层管理页，降低回归风险；为了避免无 `智能体/工作流` 权限用户被 `/ai/application` 卡住，在首页挂载时增加“按权限回退到首个可访问 AI 能力页”的保护逻辑；快速创建和调试链路采用路由查询参数联动，而不是重写原有业务弹窗，最大限度保留现有实现。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Vue Router、Playwright、PowerShell、`vue-tsc`、Vite、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/router/index.ts`
- 修改文件：`han-ui/src/views/ai/application/index.vue`
- 修改文件：`han-ui/src/views/ai/agent/index.vue`
- 修改文件：`han-ui/src/views/ai/workflow/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application.spec.ts`

### 2026-03-26 AI 应用详情页第一版

- 本轮主要目标：在不简化任何现有功能的前提下，为 AI 应用首页补齐第一版统一详情页，把“概览、设置、调试”三层视角先落下来，继续向 MaxKB 的应用工作台靠拢。
- 已完成关键任务：在 `han-ui/src/router/index.ts` 新增隐藏路由 `/ai/application/:type/:id` 作为统一应用详情入口；重写并清理 `han-ui/src/views/ai/application/index.vue` 的文案编码，保留原有首页结构并新增“查看详情”入口；新增 `han-ui/src/views/ai/application/detail.vue`，统一承接智能体和工作流两类应用的概览、设置、调试视角；详情页按路由参数动态复用 `getAiAgent/getAiWorkflow`，并聚合模型、知识库、MCP 资源名称用于展示；调试入口继续复用原有智能体页、工作流页、流程设计页与对话调试逻辑；新增 `han-ui/tests/e2e/specs/ai-application-detail.spec.ts`，用于锁定“首页进入详情页 -> 概览/设置/调试切换”的主链回归。
- 关键决策与解决方案：不删除原有 `智能体/工作流/对话/流程设计` 页面，也不重写现有调试逻辑，只在应用层增加统一详情视图；详情页通过 `type=agent|workflow` 动态分流，不新增后端接口，最大限度复用既有能力；首页同时顺手收口编码问题，避免乱码继续扩散到后续 UI 迭代。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Vue Router、Playwright、PowerShell、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/router/index.ts`
- 修改文件：`han-ui/src/views/ai/application/index.vue`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-26 AI 应用详情页第二版（发布/日志/访问入口骨架）

- 本轮主要目标：在不简化任何现有功能的前提下，为应用详情页补齐“发布、日志、访问入口”三块骨架，延续应用工作台的主线体验。
- 已完成关键任务：在 `han-ui/src/views/ai/application/detail.vue` 的“调试”页追加发布状态、访问入口、最近日志三块区域；不新增后端接口，仅展示应用当前状态并给出后续接入说明；在 `han-ui/tests/e2e/specs/ai-application-detail.spec.ts` 增加对应可见性断言，锁定 UI 骨架回归。
- 关键决策与解决方案：继续复用现有管理页与对话调试入口，不改变已有发布逻辑；新增区域全部为信息与入口层，避免侵入式重构。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 AI 应用详情页第三版（真实发布/访问入口/工作流日志）

- 本轮主要目标：在不简化任何现有功能的前提下，把 AI 应用详情页从“发布、日志、访问入口骨架”推进到“真实可用的详情工作台”，优先补齐工作流最近日志、应用发布切换、访问链接复制三条主链。
- 已完成关键任务：在 `han-modules/han-ai/src/main/java/com/han/ai/domain/query/AiConversationQuery.java` 新增 `workflowId` 查询条件，并在 `han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java` 的会话分页查询中接入该过滤能力；在 `han-ui/src/api/ai/index.ts` 补充 `AiConversationQuery` 类型并放宽 `listConversations` 的查询参数；重写 `han-ui/src/views/ai/application/detail.vue`，将“调试”页升级为真实详情工作台，支持应用发布/取消发布、访问链接生成与复制、工作流最近对话日志展示，同时保留智能体当前尚未具备应用级日志关联时的诚实占位提示；本地执行 `han-ai` 模块编译通过，执行 `ai-application-detail.spec.ts` 结果 `1 passed`。
- 关键决策与解决方案：不伪造智能体日志，也不为了统一展示而简化现有数据模型；考虑到当前会话表只有 `workflowId` 没有 `agentId`，本轮只把工作流日志做成真实链路，智能体仍明确提示“需要补应用级关联字段”；发布能力与访问入口均继续复用现有智能体、工作流管理能力和聊天入口，不新增破坏性后端接口；同时修正了本地 Maven 环境被 `MAVEN_OPTS=-Dmaven.repo.local=D:\.m2\repository` 干扰的问题，统一改用工作区本地仓库和 JDK 21 验证。
- 使用技术栈/工具：Java 21、Maven、Vue 3、TypeScript、Element Plus、Playwright、PowerShell、`apply_patch`。
- 修改文件：`README.md`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/domain/query/AiConversationQuery.java`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java`
- 修改文件：`han-ui/src/api/ai/index.ts`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 95 服务器标准链路编译运行（AI 应用工作台）

- 本轮主要目标：严格按“本机只开发代码，本地先提交并推送，`95` 服务器拉代码后自己编译、自己构镜像、自己运行验证”的标准流程，将 AI 应用工作台这批改动真正部署到 `95`。
- 已完成关键任务：在本地仅提交本次 AI 应用工作台相关文件，生成提交 `b32d205 feat(ai): add application workspace flows` 并推送到 `origin/codex/han-ui-remote-validate`；考虑到 `95` 上现有 `/opt/han/source/Han` 工作树存在脏改动，未覆盖原目录，而是新建独立验证目录 `/opt/han/source/Han-ai-app-validate-20260327` 拉取同一分支代码；在 `95` 上通过 `maven:3.9.9-eclipse-temurin-21` 容器自打 `han-ai` 包，产出 `han-modules/han-ai/target/han-ai.jar`；在 `95` 上通过 `node:20-alpine` 容器自装依赖并自构建 `han-ui/dist`；随后在 `95` 上自构建 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest` 与 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ui:latest` 两个镜像，并替换运行 `han-ai`、`han-ui` 容器。
- 关键决策与解决方案：不走本机上传 `jar` 或 `dist` 的旁路，也不覆盖 `95` 上已有脏工作树；为避免污染原验证目录，统一采用新的独立目录拉分支编译；`han-ui` 在 `95` 上构建时遇到 `pinia` 与 `pinia-plugin-persistedstate` 的 peer 依赖冲突，采用 `npm install --legacy-peer-deps` 仅作为服务器安装策略解决构建环境问题，不改任何业务代码；`han-ai` 重新启动时保留现有 `DashScope`、数据库、Redis、Nacos、RabbitMQ、JVM DNS 参数等环境变量，确保新镜像继承当前可用运行态。
- 使用技术栈/工具：Git、Docker、Maven 容器、Node 容器、Nginx 镜像、PowerShell、`local95` MCP。
- 验证结果：`95` 上 `han-ai` 和 `han-ui` 容器均为 `healthy`；`http://127.0.0.1:9208/actuator/health` 返回 `UP`；`http://127.0.0.1:3000/` 返回 `200`；带 `Accept: text/html` 访问 `http://127.0.0.1:3000/ai/application` 返回 `200`；`http://127.0.0.1:9090/system/runtime/capabilities` 返回 `200`，并显示 `tier=full`、`ai=true`。
- 修改文件：`README.md`

### 2026-03-27 95 运行态 AI 应用页面联调回归

- 本轮主要目标：在 `95` 服务器完成标准链路部署后，继续用真实运行态验证 `AI应用` 首页和统一详情页主链，确保上线容器而不是本机开发态可用。
- 已完成关键任务：使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 作为 Playwright 环境变量，执行 `han-ui/tests/e2e/specs/ai-application.spec.ts` 和 `han-ui/tests/e2e/specs/ai-application-detail.spec.ts`；覆盖“AI 应用首页加载”“从应用首页进入智能体/工作流原管理页创建链路”“应用详情页概览/设置/调试三层视角”三条回归。
- 关键决策与解决方案：不在本机重新起前端或后端服务，全部回归都指向 `95` 的新容器；为绕开本机 Playwright 的 `spawn EPERM` 环境问题，沿用已批准的提权规则启动浏览器子进程，但验证目标仍然是远端 `95` 运行态。
- 使用技术栈/工具：Playwright、PowerShell、`95` 远端容器运行态联调。
- 验证结果：`3 passed`，说明 `AI应用` 首页、创建跳转链路、统一详情页在 `95` 新部署镜像上均可用。
- 修改文件：`README.md`

### 2026-03-27 AI 应用详情页第四版（访问入口深化与日志跳转）

- 本轮主要目标：在不简化任何现有功能的前提下，继续把 `AI` 应用详情页做成更像工作台的主入口，补强真实发布状态说明、访问入口明细，以及从工作流最近日志跳回指定对话的链路。
- 已完成关键任务：重写 [detail.vue](D:/code/Han/han-ui/src/views/ai/application/detail.vue)，将应用详情页升级为第四版工作台，补齐发布状态说明、详情入口/管理入口/调试入口三类真实路径展示与复制按钮，并将工作流最近日志升级为可点击跳转；补强 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue)，支持通过 `conversationId` 路由参数恢复指定会话、同步会话切换到 URL，并在新建会话时清理查询参数；更新 [ai-application-detail.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts)，优先选取工作流应用做详情验证，并在存在日志时校验“详情页日志 -> AI 对话页指定会话”跳转链路；本地执行 `npm run build` 通过，本地指向旧版 `95` 页面时明确定位出“缺少新测试钩子”不是代码问题；随后将功能提交 `1e1b451 feat(ui): deepen ai application detail workspace` 推送到 `origin/codex/han-ui-remote-validate`，由 `95` 服务器在 `/opt/han/source/Han-ai-app-validate-20260327` 自行拉取代码、用 Node 容器重新构建 `han-ui/dist`、重建 `han-ui` 镜像并替换运行容器。
- 关键决策与解决方案：不伪造智能体日志，也不新造与原有管理页平行的调试体系；工作流日志仍然复用已有真实会话数据，只补前端详情工作台与聊天页之间的路由衔接；本机只做代码开发与推送，最终验证严格走“本地 push -> 95 pull -> 95 自编译 -> 95 自构镜像 -> 95 自运行”标准流程；由于 `han-ui` Dockerfile 只复制 `dist`，因此在 `95` 上必须先用容器化 Node 构建前端产物，再构建镜像，不能跳过构建步骤直接替换容器。
- 使用技术栈/工具：Vue 3、TypeScript、Vue Router、Element Plus、Playwright、Vite、Docker、Node 容器、PowerShell、`local95` MCP、`apply_patch`。
- 验证结果：`95` 上新前端容器 `han-ui` 重新部署后为 `healthy`；`curl -I -H 'Accept: text/html' http://127.0.0.1:3000/ai/application` 返回 `200`；使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 执行 `ai-application.spec.ts` 和 `ai-application-detail.spec.ts`，结果 `3 passed`，说明应用首页、详情工作台、访问入口和日志跳转链路均在 `95` 真环境上通过。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 AI 应用详情页第五版（发布就绪度、访问入口卡片、日志详情抽屉）

- 本轮主要目标：在不简化任何现有功能的前提下，继续把 `AI` 应用详情页向应用工作台推进，补上更清晰的发布就绪度、可直接打开的访问入口卡片，以及最近日志的详情抽屉。
- 已完成关键任务：增强 [detail.vue](D:/code/Han/han-ui/src/views/ai/application/detail.vue)，在发布区新增“模型配置、系统提示词、交付入口、知识增强”四项发布就绪度面板；在访问入口区新增三类真实入口卡片，分别承接详情页、管理页、调试页，并支持直接打开与复制；在日志区新增运行日志详情抽屉，点击工作流日志卡片可查看会话摘要、消息数、时间、对话入口和调试入口，再从抽屉继续跳转到指定对话；同步更新 [ai-application-detail.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts)，补充对发布就绪度、访问入口卡片和日志抽屉的回归断言；本地执行 `npm run build` 通过后，将功能提交 `c8342e8 feat(ui): enrich ai application delivery workspace` 推送到 `origin/codex/han-ui-remote-validate`，并由 `95` 在 `/opt/han/source/Han-ai-app-validate-20260327` 自行拉取代码、自行用 Node 容器构建前端产物、自行构建并替换 `han-ui` 容器。
- 关键决策与解决方案：不伪造不存在的执行详情，也不为了表现层统一去改动当前后端数据模型；日志抽屉先诚实复用已有真实会话数据，后续如果后端补齐执行节点、知识来源和耗时信息，再在同一抽屉中继续扩展；访问入口不再只展示路径字符串，而是提升为更接近交付工作台的入口卡片，但仍然复用现有详情页、管理页和调试页，不新造平行页面。
- 使用技术栈/工具：Vue 3、TypeScript、Vue Router、Element Plus、Playwright、Vite、Docker、Node 容器、PowerShell、`local95` MCP、`apply_patch`。
- 验证结果：`95` 上新前端容器 `han-ui` 重新部署后为 `healthy`；`curl -I -H 'Accept: text/html' http://127.0.0.1:3000/ai/application` 返回 `200`；使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 再次执行 `ai-application.spec.ts` 和 `ai-application-detail.spec.ts`，结果仍为 `3 passed`，说明第五版详情工作台在 `95` 真环境上稳定可用。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 AI 应用详情页第六版（知识来源与执行信息侧栏骨架）

- 本轮主要目标：在不简化任何现有功能的前提下，继续沿着 `MaxKB` 的解释面板思路，把 AI 应用详情页日志抽屉升级成“知识来源 + 执行信息”双侧栏骨架，并在 `95` 真环境里锁定这条链路。
- 已完成关键任务：增强 [detail.vue](D:/code/Han/han-ui/src/views/ai/application/detail.vue)，在工作流日志抽屉中新增“知识来源”侧栏，展示当前应用绑定的知识库标签，并明确提示当前消息接口尚未返回结构化引用片段、命中文档位置和来源得分；新增“执行信息”侧栏，实时加载对应会话消息，通过 `listChatMessages` 取回真实对话记录后展示最近问题、最近回复摘要、回复消息 ID、Token 消耗、回复时间、回复长度等摘要信息；同步升级 [ai-application-detail.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts)，新增对“知识来源面板”和“执行信息面板”的可见性断言；本地仅提交功能代码，生成提交 `c070a2c feat(ui): add ai log insight side panels` 并推送到 `origin/codex/han-ui-remote-validate`；考虑到 `95` 上原验证目录存在前端构建产物脏改动，本轮新建独立目录 `/opt/han/source/Han-ai-insight-20260327` 重新拉取同分支代码，由 `95` 自己使用 Node 容器完成 `npm install --legacy-peer-deps && npm run build`、自己构建 `han-ui` 镜像并替换运行容器。
- 关键决策与解决方案：不伪造后端尚未提供的结构化知识引用和节点执行轨迹，而是先把真实可用的信息组织成诚实的解释面板骨架；知识来源只展示当前应用实际绑定的知识库，不捏造命中明细；执行信息直接基于会话真实消息计算摘要，确保后续后端补齐引用片段、执行节点、重排分数后可以无缝扩展到同一块 UI；`95` 上现有验证目录已被 `dist` 和 `package-lock` 等构建产物污染，因此不覆盖旧目录，而是改用新的独立目录自拉代码、自构建、自运行，避免破坏现有回滚面。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Vite、Docker、Node 容器、Git、PowerShell、`local95` MCP、`apply_patch`。
- 验证结果：本地 `npm run build` 通过；`95` 上新构建的 `han-ui` 容器状态为 `healthy`，`curl -I -H 'Accept: text/html' http://127.0.0.1:3000/ai/application` 返回 `200`；使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 运行 `ai-application.spec.ts` 和 `ai-application-detail.spec.ts`，结果 `3 passed`，说明第六版日志抽屉解释面板已在 `95` 真环境上通过。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 AI 应用详情页第七版（知识来源明细卡片与执行阶段占位）

- 本轮主要目标：在不简化任何现有功能的前提下，把 AI 应用详情页日志抽屉继续向 `MaxKB` 的解释工作台靠拢，补齐知识来源明细卡片和执行阶段结构化占位，并继续在 `95` 真环境锁定这条链路。
- 已完成关键任务：增强 [detail.vue](D:/code/Han/han-ui/src/views/ai/application/detail.vue)，在“知识来源”侧栏中新增知识库明细卡片，展示当前应用真实绑定的知识库类型、状态、文档数、段落数、字符数，并在未绑定知识库时保留诚实的空状态占位；在“执行信息”侧栏中新增五段结构化阶段列表，分别覆盖“会话定位、消息明细、知识增强、工具执行、模型回复”，并根据真实会话消息、知识库绑定情况、MCP 绑定情况动态标记“已定位、已拉取、待引用、待轨迹、已生成”等状态；同步升级 [ai-application-detail.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts)，增加对知识来源卡片列表和执行阶段列表的断言；本地 `npm run build` 通过后，将功能提交 `b9183f0 feat(ui): deepen ai log insight workspace` 推送到 `origin/codex/han-ui-remote-validate`。
- 关键决策与解决方案：不伪造后端尚未提供的结构化引用、来源得分和节点轨迹，而是只基于当前已经真实存在的知识库配置、MCP 绑定和聊天消息来组织解释面板；在 `95` 部署中额外定位出一个关键问题，首次镜像替换时实际上吃到了仓库里旧 `dist`，导致页面白屏和回归失败，因此本轮明确把“先让 `95` 真正完成前端构建，再基于新产物重建镜像”作为固定校验步骤；为避免脏工作树影响拉取，本轮继续使用新的独立目录 `/opt/han/source/Han-ai-insight-20260327-v2` 完成远端构建和替换。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Playwright、Vite、Docker、Node 容器、Git、PowerShell、`local95` MCP、`apply_patch`。
- 验证结果：本地 `npm run build` 通过；`95` 上先用旧 `dist` 替换镜像时出现白屏并触发 Playwright `3 failed`，随后定位并修正为“先真实构建 `dist` 再构镜像”后，`han-ui` 容器恢复为 `healthy`，首页引用到新的资源包；使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 重新执行 `ai-application.spec.ts` 和 `ai-application-detail.spec.ts`，结果 `3 passed`，说明第七版知识来源卡片与执行阶段占位已在 `95` 真环境稳定通过。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/application/detail.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-application-detail.spec.ts`

### 2026-03-27 AI 对话页解释工作台对齐与 95 真环境回归

- 本轮主要目标：在不简化任何现有功能的前提下，把 [AI 对话页](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 往 `MaxKB` 的解释工作台体验继续靠拢，为通用聊天页补上“应用上下文、知识来源、执行信息”右侧面板，并在 `95` 真环境完成标准链路部署和回归。
- 已完成关键任务：重构 [AI 对话页](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 的主工作区布局，在保留现有会话列表、消息流、停止生成、重新生成、编辑重生成全部原功能的基础上，新增右侧 `chat-inspector` 解释工作台；右侧工作台补齐“应用上下文”区块，按真实 `route.query.agentId/workflowId` 和当前会话 `workflowId` 识别上下文并拉取智能体或工作流详情，同时提供“查看应用详情 / 打开管理页”入口；补齐“知识来源”区块，基于真实知识库目录展示当前上下文绑定的知识库卡片、状态、文档数、段落数和字符数；补齐“执行信息”区块，展示当前模型、会话 ID、消息数、最近回复 Token、最近更新时间，并用五段结构化阶段列表承接“应用上下文、会话定位、知识增强、工具执行、模型回复”；同步升级 [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)，增加对 `ai-chat-inspector`、上下文面板、知识来源面板、执行信息面板和五段阶段列表的断言；本地执行 `npm run build` 通过，功能提交为 `e25fe63 feat(ui): align ai chat with insight workspace` 并已推送到 `origin/codex/han-ui-remote-validate`。
- 关键决策与解决方案：不另造一套聊天体系，也不把对话页改成和应用详情页割裂的另一种信息结构，而是直接复用应用详情页已经成型的“知识来源 + 执行信息”语言做对齐；对话页右侧所有信息都尽量走真实数据，不伪造后端尚未返回的结构化引用片段和工具轨迹，缺失部分继续诚实占位；部署严格遵守“本机只开发代码，本地只提交并推送，`95` 服务器新目录拉代码、自己构建 `dist`、自己构镜像、自己替换容器”的标准流程，本轮新建远端目录 `/opt/han/source/Han-ai-chat-workspace-20260327` 完成构建；考虑到 `95` 上前端依赖已有缓存，本轮复用前一套验证目录中的 `node_modules` 和 `package-lock.json`，再由 `95` 使用 `node:20-alpine` 容器自行生成新 `dist`，最后基于新产物构建 `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ui:latest` 并替换现网容器，同时为旧镜像打了 `backup-han-ui:20260327-chat-inspector` 备份标签。
- 使用技术栈/工具：Vue 3、TypeScript、Element Plus、Vue Router、Playwright、Vite、Docker、Node 容器、Git、PowerShell、`local95` MCP、`apply_patch`。
- 验证结果：`95` 新构建的 `han-ui` 容器状态为 `healthy`，`curl -I -H 'Accept: text/html' http://127.0.0.1:3000/ai/chat` 返回 `200`；远端构建产物中可检索到 `ai-chat-inspector` 标识，确认新面板已进入镜像；使用远端前端 `http://10.18.35.95:3000` 与远端网关 `http://10.18.35.95:9090` 执行 `tests/e2e/specs/ai-full.spec.ts`，结果 `3 passed`，说明 AI 智能体页、工作流页、聊天页发送消息、重新生成、编辑重生成和新的解释工作台面板均在 `95` 真环境通过。
- 修改文件：`README.md`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
- 修改文件：`han-ui/tests/e2e/specs/ai-full.spec.ts`

### 2026-03-31 AI 对话结构化知识引用与工具轨迹闭环（95 真环境）

- 本轮主要目标：沿着“结构化知识引用 + MCP 工具轨迹”的最小闭环继续推进，在不删减任何现有 AI 能力的前提下，把后端真实命中的知识片段与 MCP 工具元数据从“内容字符串里隐含”提升为“消息级结构化字段”，并在 `95` 真环境完成远端部署和验收。
- 已完成关键任务：在 [AiChatServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java) 中让 `stream / regenerate / edit-regenerate` 的 SSE 在 `delta` 之后追加 `meta` 事件，真实回传 `messageId / tokenCount / knowledgeSources / toolExecutions`；在 [ai-stream.ts](D:/code/Han/han-ui/src/utils/ai-stream.ts) 中补齐 `meta` 事件解析和 `onMeta` 回调；在 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 中新增 `streamMeta`，让聊天页右侧解释面板优先消费最新一条助手消息的真实结构化元数据，而旧的会话回拉回退链路继续保留；随后继续补齐 workflow 语境下的上下文保持逻辑，在 [AiChatServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java) 中支持 `AiChatRequest.workflowId` 走真实工作流上下文，并在已有 workflow 会话发送新消息时自动回收真实会话上下文；在 [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue) 中让流式发送在 workflow 语境下补发 `workflowId`。
- 关键决策与解决方案：不改数据库表结构，只在消息响应层做运行时结构化增强；不覆盖 `95` 上已有脏工作树，而是新建干净目录 `/opt/han/source/Han-ai-chat-structured-meta-20260331` 拉取 `codex/han-ui-remote-validate`；严格遵守“本地 push -> 95 pull/clone -> 95 容器化 Maven/Node 自构建 -> 95 自构镜像 -> 95 自替换容器”的标准链路；由于 `95` 上直接 `npm install --legacy-peer-deps` 命中过依赖缺失，前端构建改为复用已验证目录 `/opt/han/source/Han-ai-chat-workspace-20260327/han-ui` 中可用的 `node_modules` 与 `package-lock.json`，再在新目录里只跑 `npm run build`，保证远端构建仍然来自 `95` 本机且不改业务依赖。
- 使用技术栈/工具：Spring Boot、Vue 3、TypeScript、SSE、Docker、`maven:3.9.9-eclipse-temurin-21`、`node:20-alpine`、PowerShell、Git、`local95` MCP、`apply_patch`。
- 验证结果：本地仅提交本轮文件并推送两个提交 `4769e04 feat(ai): stream structured chat metadata`、`ba4abd3 fix(ai): preserve workflow chat context`；`95` 上 `han-ai` 与 `han-ui` 新镜像重建完成并替换运行容器，容器状态均为 `healthy`；通过 `curl -N` 直连 `http://127.0.0.1:9090/ai/chat/stream`，在临时验证 workflow `workflow_id=2` 上真实收到非空 `meta` 事件，其中 `knowledgeSources` 命中 `kb_id=13 / paragraph_id=16`，`toolExecutions` 命中临时 MCP `mcp_id=15`，且工具名 `structured_lookup / tool_trace_probe` 已进入结构化返回；随后通过 `GET /ai/chat/messages/32` 验证同一条会话的助手消息已能在消息回拉接口中返回相同的 `knowledgeSources / toolExecutions` 结构；再通过第二次 `conversationId=32 + workflowId=2` 的流式请求验证 workflow 会话续聊时 `meta` 仍保持非空结构，说明“流式即时 + 会话回拉 + 续聊保持上下文”三条链路已经闭环。
- 修改文件：`README.md`
- 修改文件：`han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiChatServiceImpl.java`
- 修改文件：`han-ui/src/utils/ai-stream.ts`
- 修改文件：`han-ui/src/views/ai/chat/index.vue`
