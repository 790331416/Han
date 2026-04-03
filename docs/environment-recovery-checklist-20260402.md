# 95 环境恢复 Checklist（2026-04-02）

## 1. 适用范围

这份清单面向 `10.18.35.95` 上的 `small / medium / full` 三档 Han 环境，目标不是解释架构，而是把本轮真实踩过的恢复动作沉成可重复执行的最小步骤，避免后续环境漂移时只能翻聊天记录。

关联文档：

- [tier-test-plan-20260331.md](/D:/code/Han/docs/tier-test-plan-20260331.md)
- [server-95-deploy-flow.md](/D:/code/Han/docs/server-95-deploy-flow.md)
- [ai-model-credential-injection-20260401.md](/D:/code/Han/docs/ai-model-credential-injection-20260401.md)
- [full-ui-menu-backfill-20260401.md](/D:/code/Han/docs/full-ui-menu-backfill-20260401.md)

## 2. 基础判断

先确认当前是在排哪一档：

- `small`: `19090` 网关, `3100` UI
- `medium`: `29090` 网关, `3200` UI
- `full`: `9090` 网关, `3000` UI

先看三个信号：

1. `GET /system/runtime/capabilities` 是否返回正确 `tier`
2. `POST /auth/login` 是否能返回 `code=200`
3. UI 壳是否能正常代理到网关，而不是 `502`

如果这三项里有一项不通，不要直接怀疑前端，先按下面的底座顺序排。

## 3. 底座恢复顺序

推荐顺序：

1. `postgres`
2. `redis`
3. `nacos`
4. `gateway`
5. 业务服务：`auth/system/job/file/open/tenant/workflow/ai/gen`
6. `ui`

`medium/full` 这轮都踩到过类似问题：

- 数据库停过，导致登录或业务表访问异常
- `redis/nacos` 先挂，业务服务启动时解析失败，随后容器退出但没人把它拉回来
- `gateway` 重建后，`han-ui`/`han-ui-medium` 还缓存旧 upstream，表现成前端 `502`

所以一个稳妥顺序是：

1. 先把 `postgres/redis/nacos` 拉到 `healthy`
2. 再起 `gateway`
3. 再起依赖它们的业务服务
4. 最后重启 UI 容器，避免 Nginx 继续代理旧地址

## 4. 必查环境变量

### 4.1 Gateway 反向代理头

如果 OSS 上传后的公网 URL 变成内网地址，比如 `172.x.x.x:9207`，优先检查 `gateway` 是否信任了转发头。

代码口径：

- [application.yml](/D:/code/Han/han-gateway/src/main/resources/application.yml)
- [application-docker.yml](/D:/code/Han/han-gateway/src/main/resources/application-docker.yml)

compose 口径：

- [docker-compose.yml](/D:/code/Han/docker-compose.yml)
- [docker-compose-full.yml](/D:/code/Han/docker-compose-full.yml)

关键变量：

- `SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES=.*`

### 4.2 RustFS 能力探测

如果 `/system/runtime/capabilities` 把 `optionalServices.rustfs` 误报成 `false`，检查：

- `RUSTFS_ENDPOINT`
- `RUSTFS_ACCESS_KEY`
- `RUSTFS_SECRET_KEY`

恢复后再验证 [RuntimeCapabilityController.java](/D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java) 返回值。

### 4.3 AI 模型凭证

如果 `full` 的模型页显示“未配置”，先不要盯前端，优先检查 `han-ai` 容器环境变量。

入口代码：

- [AiModelCredentialResolver.java](/D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelCredentialResolver.java)

常见变量：

- `DEEPSEEK_API_KEY`
- `DASHSCOPE_API_KEY`
- `OPENAI_API_KEY`
- `ZHIPU_API_KEY`
- `HAN_AI_PROVIDER_DEEPSEEK_API_KEY`
- `HAN_AI_PROVIDER_QWEN_API_KEY`

### 4.4 代码生成开关

如果 `full` 的 `/gen/**` 不可用，除了路由和服务本体，还要确认：

- `HAN_GEN_ENABLED=true`

## 5. 常见数据库补齐项

### 5.1 通知中心

症状：

- `markAllRead` 业务 `500`
- 最新通知、未读数异常

执行：

- [phase6_notice_center.sql](/D:/code/Han/sql/upgrade/phase6_notice_center.sql)

### 5.2 登录日志

症状：

- `/auth/login` 表面失败
- `han-system` 日志出现登录日志写入 SQL 异常

执行：

- [phase7_login_log_alignment.sql](/D:/code/Han/sql/upgrade/phase7_login_log_alignment.sql)

### 5.3 Prompt 模板

症状：

- `/ai/prompt/list` 或 `/ai/prompt/all` 返回 `500`

执行：

- [phase8_prompt_template_alignment.sql](/D:/code/Han/sql/upgrade/phase8_prompt_template_alignment.sql)

### 5.4 基线菜单

症状：

- `/system/menu/routers` 空
- UI 登录后无菜单或核心入口缺失

执行：

- [phase9_base_menu_backfill.sql](/D:/code/Han/sql/upgrade/phase9_base_menu_backfill.sql)

### 5.5 租户配额

症状：

- `medium` 的 `/tenant/quota/{tenantId}` 业务 `500`

执行：

- [tenant_quota.sql](/D:/code/Han/sql/tenant_quota.sql)

## 6. 分档特殊注意项

### 6.1 Small

- 只保留核心系统、公告通知、任务调度和基础监控
- 不应暴露 `tenant/workflow/open/oss/ai`
- `JobFlow` 需经网关 `/actuator/jobflow/**` 验证，而不是只看直连端口

### 6.2 Medium

- 本轮真实踩过 `han-medium-file` 因 `redis` 解析失败退出
- 如果 OSS 页面能开但上传 `503`，优先检查 `han-medium-file` 是否真的 `Up`
- 如果登录 API 已恢复但 UI 仍回登录页，多半是 `han-ui-medium` 还在代理旧 gateway upstream

### 6.3 Full

- OSS 外链错误优先看 `gateway trusted-proxies`
- AI 模型“未配置”优先看 `han-ai` 环境变量
- `Prompt`、`应用详情`、`结构化知识引用 + 工具轨迹` 已完成真环境验收，可直接按专项用例复扫

## 7. 最小验收命令

接口级最小验收：

1. `GET /system/runtime/capabilities`
2. `POST /auth/login`
3. `GET /system/user/current`
4. `GET /actuator/health`

专项回归建议：

- 通知中心：[notice-center.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts)
- 任务调度：[job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts)
- 侧栏能力：[runtime-sidebar.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts)
- 租户：[tenant-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tenant-pages.spec.ts)
- 开放平台：[open-app.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/open-app.spec.ts)
- OSS：[oss-config.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/oss-config.spec.ts)
- AI 对话：[ai-full.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts), [ai-chat-edge.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-chat-edge.spec.ts)
- 结构化元数据：[ai-chat-structured-meta.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-chat-structured-meta.spec.ts)
- AI 模型：[ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts)
- 代码生成：[gen-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/gen-core.spec.ts)

## 8. 当前仍需诚实标注的边界

这些不是恢复失败，而是当前产品边界：

- `AI Graph`: 未开发
- `Embed Chat`: 未开发
- `pdf/docx` 自动解析: 部分开发，当前口径仍是允许上传但未自动解析

## 9. 建议的后续沉淀

下一步最值得补的不是再堆一次排障记录，而是把下面两类内容纳入正式部署文档：

1. 95 三档环境的标准重启顺序
2. 数据库补齐 SQL 与环境变量检查表

这样下一次遇到服务漂移，先看 checklist 就能快速收口，不用再从零考古。

## 10. 2026-04-03 补充检查项

### 10.1 Full AI 重启前先核 provider key

- 2026-04-03 在 `95 full` 重新回归 AI 套件时确认：
  - [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts) 唯一失败点不是前端页面，而是运行中的 `han-ai` 没拿到真实 provider key
  - 当前 `ai_model.api_key` 与 `sys_config` 都不能作为有效回退
- 所以只要执行：
  - `docker compose -p hanfull -f /opt/han/docker/docker-compose-full.yml up -d ai`
  之前没有先确认宿主机环境变量来源，`han-ai` 就可能在重建后失去“已配置”状态。

### 10.2 最小必查项

- 在 `95` 宿主机执行 full AI 重启前，至少先确认以下变量有真实值：
  - `DEEPSEEK_API_KEY`
  - `DASHSCOPE_API_KEY`
  - `OPENAI_API_KEY`
  - `ZHIPU_API_KEY`
  - `HAN_AI_PROVIDER_DEEPSEEK_API_KEY`
  - `HAN_AI_PROVIDER_QWEN_API_KEY`
- 若这些变量仅存在于临时 shell，而没有落到持久化来源：
  - shell 退出后会丢
  - 下次 `compose up -d ai` 后会重新变成空值

### 10.3 建议口径

- 推荐把 `95 full` 的 AI provider key 固化到单独受控的宿主机环境文件或部署侧 secret 注入来源，而不是依赖临时手工 `export`
- 每次重启 `han-ai` 前后，都补做两步：
  - 容器内核对一次 `env | grep -E 'DEEPSEEK|DASHSCOPE|OPENAI|ZHIPU|HAN_AI_PROVIDER'`
  - 回跑 [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts) 或至少确认模型页显示“已配置”

### 10.4 2026-04-03 真环境结论

- 当前 `95 full` 的 AI 聊天、知识库、Prompt、应用详情、结构化引用与工具轨迹都已通过
- [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts) 在补齐 provider key 持久化后也已通过

### 10.5 当前持久化落点

- 2026-04-03 已在 `95` 宿主机落地：
  - `/opt/han/docker/.env`
- 当前用于 full AI 的持久化变量至少包括：
  - `DEEPSEEK_API_KEY`
  - `HAN_AI_PROVIDER_DEEPSEEK_API_KEY`
- 文件权限已收紧为 `600`
- `han-ai` 重建后，需继续保留这份 `.env`，否则下次 `docker compose ... up -d ai` 仍会把模型页打回“未配置”

### 10.6 当前恢复口径

- 如 `95 full` 再次出现模型页“未配置”，优先检查：
  - `/opt/han/docker/.env` 是否仍存在
  - `docker exec han-ai env | grep -E 'DEEPSEEK_API_KEY|HAN_AI_PROVIDER_DEEPSEEK_API_KEY'` 是否非空
- 当前真环境恢复完成后的回归结果：
  - [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts) `1 passed`
  - Full AI Playwright 专项 `24 passed`

### 10.7 2026-04-03 small/medium 首页“资源不存在”恢复口径

- 这轮在 `95 small` 和 `95 medium` 都复现到同一种现象：
  - 登录成功
  - dashboard 页面可进入
  - 但首页立刻弹出“资源不存在”
- 真正根因不是登录态，也不是用户菜单，而是旧 UI bundle 仍会请求 `/system/dashboard/charts`
  - `small` 后端默认不提供这组图表接口
  - `medium` 在线容器的旧 bundle 同样会触发这条请求
- 处理顺序建议固定为：
  1. 先确认是不是旧 UI 容器还在线
  2. 不要直接覆盖线上端口，先起 canary 端口验证
  3. canary 回归通过后，再切正式端口
- 本轮真实切换方式：
  - `small`：保留旧 `han-ui-small` 回滚位，使用 `han-ui:structuredmeta-5206f5e` 先起 `3101` canary，再切 `3100`
  - `medium`：保留旧 `han-ui-medium` 回滚位，使用 `han-ui:structuredmeta-5206f5e` 先起 `3201` canary，再切 `3200`
- 对应回归口径：
  - [auth-login.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/auth-login.spec.ts) 中 `dashboard should not show missing resource error after login`
  - `small` 正式端口整包 `10 passed`
  - `medium` 正式端口整包 `12 passed`

### 10.8 2026-04-03 small 底座补齐项

- 如果 `95 small` 出现“登录页能开但无法登录”或通知中心继续 `500`，优先补查这两项：
  - `sys_login_log` 是否仍停留在旧列名 `ipaddr/msg`
  - `sys_notice_read` 是否存在
- 这轮真实补齐后，small 才恢复到可完整回归状态：
  - 登录日志表对齐后，`/auth/login` 恢复正常
  - 通知中心缺表补齐后，[notice-center.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts) 恢复通过
- 补齐完成后的推荐最小回归：
  - [auth-login.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/auth-login.spec.ts)
  - [notice-center.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts)
  - [job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts)
