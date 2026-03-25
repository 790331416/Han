# 2026-03-25 Full 档 AI 模块真实验收报告

## 验收目标

- 验证 `full` 档位中的 `han-ai` 与 `han-ui` 是否已在 `95` 服务器真实部署并稳定运行。
- 验证 AI 对话主链路、边界链路，以及 AI 管理页关键交互是否已纳入自动化回归。
- 验证回归结论同时成立于“本地最新前端 + 95 后端”与“95 前端 + 95 后端”两种真实联调模式。

## 验收环境

- 验收日期：`2026-03-25`
- 验收服务器：`10.18.35.95`
- 后端入口：`http://10.18.35.95:9090`
- 前端入口：`http://10.18.35.95:3000`
- 源码分支：`codex/han-ui-remote-validate`

## 当前收口状态

- `95` 上 `han-ai` 与 `han-ui` 已完成真实部署，容器状态健康。
- `/system/runtime/capabilities` 已返回 `tier=full` 且 `ai=true`。
- `AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 均已形成最小可用闭环。
- `AI 对话` 已完成统一流式解析、真实消息同步、当前会话恢复。
- `知识库 / MCP / Prompt` 已从基础 smoke 推进到关键交互回归。

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

### 已通过的 AI 管理页深层回归

- `ai knowledge page should upload document, reindex, and complete hit test`
- `ai prompt page should render preview variables with real template data`
- `ai mcp page should refresh tools and show generated tool list`

### 本轮新增通过的 AI 管理页高级回归

- `ai knowledge page should support multi-document lifecycle and stats rollback`
- `ai prompt page should support edit and keep preview rendering correct`
- `ai mcp page should distinguish sse and stdio tool metadata after refresh`

## 本轮关键收口

### AI 对话链路

- 抽取 [ai-stream.ts](/D:/code/Han/han-ui/src/utils/ai-stream.ts) 统一处理流式 SSE。
- `stream`、`regenerate`、`edit-regenerate` 统一走公共流式处理能力。
- 流式完成后主动回拉当前会话，保证界面使用后端真实消息和真实 `messageId`。
- 持久化当前 `conversationId` 与 `modelId`，刷新后优先恢复当前会话。

### AI 管理页交互链路

- 在 [knowledge 页面](/D:/code/Han/han-ui/src/views/ai/knowledge/index.vue) 补充文档管理、上传、重新索引、命中测试测试钩子。
- 在 [mcp 页面](/D:/code/Han/han-ui/src/views/ai/mcp/index.vue) 补充刷新工具、查看工具、工具列表对话框测试钩子。
- 在 [prompt 页面](/D:/code/Han/han-ui/src/views/ai/prompt/index.vue) 补充编辑按钮、表单输入项、提交按钮、变量输入和渲染结果测试钩子。
- 新增 [ai-admin.ts](/D:/code/Han/han-ui/tests/e2e/utils/ai-admin.ts) 作为 AI 管理页测试辅助，负责真实接口准备与清理数据。
- 新增 [ai-admin-deep.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-deep.spec.ts)，覆盖知识库上传文档、重建索引、命中测试，Prompt 变量渲染，以及 MCP 工具刷新与展示。
- 新增 [ai-admin-lifecycle.spec.ts](/D:/code/Han/han-ui/tests/e2e/specs/ai-admin-lifecycle.spec.ts)，覆盖多文档上传与删除后的统计回落、Prompt 编辑更新后的预览与渲染，以及 MCP `sse/stdio` 传输类型差异化工具元数据。

## 验证结果

### 本地最新前端 + 95 后端

- `eslint` 通过
- `vite build` 通过
- `ai-admin-deep.spec.ts` 结果 `3 passed`
- `ai-admin-lifecycle.spec.ts` 结果 `3 passed`

### 95 前端 + 95 后端

- 远端源码已拉到提交 `71d0803`
- `han-ui` 已重建并替换最新镜像
- 容器状态 `healthy`
- `ai-admin-deep.spec.ts` 结果 `3 passed`
- `ai-admin-lifecycle.spec.ts` 结果 `3 passed`

## 结论

- `full` 档 AI 模块已经从“服务可起”推进到“核心页面可回归、边界行为可验证、管理页关键交互可回归、生命周期操作可回归”的阶段。
- `知识库` 已形成“上传文档 -> 重新索引 -> 命中测试 -> 单文档删除 -> 统计回落”的真实页面回归链路。
- `Prompt` 已形成“模板预览 -> 变量填写 -> 渲染结果 -> 编辑更新 -> 再次预览渲染”的真实页面回归链路。
- `MCP` 已形成“刷新工具 -> 查看工具列表 -> 区分不同传输类型元数据”的真实页面回归链路。
- 当前 `95` 环境下，`AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 均具备最小可用闭环和持续回归能力。

## 当前剩余项

- 真实模型供应商配置与调用链仍需继续收口。
- AI 管理页还可以继续向更深层操作推进，例如知识库批量删除、Prompt 删除保护、MCP `streamable_http` 类型回归。
