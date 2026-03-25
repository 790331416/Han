# 2026-03-25 Full 档 AI 模块真实验收报告

## 验收目标

- 验证 `full` 部署档位中的 `han-ai` 服务是否已真实注册、可通过网关访问，并与前端页面契约对齐。
- 验证 AI 二期能力是否已经形成最小可用闭环，覆盖 `agent`、`workflow`、`chat` 页面与核心接口。
- 验证 `95` 服务器上的真实 Docker 环境是否已经包含本轮修复，而不是只在本地代码层通过。
- 验证 `AI 对话` 的主链路与边界链路是否稳定，覆盖 `stop`、`reload restore`、`regenerate`、`edit-regenerate`。
- 验证 `知识库 / MCP / Prompt` 三个 AI 管理页是否具备从 smoke 到真实交互的自动化保护网。

## 验收环境

- 验收日期：`2026-03-25`
- 验收服务器：`10.18.35.95`
- 后端入口：`http://10.18.35.95:9090`
- 前端入口：`http://10.18.35.95:3000`
- 源码分支：`codex/han-ui-remote-validate`

## 已完成收口

- `95` 上 `han-ai` 与 `han-ui` 均已完成真实部署，容器状态为 `healthy`。
- `/system/runtime/capabilities` 已返回 `tier=full` 且 `ai=true`。
- `AI 模型 / 智能体 / 工作流 / 对话` 页面与接口已形成最小闭环。
- `AI 对话` 已完成统一流式解析、真实消息同步、当前会话恢复。
- `知识库 / MCP / Prompt` 页面已先后补齐稳定 `data-testid`、smoke 回归与更深层交互回归。

## 接口验收结果

以下验证均在 `95` 真实网关上完成：

- `/system/runtime/capabilities` 返回 `200`
- `/ai/model/all?modelType=LLM` 返回 `200`
- `/ai/agent/*` 返回 `200`
- `/ai/workflow/*` 返回 `200`
- `/ai/chat/*` 返回 `200`
- `/ai/kb/list` 返回 `200`
- `/ai/mcp/list` 返回 `200`
- `/ai/prompt/list` 返回 `200`

## 前端页面验收结果

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

### 本轮新增通过的 AI 管理页深层回归

- `ai knowledge page should upload document, reindex, and complete hit test`
- `ai prompt page should render preview variables with real template data`
- `ai mcp page should refresh tools and show generated tool list`

## 本轮关键修复

### AI 对话链路

- 抽取 [ai-stream.ts](/D:/code/Han/han-ui/src/utils/ai-stream.ts) 统一处理流式 SSE 解析。
- `stream`、`regenerate`、`edit-regenerate` 统一走公共流式处理能力。
- 流式完成后主动回拉当前会话，保证界面使用后端真实消息和真实 `messageId`。
- 持久化当前 `conversationId` 与 `modelId`，刷新后优先恢复当前会话。

### AI 管理页交互链路

- 在 [knowledge 页面](/D:/code/Han/han-ui/src/views/ai/knowledge/index.vue) 补充文档管理、上传、重新索引、命中测试测试钩子。
- 在 [mcp 页面](/D:/code/Han/han-ui/src/views/ai/mcp/index.vue) 补充刷新工具、查看工具、工具列表对话框测试钩子。
- 在 [prompt 页面](/D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充变量输入、渲染按钮、渲染结果测试钩子。
- 新增 [ai-admin.ts](/D:/code/Han/han-ui/tests/e2e/utils/ai-admin.ts) 作为 AI 管理页测试辅助，负责真实接口准备与清理数据。
- 新增 [ai-admin-deep.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts) 覆盖知识库上传文档、重建索引、命中测试，Prompt 变量渲染，以及 MCP 工具刷新与展示。

## 验证结果

### 本地最新前端 + 95 后端

- `eslint` 通过
- `vite build` 通过
- `ai-admin-deep.spec.ts` 结果 `3 passed`

### 95 前端 + 95 后端

- 远端源码已拉到提交 `3264431`
- `han-ui` 已重建并替换最新镜像
- 容器状态 `healthy`
- `ai-admin-deep.spec.ts` 结果 `3 passed`

## 结论

- `full` 档 AI 模块已经从“服务可起”推进到“核心页面可回归、边界行为可验证、管理页关键交互可回归”的阶段。
- `知识库` 已形成“上传文档 -> 重新索引 -> 命中测试”的真实页面回归链路。
- `Prompt` 已形成“模板预览 -> 变量填写 -> 渲染结果”的真实页面回归链路。
- `MCP` 已形成“刷新工具 -> 查看工具列表”的真实页面回归链路。
- 当前 `95` 环境下，`AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 均具备最小可用闭环。

## 当前剩余项

- 真实模型供应商配置与调用链仍需继续收口。
- AI 管理页还可以继续向更深层操作推进，例如知识库多文档管理、Prompt 编辑更新回归、MCP 不同传输类型回归。
