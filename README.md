# han Cloud - 企业级微服务平台

Spring Boot 4.1 | Spring Cloud 2025 | PostgreSQL | Java 21 | Vue 3

## 项目简介

han Cloud 是一个基于 Spring Boot 4.1 + Spring Cloud 2025 的企业级多租户微服务平台，采用前后端分离架构，融合 JobFlow 任务调度理念，提供从核心后台到 AI 增强能力的完整企业应用开发方案。

平台按 `small / medium / full` 三档能力组织，覆盖：

- 核心后台
- 多租户与工作流
- 开放平台与文件服务
- 代码生成
- AI、Prompt、MCP、Agent、Chat、Token

## 核心特性

- 微服务架构：Spring Cloud 2025 + Nacos 服务治理
- 多租户支持：逻辑隔离 / 物理隔离 / 混合隔离
- JobFlow 任务调度：全链路 TraceId、真分片、云原生配置
- OAuth2 认证：Spring Authorization Server 1.5.2
- 工作流引擎：Flowable 7.2.0 集成
- Virtual Threads：Java 21 虚拟线程全面启用
- 声明式 HTTP 客户端：`@HttpExchange` + 自动注入
- 全链路追踪：Micrometer Tracing + OpenTelemetry
- RFC 7807 错误响应：ProblemDetail 标准化异常结构
- Java Record：不可变值对象与轻量 DTO
- 现代前端：Vue 3 + TypeScript + Vite
- 可插拔组件：按 tier 灵活选择中间件与模块

## 架构设计

### 技术栈

| 分类 | 技术 | 版本 | 说明 |
| --- | --- | --- | --- |
| 核心框架 | Spring Boot | 4.1.0 | Virtual Threads + ProblemDetail |
| 微服务 | Spring Cloud | 2025.1.2 | 微服务基础框架 |
| 服务治理 | Spring Cloud Alibaba | 2025.1.0.0 | Nacos 3.x |
| 追踪 | Micrometer Tracing | BOM 管理 | OpenTelemetry 集成 |
| 数据库 | PostgreSQL | 18.1 | 主数据库 |
| 缓存 | Redis | 7 | 缓存与分布式锁 |
| 对象存储 | RustFS | 1.0.0 | S3 兼容对象存储 |
| 消息队列 | RabbitMQ | 3.x | 中型部署默认使用 |
| 可选中间件 | Kafka / Elasticsearch | 3.x / 8.x | full 建议外挂 |
| 服务治理 | Nacos | 3.1 | 注册中心与配置中心 |
| 任务调度 | JobFlow | 自研 | 分布式任务调度 |
| 工作流 | Flowable | 7.2.0 | BPM 引擎 |
| 认证授权 | Spring Authorization Server | 1.5.2 | OAuth2 / OIDC |
| 前端 | Vue 3 + TS + Vite | - | 管理端前端 |

### 项目结构

```text
Han/
├── han-common/          # 公共模块
├── han-starter/         # 自动装配 Starter
├── han-gateway/         # 网关服务
├── han-auth/            # 认证服务
├── han-api/             # API 接口定义
├── han-modules/         # 业务模块
├── han-ui/              # 前端项目
├── docs/                # 正式文档入口
├── sql/                 # 正式 SQL 入口
├── deploy/              # 正式部署入口
└── scripts/             # 校验与辅助脚本
```

### 模块说明

| 模块 | 说明 |
| --- | --- |
| `han-gateway` | 网关与统一外部入口 |
| `han-auth` | 登录、验证码、认证与鉴权 |
| `han-system` | 系统管理、运行时能力、通知、监控 |
| `han-job` | JobFlow 调度与监控 |
| `han-tenant` | 多租户管理 |
| `han-workflow` | 工作流定义、实例、待办、已办 |
| `han-open` | 开放平台与 OAuth2 / SSO |
| `han-file` | 文件与 OSS |
| `han-ai` | 模型、知识库、Prompt、MCP、应用、对话 |
| `han-gen` | 代码生成 |

## 部署方案

### 三档部署矩阵

| 档位 | 适用场景 | 默认能力 | 正式部署入口 |
| --- | --- | --- | --- |
| `small` | 本地开发 / 功能验证 / 演示 | 核心后台、通知、任务调度、基础监控 | `deploy/small/docker-compose.yml` |
| `medium` | 小团队联调 / 标准测试环境 | `small` + 租户、工作流、开放平台、文件服务 | `deploy/medium/docker-compose.yml` |
| `full` | 完整业务联调 / AI 联调 / 发布预演 | `medium` + AI 与代码生成 | `deploy/full/docker-compose.yml` |

### 能力矩阵

| Tier | 核心模块 | 扩展模块 | 典型能力 |
| --- | --- | --- | --- |
| `small` | gateway、auth、system、job | - | 用户中心、公告通知、参数配置、基础监控 |
| `medium` | `small` 全部能力 | tenant、workflow、open、file、RustFS、RabbitMQ | 多租户、工作流、开放平台、文件服务 |
| `full` | `medium` 全部能力 | ai、gen | AI 与增强能力 |

运行时层级通过 `HAN_DEPLOY_TIER=small|medium|full` 控制，前端优先读取 `/system/runtime/capabilities` 做真实降级展示。

## 快速开始

### small

```bash
docker compose -f deploy/small/docker-compose.yml up -d
```

访问：

- UI：`http://localhost:3100`
- Gateway：`http://localhost:19090`
- Nacos：`http://localhost:18848/nacos`

### medium

```bash
docker compose -f deploy/medium/docker-compose.yml up -d
```

访问：

- UI：`http://localhost:3200`
- Gateway：`http://localhost:29090`

### full

```bash
docker compose -f deploy/full/docker-compose.yml up -d
```

访问：

- UI：`http://localhost:3000`
- Gateway：`http://localhost:9090`

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [01-产品与架构总览](/D:/code/Han/docs/01-产品与架构总览.md) | 产品定位、拓扑、能力矩阵、A/I/B 架构、开放平台能力 |
| [02-开发手册](/D:/code/Han/docs/02-开发手册.md) | 本地开发、技术栈约束、开发规范、对接口径、安全与测试要求 |
| [03-部署手册](/D:/code/Han/docs/03-部署手册.md) | 三档部署、本地开发部署、Kubernetes 说明、SQL/Nacos 初始化、回滚与排障 |
| [04-测试与验收手册](/D:/code/Han/docs/04-测试与验收手册.md) | 测试策略、手测清单、Playwright、当前通过状态 |
| [05-运维与95环境手册](/D:/code/Han/docs/05-运维与95环境手册.md) | 95 目录、发布链路、Nacos / PostgreSQL 运维、清理与排障 |
| [06-牛马协作总规则](/D:/code/Han/docs/06-牛马协作总规则.md) | 仓库、文档、SQL、部署、发布与验证规则 |
| [07-仓库整理与重构执行计划](/D:/code/Han/docs/07-仓库整理与重构执行计划.md) | 最终目标结构与执行计划 |
| [docs/index.md](/D:/code/Han/docs/index.md) | 正式文档索引页 |

## SQL 与部署入口

- 正式 SQL 入口：[sql/README.md](/D:/code/Han/sql/README.md)
- 正式部署入口：`deploy/small`、`deploy/medium`、`deploy/full`
- 历史 SQL 与历史文档均已迁入 `archive/`，不再作为正式入口

## JobFlow 核心特性

han Cloud 将 JobFlow 调度能力深度集成到微服务体系中：

- 统一服务治理：复用 Nacos
- TraceId 全链路透传
- 真分片与锁保护
- 配置可通过 Nacos 管理
- 更易接入统一监控与日志系统

示例调用目标：

```java
orderSyncTask.sync(100000,10)
```

典型日志上下文：

```text
[traceId=abc123] [jobId=5] [shardIndex=0]
```

## 开发指南

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+
- Docker 26+

### 本地开发

```powershell
mvn -gs settings.workspace.xml -DskipTests compile
```

```powershell
cd han-ui
pnpm install
pnpm build
```

## 当前诚实边界

- `AI Graph`：未开发
- `Embed Chat`：未开发
- `pdf/docx` 自动解析：部分开发，当前仅支持上传，不自动解析

## 服务端口

| 服务 | small | medium | full |
| --- | --- | --- | --- |
| UI | 3100 | 3200 | 3000 |
| Gateway | 19090 | 29090 | 9090 |
| PostgreSQL | 15432 | 25432 | 5432 |
| Redis | 16379 | 26379 | 6379 |
| Nacos | 18848 | 28848 | 8848 |

## 说明

- `master` 是唯一长期分支
- 正式文档只认 `docs/`
- 正式 SQL 只认 `sql/`
- 正式部署只认 `deploy/`
- 95 只允许从 `/opt/han/repo/Han` 与 `/opt/han/deploy/{small,medium,full}` 发布
