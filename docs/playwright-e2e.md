# Han UI Playwright 自动化测试说明

## 1. 目标

本说明用于统一 `han-ui` 的端到端测试执行方式，确保以下要求有明确落点：

- 前端改动至少执行 `pnpm build` 与 `pnpm lint`
- 涉及登录、菜单、通知、租户、开放平台等关键流程时，优先补 Playwright 端到端回归
- 本地开发与 `95` 服务器联调共用一套脚手架，不再重复维护多份测试入口

## 2. 目录结构

```text
han-ui/
  playwright.config.ts
  .env.e2e.example
  tests/e2e/
    fixtures/
    specs/
    utils/
  output/playwright/
```

说明：

- `fixtures/`：统一登录态、通用页面夹具
- `specs/`：业务用例
- `utils/`：API 预置、测试辅助方法
- `output/playwright/`：报告、视频、截图、trace 等产物

## 3. 执行命令

在 `han-ui` 目录下执行：

```bash
pnpm lint
pnpm build
pnpm test:e2e
```

常用命令：

```bash
pnpm test:e2e
pnpm test:e2e --project=chromium
pnpm test:e2e:headed
pnpm test:e2e:ui
pnpm test:e2e:report
```

## 4. 环境变量

复制 `han-ui/.env.e2e.example` 后按需覆盖：

```bash
PW_BASE_URL=http://10.18.35.95:3000
PW_API_URL=http://10.18.35.95:9090
PW_USERNAME=admin
PW_PASSWORD=admin123
PW_TENANT_ID=
PW_WORKERS=1
PW_RETRIES=1
PW_TRACE_MODE=on-first-retry
PW_STRUCTURED_WORKFLOW_ID=
PW_STRUCTURED_SOURCE_TEXT=
PW_STRUCTURED_TOOL_NAME=
PW_STRUCTURED_SERVER_NAME=
```

说明：

- 未设置 `PW_BASE_URL` 时，Playwright 会自动拉起本地 `vite` 开发服务
- `PW_API_URL` 默认对接 `95` 网关，便于本地前端联调远端后端
- `PW_WORKERS` 默认为 `1`，避免共享测试账号在旧环境中发生并发会话踩踏

## 5. 当前已接入用例

- `auth-login.spec.ts`
  - 登录页基础控件可见
  - 通过 API 登录态注入后进入首页
- `auth-logout.spec.ts`
  - 退出登录后返回登录页
  - 退出后再次访问首页会被拦回登录页
- `notice-center.spec.ts`
  - 通知列表加载
  - 单条已读
  - 全部已读

## 6. 95 环境联调说明

推荐两种方式：

1. 本地前端 + `95` 后端

```bash
cd han-ui
pnpm test:e2e
```

默认配置下会启动本地前端，并把接口打到 `95` 网关。

2. `95` 已部署前端 + `95` 后端

```bash
set PW_BASE_URL=http://10.18.35.95:3000
set PW_API_URL=http://10.18.35.95:9090
set PW_RETRIES=1
pnpm test:e2e
```

```bash
set PW_BASE_URL=http://10.18.35.95:3000
set PW_API_URL=http://10.18.35.95:9090
set PW_RETRIES=1
set PW_STRUCTURED_WORKFLOW_ID=2
set PW_STRUCTURED_SOURCE_TEXT=Han AI structured validation knowledge paragraph
set PW_STRUCTURED_TOOL_NAME=structured_lookup
set PW_STRUCTURED_SERVER_NAME=Codex Structured MCP 177492
npx playwright test tests/e2e/specs/ai-chat-structured-meta.spec.ts --project=chromium
```

## 7. 已知约束

- 当前 `95` 服务器上的通知中心后端镜像仍落后于本地代码，`/system/notice/markAllRead` 与本地契约不完全一致。
- 因此在 `95` 旧镜像环境中，`notice-center.spec.ts` 可能暴露真实契约差异，而不是脚手架本身故障。
- 若要让通知中心用例完全通过，需要先把最新 `gateway/auth/system/ui` 镜像部署到 `95` 环境。

## 8. 提测要求

涉及以下改动时，提交前应至少附带一项验证结果：

- 登录、退出登录、菜单、用户中心
- 公告通知、SSE、未读数
- 租户切换、开放平台、文件上传
- 路由守卫、权限、运行时能力开关

推荐附带内容：

- 执行命令
- 通过的用例列表
- 未通过项与阻塞原因
- 是否依赖 `95` 旧镜像或本地环境差异
