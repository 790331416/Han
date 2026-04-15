# Han Cloud 测试计划

## 1. 目标

本轮测试的目标不是单点接口通过，而是验证三档部署和文档口径是否一致：

- `small` 是否只暴露核心系统能力
- `medium` 是否补齐多租户、工作流、开放平台、文件能力
- `full` 是否在 `medium` 基础上补齐 AI 能力，并对可选中间件做正确探测

参考矩阵：[capability-matrix.md](./capability-matrix.md)

## 2. 通用前置

### 后端编译

```powershell
$env:JAVA_HOME='C:\Program Files\Java\latest\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS=''
mvn -gs settings.workspace.xml -DskipTests compile
```

### 前端检查

```bash
cd han-ui
pnpm install
pnpm exec vue-tsc --noEmit
pnpm exec eslint .
```

## 3. 分层冒烟

### `small`

启动：

```bash
docker compose -f docker-compose-small.yml up -d
```

验证：

- `/auth/captcha` 正常返回
- `/system/runtime/capabilities` 返回 `tier=small`
- `enabledModules` 包含 `gateway/auth/system/job`
- 登录页不显示租户选择
- 侧边栏不显示工作流、开放平台、AI 等中高阶入口

### `medium`

启动：

```bash
docker compose -f docker-compose.yml up -d
```

验证：

- `/system/runtime/capabilities` 返回 `tier=medium`
- `enabledModules` 包含 `tenant/workflow/open/file`
- `optionalServices.rustfs=true`
- `optionalServices.rabbitmq=true`
- 租户列表、工作流、开放平台、文件页可访问

### `full`

启动：

```bash
docker compose -f docker-compose-full.yml up -d
```

验证：

- `/system/runtime/capabilities` 返回 `tier=full`
- `enabledModules` 包含 `ai`
- AI 页面入口可见
- Kafka / Elasticsearch 未接入时，前端不应把它们误判为已启用

## 4. 安全校验

- 外部请求不能伪造 `X-Inner-*` 和 `X-User-*` 头穿透网关
- `@InnerAuth` 接口缺少签名时应拒绝访问
- 登录锁定键按 `tenantId + username` 生效
- 数据权限接口不再返回空壳默认值

## 5. 核心回归

- 登录、刷新、退出
- 用户中心
- 公告通知
  - 铃铛最新通知列表
  - 未读数按用户变化
  - 单条已读与全部已读
  - SSE 推送可用，失败时可降级轮询
- 参数配置
- 调度任务
- 运行时能力接口

## 6. 后续补测建议

以下能力建议在下一轮继续补自动化：

- 菜单路由与权限契约
- OSS 配置闭环
- Open App 持久化实现
- AI 后端接口联调
