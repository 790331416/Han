# Han Cloud 部署指南

## 1. 基线说明

部署前统一两组关键变量：

- `HAN_DEPLOY_TIER=small|medium|full`
- `HAN_INNER_AUTH_SECRET=<same-secret-for-all-services>`

能力矩阵请先看 [capability-matrix.md](/D:/code/Han/docs/capability-matrix.md)。

如果走 `95` 服务器验证，必须额外遵守 [server-95-deploy-flow.md](/D:/code/Han/docs/server-95-deploy-flow.md) 中的标准流程。

如果遇到 `95` 上的登录、网关代理、OSS 外链、AI 凭证、Nacos/Redis/Postgres 启动顺序等恢复问题，直接参考：

- [environment-recovery-checklist-20260402.md](/D:/code/Han/docs/environment-recovery-checklist-20260402.md)

## 2. 环境要求

| 项目 | 建议版本 |
|------|----------|
| JDK | 21 |
| Maven | 3.9+ |
| Docker | 26+ |
| Docker Compose | 2.x |
| Node.js | 18+ |

## 3. Maven 构建建议

为避免全局 `settings.xml` 中写死本地仓库路径，统一使用仓库内的 [settings.workspace.xml](/D:/code/Han/settings.workspace.xml)。

本地构建：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS=''
mvn -gs settings.workspace.xml -DskipTests compile
```

服务器未安装 JDK/Maven 时，统一使用容器化 Maven：

```bash
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  -v "$PWD/.m2/repository:/root/.m2/repository" \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -s settings.workspace.xml -Dmaven.repo.local=/root/.m2/repository -DskipTests package
```

前端 `han-ui` 自 `2026-04-03` 起也统一改成“源码内建”镜像，不再依赖仓库里遗留的 `dist`：
```bash
docker build -t han-ui:<tag> han-ui
```

说明：
- `han-ui/Dockerfile` 会先在构建阶段执行 `npm install --legacy-peer-deps --no-audit --no-fund`
- 然后执行 `npm run build`
- 最终运行层只复制新产出的 `/app/dist`
- 这条链路专门用于避免“代码已更新，但前端仍跑旧 bundle”的假发布

## 4. 三档部署

### 4.1 `small`

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

### 4.2 `medium`

适合联调环境和小团队测试环境。

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

### 4.3 `full`

适合完整业务联调和生产预演环境。

```bash
docker compose -f docker-compose-full.yml up -d
docker compose -f docker-compose-full.yml ps
```

在 `medium` 基础上增加：

- ai

三档默认验证端口建议固定为：

- `small`: UI `3100`, gateway `19090`
- `medium`: UI `3200`, gateway `29090`
- `full`: UI `3000`, gateway `9090`

建议按环境外挂而不是默认强绑：

- Kafka
- Elasticsearch
- Prometheus / Grafana
- ELK

## 5. AI 服务说明

`full` 档的 `han-ai` 支持通过环境变量注入模型密钥，不要求把明文密钥写入仓库或数据库。

示例：

```bash
export DASHSCOPE_API_KEY=<server-env-only>
docker compose -f docker-compose-full.yml up -d ai
```

若部署环境中的 Docker DNS 对 JVM 解析存在抖动，建议同步给 `han-ai` 增加如下 JVM 参数，以稳定 DashScope 等外部模型供应商调用：

```bash
export DASHSCOPE_API_KEY=<server-env-only>
export JAVA_OPTS="-Xms256m -Xmx512m -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -Dnetworkaddress.cache.ttl=60 -Dnetworkaddress.cache.negative.ttl=0"
docker compose -f docker-compose-full.yml up -d ai
```

如果是 `95` 这类长期验证环境，建议把 provider key 放入宿主机持久化环境文件，而不是只在临时 shell 中 `export`。这轮真实恢复口径是：

- 宿主机环境文件：`/opt/han/docker/.env`
- 重建前先核对容器外变量来源
- 重建后再核对 `han-ai` 容器内环境变量

否则下一次 `docker compose ... up -d ai` 后，模型页可能再次回到“未配置”。

## 6. 恢复与切换建议

如果环境不是首次部署，而是已经出现服务漂移、代理异常、数据库缺表或前端旧 bundle 混跑，建议按下面顺序恢复：

1. `postgres`
2. `redis`
3. `nacos`
4. `gateway`
5. 业务服务：`auth/system/job/file/open/tenant/workflow/ai/gen`
6. `ui`

额外建议：

- `small/medium` 的 UI 切换优先做 canary，不直接覆盖正式端口
- `small` 建议 canary 端口 `3101`
- `medium` 建议 canary 端口 `3201`
- dashboard 出现“资源不存在”时，先确认在线 UI 镜像是否还是旧 bundle，再判断是不是后端接口问题
- `full` 的 OSS 外链异常时，优先回查 `gateway trusted-proxies`
- `full` 的 AI 模型页显示“未配置”时，优先回查宿主机 provider key 持久化来源

## 7. 验证步骤

### 7.1 服务自检

```bash
curl http://localhost:9090/auth/captcha
curl http://localhost:9090/system/runtime/capabilities
```

### 7.2 重点核验

- `tier` 是否符合当前 compose
- `enabledModules` 是否覆盖当前层级默认服务
- 登录页是否按层级正确显示租户选择
- 菜单是否按层级和运行时能力一起过滤
- `AI` 场景下，模型测试和聊天回复是否是真实供应商返回

如是恢复后的二次验收，建议再补查：

- dashboard 登录后是否还会弹“资源不存在”
- `small/medium/full` 的 `tier` 是否分别与实际入口一致
- `full` 的 AI 模型页是否显示“已配置”

## 8. 默认端口

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

## 9. 常见问题

### 9.1 运行时能力和菜单不一致

优先检查：

- `HAN_DEPLOY_TIER` 是否在所有服务中一致
- `/system/runtime/capabilities` 是否可用
- 前端是否成功拉取运行时能力接口

### 9.2 Maven 构建失败，提示本地仓库路径不可写

优先改用 [settings.workspace.xml](/D:/code/Han/settings.workspace.xml)，不要依赖机器全局 Maven 配置中的固定仓库目录。

### 9.3 95 服务器验证流程跑偏

统一回到 [server-95-deploy-flow.md](/D:/code/Han/docs/server-95-deploy-flow.md)：

- 本地先推代码
- `95` 服务器拉代码
- `95` 服务器自己打包和构镜像
- 不走传 `jar` 旁路

### 9.4 登录后首页提示“资源不存在”

优先排查顺序：

1. 当前在线 UI 镜像是否还是旧 bundle
2. 是否应先用 canary 端口验证新 UI
3. 再判断是不是 dashboard 后端接口确实不存在

这轮在 `95 small` 和 `95 medium` 的真实根因，都是旧 UI bundle 仍会请求 `/system/dashboard/charts`，不是密码错、不是用户信息错。

### 9.5 95 small 登录恢复了但通知中心仍报错

优先补查：

- `sys_login_log` 是否仍停留在旧列名 `ipaddr/msg`
- `sys_notice_read` 是否已存在

如果这两项没补齐，small 往往会出现“登录恢复了，但通知链路仍然 500”的半恢复状态。
