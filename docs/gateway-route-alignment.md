# Gateway 路由对齐说明

## 1. 背景

Han Cloud 当前网关基于 `Spring Cloud Gateway Server WebFlux 5.0.0`。
该版本的静态路由配置前缀已经切换为：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
```

旧前缀：

```yaml
spring:
  cloud:
    gateway:
      routes:
```

在当前版本中不会生效。

## 2. 当前约束

- 网关路由以仓库内配置文件为准：
  - `han-gateway/src/main/resources/application.yml`
  - `han-gateway/src/main/resources/application-docker.yml`
- `95` 环境验证结果表明，历史遗留的 Nacos `han-gateway.yml` 仍可能保留旧前缀配置。
- 为避免远端旧配置覆盖当前路由树，网关当前不依赖 Nacos 配置中心提供路由定义。
- Docker 环境中的网关必须使用最新源码重新构建镜像，不能复用旧的通用模板 `application-docker.yml`。

## 3. 当前路由范围

`medium` 档默认要求以下路由可用：

- `/auth/** -> han-auth`
- `/system/** -> han-system`
- `/tenant/** -> han-tenant`
- `/job/** -> han-job`
- `/open/**`, `/oauth2/**`, `/sso/** -> han-open`
- `/file/** -> han-file`
- `/workflow/** -> han-workflow`

`full` 档在此基础上增加：

- `/ai/** -> han-ai`

## 4. 95 环境验收命令

### 4.1 基础路由

```bash
curl http://127.0.0.1:9090/auth/captcha
curl http://127.0.0.1:9090/system/runtime/capabilities
```

### 4.2 登录后访问开放平台

```bash
curl -H "Authorization: Bearer <token>" "http://127.0.0.1:9090/open/app/list?pageNum=1&pageSize=1"
```

### 4.3 文件上传与公开下载

```bash
curl -H "Authorization: Bearer <token>" -F "file=@./smoke.txt" http://127.0.0.1:9090/file/upload
curl http://127.0.0.1:9090/file/public/<locator>/<fileName>
```

## 5. 已验证结论

`2026-03-24` 在 `10.18.35.95` 上完成以下真实验收：

- `9090/auth/captcha` 返回 `200`
- `9090/system/runtime/capabilities` 返回 `200`
- 登录后访问 `9090/open/app/list` 返回 `200`
- 通过 `9090/file/upload` 上传成功
- 通过 `9090/file/public/...` 匿名下载成功

说明网关入口已经恢复为可用状态，后续部署验证应优先通过 `9090` 统一入口进行。
