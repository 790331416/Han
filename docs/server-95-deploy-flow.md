# 95 服务器部署验证流程

## 1. 目标

固定使用 `10.18.35.95` 作为 Han Cloud 的远程 Docker 验证环境。

本流程是强约束，后续默认按这条链路执行：

- 本地先提交并推送代码
- `95` 服务器拉取最新代码
- `95` 服务器自行完成打包、构镜像、Docker 部署验证
- 优先复用 `xzy0112` 仓库镜像或服务器本地已有镜像
- 不走“本地打包后手工传 jar”这种旁路

## 2. 环境信息

- 服务器地址：`10.18.35.95`
- 系统：`CentOS Linux 7`
- Git：`1.8.3.1`
- 源码目录建议：`/opt/han/source/Han`
- 现有联调目录：`/opt/han/source/Han-ui-validate-20260323`
- Docker 部署目录：`/opt/han/docker`
- 仓库地址：`https://gitee.com/xzy0112/Han.git`

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

## 5. 镜像使用原则

- 优先使用 `registry.cn-hangzhou.aliyuncs.com/xzy0112/*`
- 服务器本地已有镜像时优先复用
- 缺镜像时允许重新拉取，不去额外寻找第三方镜像源

## 6. 基础验收

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

## 7. 强约束提醒

- 先推代码，再让 `95` 拉代码
- `95` 自己打包、自构镜像、自验收
- 不走本地传 `jar` 的旁路
- 文档与实际流程必须保持一致
