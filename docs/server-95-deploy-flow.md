# 95 服务器部署验证流程

## 1. 目标

固定使用 `10.18.35.95` 作为 Han Cloud 的远程 Docker 验证环境。

本流程是强约束，后续默认按这条链路执行：

- 本地先提交并推送代码
- `95` 服务器拉取最新代码
- `95` 服务器自行完成打包、构镜像、Docker 部署验证
- 优先复用 `xzy0112` 仓库镜像或服务器本地已有镜像
- 不走“本地打包后手工传 jar”这种旁路

如果 `95` 环境已经出现服务漂移、代理头异常、数据库缺表或容器启动顺序问题，先看恢复清单：

- [environment-recovery-checklist-20260402.md](/D:/code/Han/docs/environment-recovery-checklist-20260402.md)

## 2. 环境信息

- 服务器地址：`10.18.35.95`
- 系统：`CentOS Linux 7`
- Git：`1.8.3.1`
- 源码目录建议：`/opt/han/source/Han`
- 现有联调目录：`/opt/han/source/Han-ui-validate-20260323`
- Docker 部署目录：`/opt/han/docker`
- 仓库地址：`https://gitee.com/xzy0112/Han.git`

三档默认入口：

- `small`: UI `3100`, gateway `19090`
- `medium`: UI `3200`, gateway `29090`
- `full`: UI `3000`, gateway `9090`

如需做 UI canary：

- `small` 建议临时使用 `3101`
- `medium` 建议临时使用 `3201`
- `full` 建议临时使用 `3001`

## 3. 首次准备

### 3.1 安装 Git

```bash
yum install -y git
git --version
```

### 3.2 克隆代码

```bash
mkdir -p /opt/han/source
git clone https://gitee.com/xzy0112/Han.git /opt/han/source/Han
```

### 3.3 验证远端可读

```bash
git ls-remote https://gitee.com/xzy0112/Han.git HEAD
cd /opt/han/source/Han
git pull --ff-only origin master
```

## 4. 标准发布链路

### 4.1 本地提交并推送

在本地工作区 [D:\code\Han](/D:/code/Han) 完成改动后：

```bash
git status
git add <files>
git commit -m "<message>"
git push origin <branch>
```

说明：

- 工作区存在混合改动时，只提交当前发布范围
- 如需隔离发布，使用 `codex/<topic>` 分支
- 未推送前，不进入 `95` 打包步骤

### 4.2 95 服务器拉代码

```bash
cd /opt/han/source/Han
git fetch origin
git checkout <branch>
git pull --ff-only origin <branch>
```

如果服务器上用于验证的是单独目录，也按同样方式拉取，不直接覆盖脏工作树。

### 4.3 95 服务器自行打包

`95` 宿主机没有固定安装 JDK 和 Maven 时，统一使用容器化 Maven：

```bash
cd /opt/han/source/Han
docker run --rm \
  -v "$PWD:/workspace" \
  -w /workspace \
  -v "$PWD/.m2/repository:/root/.m2/repository" \
  maven:3.9.9-eclipse-temurin-21 \
  mvn -s settings.workspace.xml -Dmaven.repo.local=/root/.m2/repository -DskipTests package
```

说明：

- `settings.workspace.xml` 统一把本地 Maven 仓库落到仓库内 `.m2/repository`
- 所有产物必须由 `95` 本机打出
- 不从本地手工上传 `jar` 到服务器替代打包

### 4.4 95 服务器构建镜像

```bash
cd /opt/han/source/Han
docker build -t registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest -f han-modules/han-ai/Dockerfile han-modules/han-ai
```

其它服务按相同原则构建。

### 4.5 95 服务器 Docker 验证

优先使用部署目录或当前验证目录中的 compose 文件：

```bash
cd /opt/han/source/Han
docker compose -f docker-compose-full.yml up -d
docker compose -f docker-compose-full.yml ps
```

如果是 `AI` 联调，需要通过环境变量注入供应商密钥：

```bash
export DASHSCOPE_API_KEY=<server-env-only>
docker compose -f docker-compose-full.yml up -d ai
```

如果是 `95` 宿主机上的正式部署目录，优先使用 `/opt/han/docker` 下的 compose 和持久化环境文件，不要只在临时 shell 里 `export`：

```bash
cd /opt/han/docker
docker compose -p hanfull -f /opt/han/docker/docker-compose-full.yml config
docker compose -p hanfull -f /opt/han/docker/docker-compose-full.yml up -d ai
```

为避免部分 Docker 环境下的 JVM DNS 抖动影响 DashScope 真实调用，`han-ai` 建议显式带上以下 JVM 参数：

```bash
export DASHSCOPE_API_KEY=<server-env-only>
export JAVA_OPTS="-Xms256m -Xmx512m -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false -Dnetworkaddress.cache.ttl=60 -Dnetworkaddress.cache.negative.ttl=0"
docker compose -f docker-compose-full.yml up -d ai
```

更稳的口径是把 AI provider key 固化在 `/opt/han/docker/.env` 这类宿主机持久化来源里，再执行 `compose up -d ai`。否则 shell 退出后，下一次重建 `han-ai` 仍可能回到“未配置”。

## 5. 95 恢复顺序

如果 `95` 已经不是“标准发版”，而是进入恢复态，优先按下面顺序收口，不要一上来就只重启单个前端容器：

1. `postgres`
2. `redis`
3. `nacos`
4. `gateway`
5. 业务服务：`auth/system/job/file/open/tenant/workflow/ai/gen`
6. `ui`

经验口径：

- `medium/full` 如出现 `502`、登录回跳或 OSS 上传 `503`，先看 `redis/nacos/gateway` 是否真的恢复
- `small` 如出现登录失败或通知中心 `500`，除了服务状态，还要回查数据库是否已补齐登录日志和通知中心表结构
- `full` 如模型页再次显示“未配置”，先查 `/opt/han/docker/.env` 与 `han-ai` 容器内环境变量，再决定是否重启 `ai`

## 6. UI Canary 切换原则

这轮在 `small/medium` 都踩到过“登录成功但首页弹资源不存在”的问题，根因不是登录态，而是旧 UI bundle 仍请求 `/system/dashboard/charts`。因此 `95` 上切 UI 建议统一走 canary：

1. 保留旧 UI 容器作为回滚位
2. 用新镜像先起 canary 端口
3. 用 Playwright 或最小登录回归确认 dashboard 不再弹错
4. 再切正式端口

推荐口径：

- `small`: 旧 `3100` 保留回滚位，新镜像先起 `3101`
- `medium`: 旧 `3200` 保留回滚位，新镜像先起 `3201`
- canary 验证通过后，再替换正式端口

如果你只看到“资源不存在”这种前端 toast，不要直接认为是后端功能缺失；先确认在线 UI 镜像是否仍是旧 bundle。

## 7. 镜像使用原则

- 优先使用 `registry.cn-hangzhou.aliyuncs.com/xzy0112/*`
- 服务器本地已有镜像时优先复用
- 缺镜像时允许重新拉取，不去额外寻找第三方镜像源

## 8. 基础验收

部署后至少验证以下内容：

```bash
curl http://127.0.0.1:9090/auth/captcha
curl http://127.0.0.1:9090/system/runtime/capabilities
docker compose ps
docker ps
```

AI 相关改动额外验证：

```bash
curl http://127.0.0.1:9090/ai/model/all?modelType=LLM
curl http://127.0.0.1:9090/ai/chat/conversations
```

如处于恢复态，建议额外补三类最小验证：

```bash
curl http://127.0.0.1:19090/system/runtime/capabilities
curl http://127.0.0.1:29090/system/runtime/capabilities
curl http://127.0.0.1:9090/system/runtime/capabilities
```

- `small/medium/full` 的 `tier` 是否分别正确
- UI 正式端口登录后 dashboard 是否正常，不弹“资源不存在”
- `full` 的 AI 模型页是否显示“已配置”

## 9. 强约束提醒

- 先推代码，再让 `95` 拉代码
- `95` 自己打包、自构镜像、自验收
- 不走本地传 `jar` 的旁路
- 文档与实际流程必须保持一致
