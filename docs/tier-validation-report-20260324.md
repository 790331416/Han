# 2026-03-24 三档部署系统化验收报告

## 验收范围

- 验收日期：2026-03-24
- 验收环境：`10.18.35.95`
- 验收目标：核对 `small / medium / full` 三档部署与文档能力矩阵的一致性，并验证当前整改项是否真实生效
- 验收方式：
  - `95` 服务器 Docker 容器与运行状态检查
  - 网关与业务接口实测
  - 隔离 PostgreSQL 初始化验证
  - 本地 Playwright 直连 `95` 后端做页面回归

## 最新结论

- `small`：核心链路可用，基线菜单初始化已在隔离 PostgreSQL 中验证通过
- `medium`：主体链路通过，通知中心与运行时能力口径已收口；当前剩余阻塞集中在 `workflow` 发布物缺失
- `full`：仍然无法在 `95` 实际拉起，根因是 `han-workflow:latest` 与 `han-ai:latest` 镜像缺失

## Small

### 已验证通过

- `gateway / auth / system / job` 可启动并通过接口回归
- `/system/runtime/capabilities` 返回 `200`
- `/auth/login` 返回 `200`
- `/system/user/current` 返回 `200`
- `/job/list?pageNum=1&pageSize=10` 返回 `200`
- `/job/log/list?pageNum=1&pageSize=10` 返回 `200`

### fresh deploy 初始化验证

- 使用隔离 PostgreSQL 容器挂载 [init.sql](D:/code/Han/sql/postgres/init.sql) 与 [init-base-data.sql](D:/code/Han/sql/postgres/init-base-data.sql) 做首次初始化验证
- 初始化后：
  - `sys_menu = 37`
  - `sys_role_menu(role_id=1) = 37`
  - 顶级菜单路径为 `system,job,workflow,open,ai`
- 说明当前源码中的 PostgreSQL 初始化脚本已经能够为 fresh deploy 提供基础菜单数据，不再出现 `sys_menu=0` 的问题

### 当前判断

- 从源码与初始化脚本角度，`small` 的基础可用性已经满足
- 如果后续要做一次完整的 `small` fresh deploy 验收，建议直接使用最新源码目录中的 Compose 与 SQL，而不是复用历史残留部署目录

## Medium

### 已验证通过

- `95` 当前健康服务包括：
  - `gateway`
  - `auth`
  - `system`
  - `tenant`
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

### 本轮新增收口结果

- [RuntimeCapabilityController.java](D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java) 已改为基于服务发现判断真实启用模块
- `95` 当前返回的运行时能力为：
  - `tier=medium`
  - `enabledModules=[gateway,auth,system,user-center,notice,config,monitor,job,tenant,open,file]`
  - `featureFlags.workflow=false`
  - `featureFlags.ai=false`
- 说明当前 `medium` 环境不会再误报 `workflow` 已启用
- 通知中心路由冲突已修复，`unreadCount/latest/sse` 均恢复正常

### 前端回归结果

- Playwright 关键用例通过：
  - `auth-login.spec.ts`
  - `tenant-pages.spec.ts`
  - `oss-config.spec.ts`
  - `open-app.spec.ts`
  - `notice-center.spec.ts`
  - `runtime-sidebar.spec.ts`
- 其中 [runtime-sidebar.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts) 已验证：
  - `job` 菜单可见
  - `open` 菜单可见
  - `tenant` 与 `oss-config` 入口可见
  - `workflow` 菜单隐藏
  - `ai` 菜单隐藏

### 当前阻塞项

1. `workflow` 仍未在 `95` 上运行
   - 当前不是能力口径问题，而是发布物未落地
2. `medium` 目标能力矩阵包含 `workflow`
   - 因此当前 `95` 只能算“中配主体通过，但 workflow 能力待补”

## Full

### 当前阻塞项

1. `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-workflow:latest`
   - 拉取结果：`manifest unknown`
2. `registry.cn-hangzhou.aliyuncs.com/xzy0112/han-ai:latest`
   - 拉取结果：`manifest unknown`

### 当前判断

- `full` 目前无法在 `95` 做真实拉起
- 在镜像未补齐前，不建议继续做 `full` 页面与接口验收

## 整改状态汇总

### 已从“失败/阻塞”转为“通过/已收口”

- `small` fresh deploy 基线菜单缺失
- `medium` 通知中心 `unreadCount/latest/sse` 被详情路由误吞
- `medium` 运行时能力误报 `workflow` 已启用
- `medium` 前端侧边栏未按运行时能力隐藏入口

### 仍需继续推进

1. 发布并部署 `han-workflow:latest`
2. 发布并部署 `han-ai:latest`
3. 在镜像补齐后重跑 `medium` 的 workflow 验收与 `full` 全链路验收

## 建议下一步

1. 先补 `han-workflow:latest` 镜像并在 `95` 拉起服务
2. 重新验证：
   - `/workflow/**` 接口
   - `medium` 运行时能力
   - 前端工作流菜单与页面
3. 再补 `han-ai:latest` 镜像，重跑 `full` 验收
