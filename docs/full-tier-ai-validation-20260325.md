# 2026-03-25 Full 档 AI 模块真实验收报告

## 验收目标

- 验证 `full` 部署档位中的 `han-ai` 服务是否已真实注册、可被网关访问，并与前端页面契约对齐。
- 验证 AI 二期能力是否已经形成最小可用闭环，覆盖 `agent`、`workflow`、`chat` 页面与核心接口。
- 验证 `95` 服务器上的真实 Docker 环境是否已包含本轮修复，而不是只在本地代码层通过。
- 验证 `AI 对话` 的边界链路是否稳定，包括 `stop`、`reload restore`、`regenerate`、`edit-regenerate`。
- 验证 `知识库`、`MCP`、`Prompt` 三个 AI 管理页面的基础 smoke 是否可用。

## 验收环境

- 验收日期：`2026-03-25`
- 验收服务器：`10.18.35.95`
- 后端入口：`http://10.18.35.95:9090`
- 前端入口：`http://10.18.35.95:3000`
- 源码分支：`codex/han-ui-remote-validate`

## 已完成收口

- `95` 上 `han-ai` 与 `han-ui` 均完成真实部署，服务状态为健康。
- `runtime capabilities` 已返回 `tier=full` 且 `ai=true`。
- `AI 模型 / 智能体 / 工作流 / 对话` 页面与接口已形成最小闭环。
- `AI 对话` 前端已补齐统一流式解析工具，避免发送、重生成、编辑后重生成各走各的解析逻辑。
- `AI 对话` 已在流式结束后主动同步当前会话真实消息，解决前端临时 `messageId` 与后端真实消息脱节的问题。
- `AI 对话` 已补齐当前会话和模型选择的轻量持久化，刷新后可恢复最近会话。
- `知识库 / MCP / Prompt` 页面已补齐稳定的 Playwright 测试钩子。

## 接口验收结果

以下验证均在 `95` 真实网关上完成：

- `/system/runtime/capabilities` 返回 `200`
- `/ai/model/all?modelType=LLM` 返回 `200`，且可返回可选模型
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

### 新增通过的边界回归

- `ai chat should stop streaming and allow sending another message`
- `ai chat should restore current conversation after reload`

### 新增通过的 AI 管理页 smoke

- `ai knowledge page smoke should load and open create dialog`
- `ai mcp page smoke should load and open create dialog`
- `ai prompt page smoke should load and support create or preview entry`

## 本轮关键修复

### AI 对话链路

- 抽取 `han-ui/src/utils/ai-stream.ts` 统一处理流式 SSE 解析。
- `stream`、`regenerate`、`edit-regenerate` 统一走公共流式处理能力。
- 流式完成后主动回拉当前会话，保证界面使用后端真实消息和真实 `messageId`。
- 持久化当前 `conversationId` 和 `modelId`，刷新后优先恢复当前会话；如无持久化记录，则回到最近一条会话。

### AI 管理页面

- 为 `知识库`、`MCP`、`Prompt` 页面补充稳定 `data-testid`。
- 为 `Prompt` 预览弹层补充稳定定位入口。
- 将三类页面的 smoke 回归固化进 Playwright 测试。

## 结论

- `full` 档 AI 模块已经从“能起服务”进一步收口到“核心页面可回归、边界行为可验证”的阶段。
- 当前 `95` 环境下，`AI 模型 / 智能体 / 工作流 / 对话 / 知识库 / MCP / Prompt` 均具备最小可用闭环。
- `AI 对话` 的主链和边界链路目前都已纳入自动化回归，后续继续扩能力时有现成保护网。

## 当前剩余项

- 真实模型供应商配置与调用链仍需继续收口。
- `知识库` 深层操作链路，如上传文档、命中测试、重建索引，还可以继续补更深一层的自动化。
- `Prompt` 渲染变量填充、`MCP` 工具刷新结果等，也适合继续补成更完整的交互回归。
