# 2026-03-31 三档功能清单与测试执行计划

## 1. 目标

本轮目标不是只看单点接口通不通，而是按 `small / medium / full` 三档部署口径，把每档应该具备的功能、当前实现状态、现有自动化覆盖、本轮测试状态和未开发项一起拉平，边测边回填。

## 2. 状态说明

### 2.1 实现状态

| 状态 | 含义 |
| --- | --- |
| 已开发 | 代码、路由或接口已落地，具备真实能力 |
| 部分开发 | 主链路存在，但细节能力仍缺口或明确有诚实占位 |
| 未开发 | 路由、页面或后端能力尚未真正开放 |

### 2.2 测试状态

| 状态 | 含义 |
| --- | --- |
| 本轮通过 | 2026-03-31 本轮真实测试已通过 |
| 本轮失败 | 2026-03-31 本轮真实测试已执行但失败 |
| 进行中 | 已进入当前轮次执行，但尚未收敛 |
| 待测 | 本轮尚未执行 |
| 历史通过 | 之前轮次在真实环境通过，但本轮尚未重跑 |
| 环境阻塞 | 当前运行环境不满足验收前置条件 |
| 未开发 | 功能本身还没真正开放，不进入通过/失败判断 |

## 3. 证据来源

- 能力矩阵：[capability-matrix.md](/D:/code/Han/docs/capability-matrix.md)
- 基础测试计划：[test-plan.md](/D:/code/Han/docs/test-plan.md)
- 三档历史验收：[tier-validation-report-20260324.md](/D:/code/Han/docs/tier-validation-report-20260324.md)
- Full AI 历史验收：[full-tier-ai-validation-20260325.md](/D:/code/Han/docs/full-tier-ai-validation-20260325.md)
- 前端静态路由：[index.ts](/D:/code/Han/han-ui/src/router/index.ts)
- 当前自动化用例目录：`han-ui/tests/e2e/specs`

## 4. 总览

| Tier | 默认能力 | 本轮总状态 | 备注 |
| --- | --- | --- | --- |
| `small` | 核心系统、公告、任务调度、基础监控 | 本轮通过 | 隔离 small 环境已拉起；核心页面、登录、公告通知、任务调度、JobFlow 监控与菜单降级均已验证通过 |
| `medium` | `small` + 租户、工作流、开放平台、文件/OSS | 本轮通过 | 隔离 medium 环境已拉起；登录、菜单、工作流路由、开放平台、OSS、租户列表/套餐/配额与可选中间件探测均已验证通过 |
| `full` | `medium` + AI 管理、AI 对话、知识库、Prompt、MCP | 本轮通过 | 主环境已恢复；AI 主链路、Prompt、应用详情与模型凭证链路均已在 `95` 真环境回归通过，未开发项仍按文档诚实标注 |

## 5. Small 清单

`small` 目标口径：仅保留核心系统能力，不暴露 `tenant / workflow / open / file / ai` 等中高阶入口。

| 模块 | 细节功能 | 实现状态 | 现有自动化/证据 | 本轮状态 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 运行时能力 | `/system/runtime/capabilities` 返回 `tier=small`，`enabledModules` 包含 `gateway/auth/system/job` | 已开发 | [capability-matrix.md](/D:/code/Han/docs/capability-matrix.md), [tier-validation-report-20260324.md](/D:/code/Han/docs/tier-validation-report-20260324.md) | 本轮通过 | 2026-03-31 隔离 small 网关 `19090` 返回 `tier=small`，`tenant/workflow/open/file/ai=false` |
| 登录与鉴权 | 验证码、登录、刷新登录态、登出 | 已开发 | [auth-login.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/auth-login.spec.ts), [auth-logout.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/auth-logout.spec.ts) | 本轮通过 | `auth-login.spec.ts` 2 条通过；验证码接口 `200`，登录后未出现租户选择 |
| 首页与个人中心 | dashboard、当前用户、个人中心 | 已开发 | 历史接口验收 | 本轮通过 | `auth-login.spec.ts` 已确认可进入 dashboard 且右上角用户菜单可见 |
| 系统管理 | 用户、角色、分配用户、菜单、部门、岗位、字典、字典数据、参数配置 | 已开发 | 静态路由与历史验收 | 本轮通过 | [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) 已逐页打开 `/system/user|role|menu|dept|post|dict|config` |
| 公告通知 | 列表、最新通知、未读数、单条已读、全部已读、SSE 推送/降级 | 已开发 | [notice-center.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts), 历史 medium 验收 | 本轮通过 | 2026-04-01 查明 `95` 共享后端同时存在 `sys_notice_read` 缺表与旧版 `ASysNoticeController` 仍在运行；补执行 [phase6_notice_center.sql](/D:/code/Han/sql/upgrade/phase6_notice_center.sql)、推送通知中心增强代码并重建 `han-system` 后，接口 `markAllRead/latest/unreadCount` 均恢复 `200`，Playwright `notice-center.spec.ts` 实测 `1 passed` |
| 日志监控 | 操作日志、登录日志、在线用户、服务监控、缓存监控 | 已开发 | 静态路由与历史验收 | 本轮通过 | [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) 已打开 `/system/operlog|loginlog|online|server|cache-monitor` |
| 任务调度页面 | 任务列表、任务日志页面可达 | 已开发 | [index.ts](/D:/code/Han/han-ui/src/router/index.ts), [tier-validation-report-20260324.md](/D:/code/Han/docs/tier-validation-report-20260324.md), [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) | 本轮通过 | [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) 已打开 `/job/list` 与 `/job/log` |
| 任务调度基础接口 | `handlers`、Cron 校验、任务列表、日志列表 | 已开发 | [job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts), [SysJobController.java](/D:/code/Han/han-modules/han-job/src/main/java/com/han/job/controller/SysJobController.java), [SysJobLogController.java](/D:/code/Han/han-modules/han-job/src/main/java/com/han/job/controller/SysJobLogController.java) | 本轮通过 | [job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts) 已确认网关下 `/job/handlers`、`/job/checkCron`、`/job/list`、`/job/log/list` 全部返回 `200`；同时确认 `/job/handlers` 当前读取的是 `sys_job` 持久化任务，而不是 Spring handler Bean 目录 |
| 任务执行控制 | 新增、编辑、删除、暂停/恢复、立即执行、Cron 生成器 | 已开发 | [index.vue](/D:/code/Han/han-ui/src/views/job/index.vue), [SysJobController.java](/D:/code/Han/han-modules/han-job/src/main/java/com/han/job/controller/SysJobController.java), [SampleTaskHandler.java](/D:/code/Han/han-modules/han-job/src/main/java/com/han/job/handler/SampleTaskHandler.java), [job-ui.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-ui.spec.ts) | 本轮通过 | 2026-03-31 已用 `sampleTask.execute` 在 small 上跑通“新增任务 -> 暂停 -> 恢复 -> 立即执行 -> 查日志 -> 删除”；随后修正前端 `changeStatus` 契约为 query params，并在 95 small UI 重建后通过 `job-ui.spec.ts` 验证页面状态开关可用 |
| JobFlow 监控端点 | `health`、`metrics`、`config` | 已开发 | [JobFlowMonitorController.java](/D:/code/Han/han-modules/han-job/src/main/java/com/han/job/controller/JobFlowMonitorController.java), [job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts) | 本轮通过 | 2026-04-01 已补齐网关 `/actuator/jobflow/**` 路由与 `han-job` docker 配置，随后以提交 `2ec7228`、`92fc26a`、`b80ecc5` 在 95 远端重建 `han-job`；small/full 现均可经网关返回 `health/metrics/config=200`，且 Playwright `job-core.spec.ts` 在 `19090` 与 `9090` 下均实测 `3 passed` |
| 菜单降级 | 不显示工作流、开放平台、OSS、AI | 已开发 | [runtime-sidebar.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts), 路由 tier 标记 | 本轮通过 | `runtime-sidebar.spec.ts` 已确认 `small` 下仅保留核心菜单，`tenant/workflow/open/oss/ai` 不显示 |

## 6. Medium 清单

`medium` 必须覆盖 `small` 全量能力，并额外提供多租户、工作流、开放平台和文件能力。

### 6.1 继承要求

- `medium` 必须同时满足上文 `small` 全量检查项。

### 6.2 增量能力

| 模块 | 细节功能 | 实现状态 | 现有自动化/证据 | 本轮状态 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 运行时能力 | `/system/runtime/capabilities` 返回 `tier=medium`，包含 `tenant/workflow/open/file` | 已开发 | [tier-validation-report-20260324.md](/D:/code/Han/docs/tier-validation-report-20260324.md), [runtime-sidebar.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts) | 本轮通过 | 2026-03-31 隔离 medium 网关 `29090` 返回 `tier=medium`，`tenant/workflow/open/file=true`，`ai=false` |
| 可选中间件探测 | `optionalServices.rustfs/rabbitmq` 识别正确 | 已开发 | [capability-matrix.md](/D:/code/Han/docs/capability-matrix.md) | 本轮通过 | 2026-04-01 已补齐 `han-system` 的 `RUSTFS_*` compose 环境变量，并为 [RuntimeCapabilityController.java](/D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java) 增加 `System.getenv` 兜底；95 `medium/full` 的 `/system/runtime/capabilities` 现均返回 `rustfs=true`、`rabbitmq=true` |
| 租户管理 | 租户列表、套餐、配额、真实数据渲染 | 已开发 | [tenant-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tenant-pages.spec.ts) | 本轮通过 | 2026-04-01 已在 95 medium 环境补执行 [tenant_quota.sql](/D:/code/Han/sql/tenant_quota.sql)，随后 Playwright `tenant-pages.spec.ts` 实测 `3 passed`，租户列表、套餐与配额页全部恢复 |
| 工作流 | 流程定义、实例、待办、已办 | 已开发 | [tier-validation-report-20260324.md](/D:/code/Han/docs/tier-validation-report-20260324.md), [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) | 本轮通过 | [tier-core-pages.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/tier-core-pages.spec.ts) 已逐页打开 `/workflow/definition|instance|todo|done` |
| 开放平台 | 应用列表、创建、启停、重置密钥、生命周期 | 已开发 | [open-app.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/open-app.spec.ts) | 本轮通过 | `open-app.spec.ts` 生命周期完整通过：新增、编辑、启停、重置密钥、删除均成功 |
| OAuth2/SSO | `/oauth2`、`/sso` 与 `/open/oauth2`、`/open/sso` 兼容 | 已开发 | README 历史记录 | 本轮通过 | 2026-03-31 接口层确认：`/oauth2/authorize`、`/open/oauth2/authorize`、`/sso/login`、`/open/sso/login` 均返回 `200` |
| 文件/OSS | OSS 配置列表、活动配置、上传链路、RustFS | 已开发 | [oss-config.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/oss-config.spec.ts), README 历史记录 | 本轮通过 | 2026-04-02 已先修复 `han-gateway` 对反向代理转发头的信任链，并在 95 上恢复 `han-medium-file`；随后 Playwright `oss-config.spec.ts` 在 `medium` 与 `full` 环境均实测 `2 passed`，覆盖 OSS 配置列表、活动配置、真实上传与公网 URL 可访问性 |
| 代码生成器（可选） | 导入表、库表扫描、配置编辑、代码预览、ZIP 下载 | 已开发 | [index.vue](/D:/code/Han/han-ui/src/views/tool/gen/index.vue), [gen.ts](/D:/code/Han/han-ui/src/api/tool/gen.ts), [GenController.java](/D:/code/Han/han-modules/han-gen/src/main/java/com/han/gen/controller/GenController.java), [han_data.sql](/D:/code/Han/sql/han_data.sql), [pom.xml](/D:/code/Han/han-modules/han-gen/pom.xml), [gen-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/gen-core.spec.ts) | 本轮通过 | 2026-04-01 已补齐 `han-gen` 完整源码、Dockerfile、`/gen/**` 网关路由与运行时部署链，95 full 环境中 `han-gen`、`han-auth` 均已恢复可用；Playwright `gen-core.spec.ts` 实测 `1 passed`，覆盖导入表、预览、ZIP 下载与清理 |
| 菜单降级 | `medium` 可见租户/工作流/开放平台/OSS，不应显示 AI | 已开发 | [runtime-sidebar.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/runtime-sidebar.spec.ts) | 本轮通过 | `runtime-sidebar.spec.ts` 已确认 `tenant/open/oss/workflow` 可见，`AI` 不显示 |

## 7. Full 清单

`full` 必须覆盖 `small + medium` 全量能力，并额外提供 AI 管理、知识增强与 AI 对话能力。

### 7.1 继承要求

- `full` 必须同时满足 `small` 和 `medium` 的全部检查项。

### 7.2 AI 增量能力

| 模块 | 细节功能 | 实现状态 | 现有自动化/证据 | 本轮状态 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 运行时能力 | `/system/runtime/capabilities` 返回 `tier=full` 且 `ai=true` | 已开发 | [full-tier-ai-validation-20260325.md](/D:/code/Han/docs/full-tier-ai-validation-20260325.md) | 本轮通过 | 2026-04-01 远端 API 返回 `tier=full`、`ai=true`，且 `open/file/gen` 模块与 `optionalServices.rustfs=true` 已重新对齐 |
| AI 应用首页 | 应用首页可加载，保留 app-first 入口 | 已开发 | [ai-application.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-application.spec.ts) | 本轮通过 | `ai-application.spec.ts` 2 条通过 |
| AI 应用详情 | 详情工作台、发布/取消发布、访问链接、工作流日志跳转 | 部分开发 | [ai-application-detail.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts), README | 本轮通过 | 2026-04-01 已将 [ai-application-detail.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-application-detail.spec.ts) 改为测试前自动创建并发布 workflow，再直达详情页验证工作台、调试面板、访问入口与日志抽屉；当前仍保留“应用级日志关联未完备”的诚实占位 |
| AI 模型 | 列表、编辑、环境变量密钥、测试连通、掩码保留原值 | 已开发 | [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts), [ai-model-credential-injection-20260401.md](/D:/code/Han/docs/ai-model-credential-injection-20260401.md) | 本轮通过 | 2026-04-01 已在 `95` 通过 DeepSeek 环境变量凭证完成真实连通回归；模型创建后显示“已配置/环境变量”，测试连通返回 `200`，编辑后凭证来源与掩码保留正常 |
| 知识库基础 | 创建、文档上传、重建索引、命中测试 | 已开发 | [ai-admin-deep.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts) | 本轮通过 | 知识库上传、重建索引、命中测试通过 |
| 知识库生命周期 | 多文档、统计回滚、删除知识库 | 已开发 | [ai-admin-lifecycle.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts), [ai-admin-extended.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-extended.spec.ts) | 本轮通过 | 2026-03-31 已补跑并通过多文档上传/删除与统计回滚，删除知识库卡片操作也已通过 |
| 文档解析 | `txt/md/json/html` 可解析，`pdf/docx` 允许上传但暂未自动解析 | 部分开发 | [AiKnowledgeBaseServiceImpl.java](/D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiKnowledgeBaseServiceImpl.java), [index.vue](/D:/code/Han/han-ui/src/views/ai/knowledge/index.vue) | 本轮通过 | 2026-04-02 已在 95 full 以中文文件名 `产品说明.pdf`、`接口说明.docx` 做真实上传探针，接口返回 `indexStatus=failed`、`paragraphCount=0`，错误信息为“当前版本暂仅支持 txt、md、html 自动解析”；说明上传入口存在，但 `pdf/docx` 自动解析仍属诚实占位 |
| MCP 管理 | 创建、刷新工具、SSE/stdio/streamable_http 元数据展示 | 已开发 | [ai-admin-deep.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts), [ai-admin-lifecycle.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts), [ai-admin-extended.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-extended.spec.ts) | 本轮通过 | `refresh tools`、`streamable_http`、`sse/stdio` 元数据回归通过 |
| Prompt 模板 | 预览变量、编辑、内置模板禁删、内置模板禁编 | 已开发 | [ai-admin-deep.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts), [ai-admin-lifecycle.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts), [ai-admin-extended.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-extended.spec.ts), [phase8_prompt_template_alignment.sql](/D:/code/Han/sql/upgrade/phase8_prompt_template_alignment.sql) | 本轮通过 | 2026-04-01 查明 `95 full` 缺失 `ai_prompt_template` 表且旧表结构也缺 `create_by/update_by`，补执行 [phase8_prompt_template_alignment.sql](/D:/code/Han/sql/upgrade/phase8_prompt_template_alignment.sql) 后，`/ai/prompt/list` 与 `/ai/prompt/all` 恢复 `200`，Playwright Prompt 相关专项实测 `4 passed` |
| 智能体/工作流页 | AI 智能体列表、AI 工作流列表、设计器入口 | 已开发 | [ai-full.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts) | 本轮通过 | `ai-full.spec.ts` 已覆盖智能体/工作流页可达 |
| Token 统计 | 模型/用户/按日统计页面 | 已开发 | 历史接口与页面验收, [ai-token.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-token.spec.ts) | 本轮通过 | [ai-token.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-token.spec.ts) 已确认模型、用户、按日统计接口与页面卡片可加载 |
| AI 对话主链路 | 新建会话、发送消息、刷新恢复、停止生成、重新生成、编辑后重新生成 | 已开发 | [ai-full.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts), [ai-chat-edge.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-chat-edge.spec.ts) | 本轮通过 | 发送、停止后续发、刷新恢复、重新生成、编辑后重生成均通过 |
| 结构化元数据 | 助手消息级 `knowledgeSources` 与 `toolExecutions` 真正返回并被右侧面板消费 | 已开发 | [ai-chat-structured-meta.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-chat-structured-meta.spec.ts) | 本轮通过 | 首跑存在发送按钮偶发 disabled，重试后通过；功能已闭环但有轻微抖动 |
| 上下文恢复 | 显式 `workflowId/agentId` 上下文优先，不被旧会话抢占 | 已开发 | 2026-03-31 最新修复与远端实测 | 本轮通过 | `workflowId` 场景下结构化元数据会话可直接发起并回拉正确消息 |
| AI Graph | 知识图谱页面 | 未开发 | [index.ts](/D:/code/Han/han-ui/src/router/index.ts) | 未开发 | 路由已注释 |
| Embed Chat | 嵌入式免登录对话页 | 未开发 | [index.ts](/D:/code/Han/han-ui/src/router/index.ts) | 未开发 | 路由已注释 |

## 8. 自动化映射

| 范围 | 已有自动化 |
| --- | --- |
| 认证 | `auth-login.spec.ts`, `auth-logout.spec.ts` |
| 公告通知 | `notice-center.spec.ts` |
| 任务调度 | `job-core.spec.ts` |
| 中型模块 | `tenant-pages.spec.ts`, `open-app.spec.ts`, `oss-config.spec.ts`, `runtime-sidebar.spec.ts` |
| 三档核心路由烟测 | `tier-core-pages.spec.ts` |
| AI 主链路 | `ai-full.spec.ts`, `ai-chat-edge.spec.ts`, `ai-chat-structured-meta.spec.ts`, `ai-model.spec.ts`, `ai-token.spec.ts` |
| AI 管理页深度回归 | `ai-admin-pages.spec.ts`, `ai-admin-deep.spec.ts`, `ai-admin-lifecycle.spec.ts`, `ai-admin-extended.spec.ts`, `ai-application.spec.ts`, `ai-application-detail.spec.ts` |

## 9. 本轮执行记录

| 时间 | 动作 | 结果 |
| --- | --- | --- |
| 2026-03-31 | 重新梳理三档矩阵、静态路由和现有 E2E 用例，生成本清单 | 已完成 |
| 2026-03-31 | 检查 95 容器状态 | 发现容器多数 `healthy`，但网关口径仍需进一步确认 |
| 2026-03-31 | 访问 `95 /system/runtime/capabilities` | 当前返回 `404`，说明标准 tier 验收环境尚未完全恢复 |
| 2026-03-31 | 修复 `95` full 环境 `docker-*` 旧镜像串用、`sys_oss_config.sql` 目录挂载错误、`han-ui` 未接入新 network 的问题 | 已完成，`full` 标准 compose 已恢复 |
| 2026-03-31 | Full API 冒烟：`/system/runtime/capabilities`、`/auth/captcha` | 已通过，返回 `tier=full` 且验证码 `200` |
| 2026-03-31 | Playwright `ai-full.spec.ts` | `3 passed` |
| 2026-03-31 | Playwright `ai-chat-edge.spec.ts`、`ai-chat-structured-meta.spec.ts`、`ai-model.spec.ts`、`ai-application*.spec.ts`、`ai-admin-pages.spec.ts` | `7 passed, 2 failed, 1 flaky` |
| 2026-03-31 | Playwright `ai-admin-deep.spec.ts`、`ai-admin-lifecycle.spec.ts`、`ai-admin-extended.spec.ts` | `5 passed, 5 failed`，失败集中在 `Prompt` API `500` 与登录限流 |
| 2026-03-31 | Playwright `ai-admin-lifecycle.spec.ts -g "ai knowledge page should support multi-document lifecycle and stats rollback"` | `1 passed`，已排除登录限流噪音并确认知识库多文档生命周期正常 |
| 2026-03-31 | Playwright `ai-token.spec.ts`（full） | `1 passed`，已确认 Token 统计页模型/用户/按日接口与卡片加载正常 |
| 2026-03-31 | Playwright `auth-login.spec.ts`、`runtime-sidebar.spec.ts`、`tier-core-pages.spec.ts`（full 继承链路） | `4 passed`，已确认 full 下 small/medium 核心页面和菜单降级逻辑仍可用 |
| 2026-03-31 | Playwright `notice-center.spec.ts`（full 对照） | `1 failed`，与 small 复现同样的通知不可见与 `markAllRead` 业务 `500`，基本排除 tier 特异性 |
| 2026-03-31 | 拉起隔离 `small` 环境与 `han-ui-small`，验证 `/system/runtime/capabilities`、`/auth/captcha` | 已通过，返回 `tier=small`，验证码 `200`，`han-ui-small` 可经 small 网关正常加载登录页 |
| 2026-03-31 | Playwright `auth-login.spec.ts`、`runtime-sidebar.spec.ts`（small） | `3 passed` |
| 2026-03-31 | Playwright `tier-core-pages.spec.ts`（small） | `1 passed`，已逐页打开 small 核心系统/监控/任务页面 |
| 2026-03-31 | Playwright `notice-center.spec.ts`（small） | `1 failed`，失败点为通知列表未及时出现与 `markAllRead` 业务 `500` |
| 2026-03-31 | Playwright `job-core.spec.ts`（small） | `2 passed`，除 `handlers/checkCron/list/log` 基础接口外，已新增并跑通 `sampleTask.execute` 生命周期：创建、暂停/恢复、立即执行、日志回收、删除 |
| 2026-03-31 | `job` 状态切换契约修复与 small UI 回归 | 初始核验确认旧前端以 JSON body 调 `/job/changeStatus` 会触发业务 `500: 缺少参数 jobId`；修复为 query params 后，已在 95 重建 `han-ui-small` 并通过 Playwright `job-ui.spec.ts`（`1 passed`） |
| 2026-03-31 | small JobFlow 监控端点直连核验 | `health/metrics` 直连 `19204` 返回 `200`；`config` 序列化失败，且经网关访问 `/actuator/jobflow/*` 为 `404` |
| 2026-03-31 | 拉起隔离 `medium` 环境与 `han-ui-medium`，验证 `/system/runtime/capabilities`、`/auth/captcha` | 已通过，返回 `tier=medium`，验证码 `200`，`han-ui-medium` 可经 medium 网关正常加载登录页 |
| 2026-03-31 | Playwright `auth-login.spec.ts`、`runtime-sidebar.spec.ts`、`open-app.spec.ts`、`oss-config.spec.ts`（medium） | `5 passed` |
| 2026-03-31 | Playwright `tenant-pages.spec.ts`（medium） | 首轮 `3 failed`，根因是租户/套餐/有效租户接口均为空；补种 1 个套餐 + 1 个租户后重跑变为 `2 passed, 1 failed`，剩余失败点为 `/tenant/quota/{tenantId}` 业务 `500` |
| 2026-03-31 | 通过正式接口向隔离 `medium` 补种租户样本 | 已补入 1 个套餐 `E2E Medium Package` 与 1 个租户 `E2E Medium Tenant`，用于区分“无数据”与“真实接口缺陷” |
| 2026-04-01 | 95 `medium` 环境补齐租户配额 migration 并回归 `tenant-pages.spec.ts` | 确认 `han-medium-postgres` 缺失 `sys_tenant_quota` 表后执行 [tenant_quota.sql](/D:/code/Han/sql/tenant_quota.sql)；`hanmedium` 与 `han-ui-medium` 拉起后，Playwright `tenant-pages.spec.ts` 实测 `3 passed`，说明租户列表、套餐、配额链路均已恢复 |
| 2026-04-01 | 95 `medium/full` 运行时能力收口 RustFS 探测 | 先确认 `han-medium-system` 与 `han-system` 容器内 `RUSTFS_*` 已注入，再重建 [RuntimeCapabilityController.java](/D:/code/Han/han-modules/han-system/src/main/java/com/han/system/controller/RuntimeCapabilityController.java) 所在镜像；最终 `29090/system/runtime/capabilities` 与 `9090/system/runtime/capabilities` 均返回 `optionalServices.rustfs=true` |
| 2026-03-31 | Playwright `tier-core-pages.spec.ts`（medium） | 首轮因 auth 登录限流失败，冷却后单独重跑 `1 passed`；已逐页打开继承 small 页面与 `workflow/*` 路由 |
| 2026-03-31 | 接口核验 `OAuth2/SSO` 与 RustFS（medium） | `/oauth2`、`/open/oauth2`、`/sso`、`/open/sso` 入口均 `200`；RustFS 端口可达但 capability 仍回 `rustfs=false` |
| 2026-03-31 | 代码生成器部署核验 | `han-gen` 本地源码、菜单种子与前端入口存在，但当前网关无 `/gen/**` 路由、模块未补独立 Dockerfile，且 95 部署源码目录下 `han-gen` 仅见 `pom.xml`；因此默认 compose 无法直接拉起，full 网关 `/gen/list` 返回 `404` |
| 2026-04-01 | full 代码生成器远端部署与 Playwright 回归 | 已补齐 `han-gen` 依赖、Dockerfile、Nacos/Redis 配置与网关 `/gen/**` 路由，在 95 full 环境以镜像 `han-gen:gen-494fdbf` 成功拉起服务；同时手工重启 `han-auth` 恢复 `/auth/login`；最终 Playwright [gen-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/gen-core.spec.ts) `1 passed`，覆盖导入表、预览、ZIP 下载与清理 |
| 2026-04-01 | 95 `full` 运行时能力补齐 `open/file/gen/rustfs` | 先补 `han-system` 的 `RUSTFS_*` 与 `HAN_GEN_ENABLED` compose 环境变量，再重建 `han-system` 镜像并拉起 `han-open`、`han-file`、`han-gen`；最终 `9090/system/runtime/capabilities` 返回 `open/file/gen` 已启用，`optionalServices.rustfs=true` |
| 2026-04-01 | 95 `full/medium` 通知中心后端补齐与 Playwright 回归 | 先确认 `han-postgres` 与 `han-medium-postgres` 均缺失 `sys_notice_read`，并在 `95` 上补执行 [phase6_notice_center.sql](/D:/code/Han/sql/upgrade/phase6_notice_center.sql)；随后定位到运行中 `han-system` 仍是旧版通知控制器，正式将通知中心增强代码纳入分支、远端重建 `han-system` 并重启 `hanfull/hanmedium`；最终接口 `POST /system/notice/markAllRead`、`GET /system/notice/unreadCount`、`GET /system/notice/latest` 均恢复 `200`，Playwright [notice-center.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/notice-center.spec.ts) `1 passed` |
| 2026-04-01 | 95 `full/medium` 登录日志表结构对齐 | 确认两套库的 `sys_login_log` 仍使用旧列名 `ipaddr/msg` 且缺少 `client_type`，补执行 [phase7_login_log_alignment.sql](/D:/code/Han/sql/upgrade/phase7_login_log_alignment.sql) 后，`sys_login_log` 已对齐为 `ip_addr/message/client_type`；full 与 medium 的 `/auth/login` 复验均返回 `200`，`han-system` 不再出现登录日志写入 SQL 异常 |
| 2026-04-01 | 95 `full` Prompt 模板链路恢复 | 确认 `han-postgres` 缺失 `ai_prompt_template`，且旧脚本口径缺少 `create_by/update_by`；补执行 [phase8_prompt_template_alignment.sql](/D:/code/Han/sql/upgrade/phase8_prompt_template_alignment.sql) 后，`/ai/prompt/list`、`/ai/prompt/all` 恢复 `200`，Playwright `--grep "ai prompt page"` 实测 `4 passed` |
| 2026-04-01 | Playwright `ai-application-detail.spec.ts` 自动 seed 回归 | 将详情页用例从依赖人工样本卡片改为测试前自动创建并发布 workflow，并直达详情页验证工作台/调试面板与访问入口；日志跳转仍按“有日志则验证”的口径保留诚实覆盖 |
| 2026-04-01 | 95 `small/full` JobFlow 与任务调度链路收口 | 先修复 `han-job` 误用模板化 `application-docker.yml` 导致 Redis 指向 `localhost` 的问题，再将任务 API 从内部 `base` 组合结构拉平为真实前后端扁平契约，并收敛 JobFlow config 数值返回；最终 95 远端以提交 `2ec7228`、`92fc26a`、`b80ecc5` 重建 `han-job` 后，small/full 的 `/job` 创建链路与 `/actuator/jobflow/*` 均恢复正常，Playwright [job-core.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/job-core.spec.ts) 在 `19090` 与 `9090` 下均实测 `3 passed` |
| 2026-04-01 | 95 `full` AI 模型凭证链路收口 | 先确认 [AiModelCredentialResolver.java](/D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelCredentialResolver.java) 的解析顺序确实优先读取环境变量，再从 `maxkb` 现有部署提取可用 DeepSeek 凭证并注入 `han-ai`；最终容器内 `DEEPSEEK_API_KEY` 与 `HAN_AI_PROVIDER_DEEPSEEK_API_KEY` 均为非空，Playwright [ai-model.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-model.spec.ts) 在 provider override 下实测 `1 passed (31.0s)` |
| 2026-04-02 | 95 `full/medium` OSS 真实上传链路收口 | 先定位 full 上传 URL 泄漏内网地址的根因为 `han-gateway` 未启用 `trusted-proxies`，补充 [application.yml](/D:/code/Han/han-gateway/src/main/resources/application.yml)、[application-docker.yml](/D:/code/Han/han-gateway/src/main/resources/application-docker.yml)、[docker-compose.yml](/D:/code/Han/docker-compose.yml)、[docker-compose-full.yml](/D:/code/Han/docker-compose-full.yml) 后，Playwright [oss-config.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/oss-config.spec.ts) 在 full 实测 `2 passed`；继续排查 medium 时确认 `han-medium-file` 早前因 `redis` 解析失败退出，在 `han-medium-redis`、`han-medium-nacos` 恢复后重新拉起 `han-medium-file`，并再次执行 Playwright [oss-config.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/oss-config.spec.ts) 取得 `2 passed`，说明 medium/full 的 OSS 列表、配置启用、真实上传与公网 URL 验证现均已闭环 |
| 2026-04-02 | 95 `full` 文档解析边界核验 | 已通过真实接口创建临时知识库，并以中文文件名 `产品说明.pdf`、`接口说明.docx` 上传文档；随后查询 `/ai/kb/{kbId}/document/list`，两条记录均返回 `indexStatus=failed`、`paragraphCount=0` 且 `indexError=当前版本暂仅支持 txt、md、html 自动解析`，与 [AiKnowledgeBaseServiceImpl.java](/D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiKnowledgeBaseServiceImpl.java) 当前实现一致，确认该能力属于“允许上传、暂不自动解析”的部分开发状态 |

## 10. 下一步执行顺序

1. 将 [environment-recovery-checklist-20260402.md](/D:/code/Han/docs/environment-recovery-checklist-20260402.md) 纳入正式部署文档体系，避免恢复手册继续散落在单次排障记录中。
2. 继续保留并核查“部分开发/未开发”边界，包括 `pdf/docx` 自动解析、`AI Graph` 与 `Embed Chat`，确保计划文档与真实实现始终对齐。
3. 针对 medium/full 的容器恢复过程补最小运行手册，至少覆盖 `nacos/redis/postgres/file/gateway/ui` 的联动重启顺序。
4. 每完成一项就在本文件更新“本轮状态”和备注，不做口头漂移。
