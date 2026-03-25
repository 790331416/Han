# 2026-03-25 Full 档 AI 模块真实验收报告

## 验收目标

- 验证 `full` 档位下 `han-ai` 与 `han-ui` 是否已经在 `95` 服务器完成真实部署并稳定运行。
- 验证 AI 对话主链路、边界链路，以及 AI 管理页关键交互是否已经纳入自动化回归。
- 验证回归结论同时成立于“本地最新前端 + 95 后端”和“95 前端 + 95 后端”两种真实联调口径。

## 验收环境

- 验收日期：`2026-03-25`
- 验收服务器：`10.18.35.95`
- 前端入口：`http://10.18.35.95:3000`
- 后端入口：`http://10.18.35.95:9090`
- 验证分支：`codex/han-ui-remote-validate`

## 当前收口状态

- `95` 上 `han-ai` 与 `han-ui` 均为 `healthy`。
- `/system/runtime/capabilities` 返回 `tier=full` 且 `ai=true`。
- `AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 已形成最小可用闭环。
- `AI 对话` 已完成统一流式解析、真实消息同步、当前会话恢复、重新生成、编辑后重新生成。
- `知识库 / MCP / Prompt` 已从基础 smoke 推进到深层交互、生命周期、保护约束回归。
- `Prompt` 内置模板现在同时具备“前端禁止编辑”和“后端接口拒绝编辑”双层保护。

## 接口验收结果

以下接口均通过 `95` 真实网关完成验证：

- `/system/runtime/capabilities` 返回 `200`
- `/ai/model/all?modelType=LLM` 返回 `200`
- `/ai/agent/*` 返回 `200`
- `/ai/workflow/*` 返回 `200`
- `/ai/chat/*` 返回 `200`
- `/ai/kb/list` 返回 `200`
- `/ai/mcp/list` 返回 `200`
- `/ai/prompt/list` 返回 `200`
- `/ai/prompt/edit` 对内置模板返回非 `200` 业务码，符合保护预期

## 前端页面回归结果

### 已通过的 Full 档页面回归

- `ai agent and workflow pages should load on full tier`
- `ai chat page should send a message and render assistant reply`
- `ai chat page should support regenerate and edit-regenerate`

### 已通过的 AI 对话边界回归

- `ai chat should stop streaming and allow sending another message`
- `ai chat should restore current conversation after reload`

### 已通过的 AI 管理页 Smoke

- `ai knowledge page smoke should load and open create dialog`
- `ai mcp page smoke should load and open create dialog`
- `ai prompt page smoke should load and support create or preview entry`

### 已通过的 AI 管理页深层回归

- `ai knowledge page should upload document, reindex, and complete hit test`
- `ai prompt page should render preview variables with real template data`
- `ai mcp page should refresh tools and show generated tool list`

### 已通过的 AI 管理页生命周期回归

- `ai knowledge page should support multi-document lifecycle and stats rollback`
- `ai prompt page should support edit and keep preview rendering correct`
- `ai mcp page should distinguish sse and stdio tool metadata after refresh`

### 已通过的 AI 管理页保护与传输回归

- `ai knowledge page should delete knowledge base from card actions and clear list state`
- `ai prompt page should keep built-in templates protected from deletion`
- `ai prompt page should keep built-in templates protected from editing`
- `ai mcp page should support streamable_http tool metadata regression`

## 本轮关键收口

### AI 对话链路

- 抽取 `han-ui/src/utils/ai-stream.ts` 统一处理流式 SSE。
- `stream`、`regenerate`、`edit-regenerate` 统一走公共流式处理能力。
- 流式完成后主动回拉当前会话，保证界面使用后端真实消息和真实 `messageId`。
- 持久化当前 `conversationId` 与 `modelId`，刷新后优先恢复当前会话。

### AI 管理页交互链路

- `知识库` 已覆盖上传文档、重新索引、命中测试、多文档生命周期、卡片删除入口。
- `Prompt` 已覆盖模板预览、变量渲染、编辑更新、内置模板删除保护、内置模板编辑保护。
- `MCP` 已覆盖刷新工具、查看工具列表、`sse / stdio / streamable_http` 传输类型差异回归。
- `han-ui/tests/e2e/utils/ai-admin.ts` 负责通过真实接口准备和清理 AI 管理页测试数据。

### 本轮新增保护

- 后端在 `han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiPromptTemplateServiceImpl.java` 中对内置模板更新操作增加显式拦截。
- 前端在 `han-ui/src/views/ai/prompt/index.vue` 中对内置模板编辑按钮增加禁用保护。
- `han-ui/tests/e2e/specs/ai-admin-extended.spec.ts` 新增“内置模板禁止编辑”真实回归，并同时验证页面禁用态和接口拒绝态。

## 验证结果

### 本地最新前端 + 95 后端

- `eslint` 通过
- `vite build` 通过
- `han-ai` Maven 编译通过
- `ai-admin-deep.spec.ts` 结果 `3 passed`
- `ai-admin-lifecycle.spec.ts` 结果 `3 passed`
- `ai-admin-extended.spec.ts` 结果 `4 passed`

### 95 前端 + 95 后端

- `95` 源码已拉到提交 `85db33d`
- `han-ui` 已重新通过容器化 Node 构建 `dist`
- `han-ui` 镜像已重建并替换线上容器
- `han-ui` 与 `han-ai` 容器状态均为 `healthy`
- `ai-admin-extended.spec.ts` 结果 `4 passed`

## 结论

- `full` 档 AI 模块已经从“服务可起”推进到“核心页面可回归、边界行为可验证、管理页关键交互可回归、保护性约束可回归”的阶段。
- `Prompt` 已形成“预览 -> 变量渲染 -> 编辑更新 -> 再次渲染 -> 内置模板删除保护 -> 内置模板编辑保护”的真实页面回归链路。
- `95` 环境下，`AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 均具备最小可用闭环和持续回归能力。

## 当前剩余项

- 真实模型供应商配置与调用链仍需继续收口。
- AI 管理页仍可继续向更深层操作推进，例如知识库批量管理、Prompt 内置模板更多只读保护、MCP 外部真实服务联通回归。
