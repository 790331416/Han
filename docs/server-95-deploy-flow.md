# 95 服务器部署验证流程

## 1. 目标

固定使用 `10.18.35.95` 作为 Han Cloud 的远程 Docker 验证环境。

该环境的默认原则如下：

- 先在服务器安装 `git`
- 本地代码先推送到 `origin`
- 服务器再执行 `pull`
- Docker 部署优先复用 `xzy0112` 仓库已推送镜像，或服务器本地已有镜像
- 不额外去其他地方找镜像

## 2. 已确认环境

- 服务器地址：`10.18.35.95`
- 系统：`CentOS Linux 7`
- Git：已安装，版本 `1.8.3.1`
- 服务器代码目录：`/opt/han/source/Han`
- 服务器部署目录：`/opt/han/docker`
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

## 4. 后续固定流程

### 4.1 本地推送

在本地工作区 `D:\code\Han` 完成改动后，按需要提交并推送到 `origin`。

```bash
git status
git add <files>
git commit -m "<message>"
git push origin master
```

说明：

- 如果当前工作区包含未确认的混合改动，不直接推送
- 先确认要发布的改动范围，再提交和推送
- 如需隔离发布，可使用 `codex/<topic>` 分支

### 4.2 服务器拉代码

```bash
cd /opt/han/source/Han
git pull --ff-only origin master
```

### 4.3 远程 Docker 部署

优先使用 `/opt/han/docker` 下的编排文件。

```bash
cd /opt/han/docker
docker compose up -d
docker compose ps
```

## 5. 镜像使用原则

- 优先使用 `registry.cn-hangzhou.aliyuncs.com/xzy0112/*`
- 如果服务器本地已有对应镜像，优先复用本地镜像
- 不额外搜索其他镜像源

## 6. 基础验收

部署后至少验证以下内容：

```bash
curl http://127.0.0.1:9090/auth/captcha
curl http://127.0.0.1:9090/auth/login
curl http://127.0.0.1:9090/system/user/current
curl http://127.0.0.1:9090/system/menu/routers
docker compose ps
docker ps
```

## 7. 当前已知事项

- `95` 服务器当前已经完成 Git 安装
- `95` 服务器当前已经完成仓库克隆
- 当前远程 Docker 镜像版本落后于本地整改代码
- 后续如果要验证本地最新整改结果，需要先推送代码，必要时同步推送最新镜像
