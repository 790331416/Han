# Han Cloud 部署指南

## 1. 基线说明

部署前先统一两组变量：

- `HAN_DEPLOY_TIER=small|medium|full`
- `HAN_INNER_AUTH_SECRET=<same-secret-for-all-services>`

这两项分别控制运行时层级和服务间内部鉴权。网关会剥离外部伪造的内部鉴权头，服务端会对 `@InnerAuth` 接口做签名校验。

能力矩阵请先看：[capability-matrix.md](./capability-matrix.md)

## 2. 环境要求

| 项目 | 建议版本 |
|------|----------|
| JDK | 21 |
| Maven | 3.9+ |
| Docker | 26+ |
| Docker Compose | 2.x |
| Node.js | 18+ |

## 3. 构建建议

项目默认 Maven 安装目录里可能存在全局 `settings.xml`，为了避免本地仓库路径写死，建议在仓库根目录使用工作区配置：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS=''
mvn -gs settings.workspace.xml -DskipTests compile
```

`settings.workspace.xml` 会把 Maven 本地仓库落到仓库内的 `.m2/repository`，更适合 CI 和多环境协作。

## 4. 三档部署

### `small`

适合本地开发、功能验证、演示环境。

```bash
docker compose -f docker-compose-small.yml up -d
docker compose -f docker-compose-small.yml ps
```

默认启动：

- PostgreSQL
- Redis
- Nacos
- gateway
- auth
- system
- job

### `medium`

适合联调环境、小团队测试环境。

```bash
docker compose -f docker-compose.yml up -d
docker compose -f docker-compose.yml ps
```

在 `small` 基础上增加：

- RustFS
- RabbitMQ
- tenant
- workflow
- open
- file

说明：

- 代码生成器 `han-gen` 当前保留为可选能力，建议单独启用和验收，不强绑在默认 compose 主链路里。

### `full`

适合完整业务联调和生产预演环境。

```bash
docker compose -f docker-compose-full.yml up -d
docker compose -f docker-compose-full.yml ps
```

在 `medium` 基础上增加：

- ai

建议按环境外挂而不是默认强绑：

- Kafka
- Elasticsearch
- Prometheus / Grafana
- ELK

## 5. 验证步骤

### 服务自检

```bash
curl http://localhost:9090/auth/captcha
curl http://localhost:9090/system/runtime/capabilities
```

### 重点核验

- `tier` 是否符合当前 compose
- `enabledModules` 是否覆盖当前层级默认服务
- `optionalServices.rabbitmq` / `optionalServices.rustfs` 是否和环境一致
- 登录页是否按层级正确显示租户选择
- 侧边栏是否按层级过滤非当前能力页面

## 6. 默认端口

| 服务 | 端口 | 说明 |
|------|------|------|
| gateway | `9090 -> 8080` | 网关外部访问入口 |
| auth | `9200` | 认证服务 |
| system | `9201` | 系统服务 |
| tenant | `9202` | 租户服务 |
| workflow | `9203` | 工作流服务 |
| job | `9204` | 调度服务 |
| open | `9205` | 开放平台 |
| file | `9207` | 文件服务 |
| ai | `9208` | AI 服务 |
| postgres | `5432` | 数据库 |
| redis | `6379` | 缓存 |
| nacos | `8848` | 注册与配置中心 |
| rustfs | `9000/9001` | 对象存储 API / 控制台 |
| rabbitmq | `5672/15672` | AMQP / 管理台 |

## 7. 常见问题

### 运行时能力和菜单不一致

优先检查：

- `HAN_DEPLOY_TIER` 是否在所有服务中一致
- 网关是否已经放行 `/system/runtime/capabilities`
- 前端是否成功拉到了运行时能力接口

### Maven 构建失败，提示本地仓库不可写

优先使用仓库根目录的 `settings.workspace.xml`，不要依赖机器全局 Maven 配置里的固定仓库目录。
