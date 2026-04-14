# 2026-03-24 三档部署系统化验收报告

## 验收范围

- 验收日期：2026-03-24
- 验收环境：`10.18.35.95`
- 验收目标：核对 `small / medium / full` 三档部署与文档能力矩阵的一致性，并验证本轮整改是否真实生效
- 验收方式：
  - `95` 服务器 Docker 容器与健康状态检查
  - 网关与业务接口真实回归
  - 隔离 PostgreSQL 首次初始化验证
  - Playwright 直连 `95` 前后端做页面回归

## 最新结论

- `small`：基础链路通过，`fresh deploy` 所需的基线菜单初始化已在隔离 PostgreSQL 环境验证通过。
- `medium`：主体链路通过，`workflow` 已在 `95` 服务器通过最新源码构建镜像并成功部署，运行时能力、网关接口和前端侧边栏均已对齐。
- `full`：截至 2026-03-24 仍无法在 `95` 完整拉起，当前核心阻塞是 `han-ai:latest` 发布物缺失。

## Small

### 已验证通过

- `gateway / auth / system / job` 可启动并通过接口回归
- `/system/runtime/capabilities` 返回 `200`
- `/auth/login` 返回 `200`
- `/system/user/current` 返回 `200`
- `/job/list?pageNum=1&pageSize=10` 返回 `200`
- `/job/log/list?pageNum=1&pageSize=10` 返回 `200`

### Fresh Deploy 初始化验证

- 使用隔离 PostgreSQL 容器挂载 [init.sql](D:/code/Han/sql/postgres/init.sql) 和 [init-base-data.sql](D:/code/Han/sql/postgres/init-base-data.sql) 完成首次初始化验证。
- 初始化后结果：
  - `sys_menu = 37`
  - `sys_role_menu(role_id=1) = 37`
  - 顶级菜单路径包含 `system, job, workflow, open, ai`

### 当前判断

- 从源码与初始化脚本角度，`small` 的基础可用性已经满足。
- 后续若要重跑一次完整 `small fresh deploy`，建议继续使用最新源码目录中的 Compose 与 SQL，而不是复用历史部署残留目录。

## Medium

### 已验证通过

- `95` 当前健康服务包括：
  - `gateway`
  - `auth`
  - `system`
  - `tenant`
  - `workflow`
  - `job`
  - `open`
  - `file`
  - `rabbitmq`
  - `postgres`
  - `redis`
  - `nacos`
- 关键接口返回正常：
  - `/system/runtime/capabilities`
  - `/auth/login`
  - `/system/user/current`
  - `/tenant/list?pageNum=1&pageSize=5`
  - `/open/app/list?pageNum=1&pageSize=5`
  - `/system/oss/config/list?pageNum=1&pageSize=5`
  - `/system/notice/list?pageNum=1&pageSize=5`
  - `/system/notice/unreadCount`
  - `/system/notice/latest?limit=5`
  - `/system/notice/sse?token=...`
  - `/workflow/definition/list?pageNum=1&pageSize=10`

### 本轮新增收口结果

- [RuntimeCapabilityController.java](D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java) 已改为基于服务发现判断真实启用模块。
- `95` 当前返回的运行时能力为：
  - `tier=medium`
  - `enabledModules=[gateway, auth, system, user-center, notice, config, monitor, job, tenant, workflow, open, file]`
  - `featureFlags.workflow=true`
  - `featureFlags.ai=false`
- [application-docker.yml](D:/code/Han/han-modules/han-workflow/src/main/resources/application-docker.yml) 已补齐 `DB / Redis / Nacos` 与 `Flowable schema` 的 Docker 运行时配置。
- `han-workflow` 容器在 `95` 上已达到 `healthy`，`/actuator/health` 返回 `UP`。
- Flowable 首次建表已完成，`act_%` 表数量为 `56`。
- 带登录态访问 `/workflow/definition/list?pageNum=1&pageSize=10` 返回 `200`，当前为 `0` 条流程定义，符合“服务已通但尚未导入流程模型”的状态。

### 前端回归结果

- Playwright 关键用例通过：
  - `auth-login.spec.ts`
  - `tenant-pages.spec.ts`
  - `oss-config.spec.ts`
  - `open-app.spec.ts`
  - `notice-center.spec.ts`
  - `runtime-sidebar.spec.ts`
- [runtime-sidebar.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts) 已同时验证两种联调口径：
  - 本地最新 `han-ui` 直连 `95` 网关
  - 远端 `95:3000` 页面直连 `95:9090` 网关
- 当前页面结果为：
  - `job` 菜单可见
  - `open` 菜单可见
  - `tenant`、`tenant-package`、`tenant-quota`、`oss-config` 入口可见
  - `workflow` 菜单可见
  - `ai` 菜单隐藏

### 当前残留风险

1. `/system/menu/routers` 当前顶级菜单仍主要返回 `System / Tenant / Job`，`workflow` 页面入口仍依赖前端静态路由与运行时能力过滤。
2. 这不影响当前 `medium` 环境可用性，但与“后端菜单作为业务路由唯一事实来源”的目标仍有差距，后续需要继续收口。
3. `han-workflow:latest` 本轮已在 `95` 通过本地构建补齐，是否同步发布到远端镜像仓库仍需单独确认。

## Full

### 当前阻塞项

1. `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest`
   - 2026-03-24 在 `95` 上拉取结果仍为 `manifest unknown`

### 当前判断

- `full` 目前无法在 `95` 做真实完整拉起。
- 在 `han-ai:latest` 未补齐前，不建议继续做 `full` 页面与接口验收。

## 整改状态汇总

### 已从“失败/阻塞”转为“通过/已收口”

- `small` fresh deploy 基线菜单缺失
- `medium` 通知中心 `unreadCount/latest/sse` 被详情路由误吞
- `medium` 运行时能力误报 `workflow` 已启用
- `medium` 前端侧边栏未按运行时能力过滤入口
- `medium` `workflow` Docker 运行时配置缺失，导致 Redis 指向 `localhost`
- `medium` `workflow` 服务未注册、网关接口不可用

### 仍需继续推进

1. 发布并部署 `han-ai:latest`
2. 在 `han-ai` 发布物补齐后重跑 `full` 验收
3. 继续推进 `workflow/open/ai` 菜单与后端动态路由来源统一

## 建议下一步

1. 先补 `han-ai:latest` 镜像并在 `95` 拉起服务
2. 重跑 `full` 验收，重点验证：
   - `/ai/**` 接口
   - `full` 运行时能力
   - AI 菜单与页面入口
3. 然后继续收口“后端菜单作为业务路由唯一事实来源”的架构目标
