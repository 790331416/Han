# 2026-03-25 Full 档 AI 模块真实验收报告

## 验收目标

- 验证 `full` 部署档位下 `han-ai` 服务是否已真实注册、可被网关访问，并与前端页面契约对齐。
- 验证 AI 二期能力是否完成最小可用闭环，包括 `agent`、`workflow`、`chat` 页面与核心接口。
- 验证 `95` 服务器上的真实 Docker 环境是否已包含本轮修复，而不是只在本地代码层通过。

## 验收环境

- 验收日期：`2026-03-25`
- 验收服务器：`10.18.35.95`
- 后端入口：`http://10.18.35.95:9090`
- 前端入口：`http://10.18.35.95:3000`
- 源码分支：`codex/han-ui-remote-validate`
- 远端源码提交：`192b528`

## 本轮收口内容

- 确认 `95` 上 `han-ai` 与 `han-ui` 容器均为 `healthy`。
- 将 AI 二期源码拉取到 `95`，补齐并执行 `ai_agent` 表初始化。
- 在 `95` 上重新构建 `han-ai` 镜像并通过 `docker-compose-full.yml` 重建 `ai` 服务。
- 在 `95` 上重新构建 `han-ui` 并以独立容器方式重新部署到 `docker_han-network`。
- 修复 `AI 聊天` 场景下模型下拉为空的问题：
  - 后端 [AiModelServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java) 为 `selectAll(modelType)` 增加“启用模型优先，若无启用模型则回退全部模型”的兼容逻辑。
  - 初始化脚本 [han_ai.sql](D:/code/Han/sql/han_ai.sql) 将默认 `DeepSeek Chat` 模型状态改为启用。
  - `95` 现网历史数据同步执行 `update ai_model set status='0' where model_name='DeepSeek Chat';`。
- 为 AI 页面补齐 Playwright 测试钩子，并新增 [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)。

## 接口验收结果

以下验证均在 `95` 真实网关上完成：

- `/system/runtime/capabilities` 返回 `200`
- 运行时能力返回：
  - `tier=full`
  - `enabledModules` 包含 `ai`
- `/ai/model/all?modelType=LLM` 返回 `200`
  - 返回模型数量大于 `0`
  - 当前已确认包含 `DeepSeek Chat`
- `/ai/agent/add`、`/ai/agent/publish/{id}`、`/ai/agent/chat/{id}` 返回 `200`
- `/ai/workflow/add`、`/ai/workflow/publish/{id}`、`/ai/workflow/chat/{id}` 返回 `200`
- `/ai/chat/send`、`/ai/chat/conversations`、`/ai/chat/messages/{conversationId}` 返回 `200`

本轮远端抽查样例结果：

- `tier: full`
- `ai_enabled: true`
- `llm_model_count: 1`

## 前端页面验收结果

本轮前端不是静态检查，而是“真实浏览器 + 真实远端前后端”验证：

- 执行环境：本地 Playwright
- 页面入口：`http://10.18.35.95:3000`
- API 入口：`http://10.18.35.95:9090`
- 测试文件：[ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)

执行结果：

- `2 passed`

覆盖范围：

- `ai agent and workflow pages should load on full tier`
  - 验证 `AI 智能体` 页面可见
  - 验证 `AI 工作流` 页面可见
  - 验证运行时 `full + ai=true` 下菜单与页面入口可用
- `ai chat page should send a message and render assistant reply`
  - 验证 `AI 对话` 页面可见
  - 验证模型下拉可选
  - 验证发送消息成功
  - 验证页面渲染助手回复

## 结论

- `full` 档 AI 模块已从“镜像缺失、服务不可拉起”收口为“真实服务可运行、真实页面可回归”。
- 当前 `95` 环境下，`AI 模型 / 智能体 / 工作流 / 对话` 的最小闭环已经成立。
- 三档部署验收中，`full` 的核心阻塞项已被解除。

## 当前残留

- AI 二期目前仍是“壳层能力 + 最小可用闭环”，并未引入真实大模型供应商调用，也未补流式输出和复杂编排。
- 如果后续要继续向文档最终态靠近，下一阶段建议继续补：
  - `stream` 对话
  - `edit-regenerate / regenerate`
  - 真实模型供应商配置与调用链
  - 知识库检索与智能体编排联动

## 关联文件

- [AiModelServiceImpl.java](D:/code/Han/han-modules/han-ai/src/main/java/com/han/ai/service/impl/AiModelServiceImpl.java)
- [han_ai.sql](D:/code/Han/sql/han_ai.sql)
- [index.vue](D:/code/Han/han-ui/src/views/ai/agent/index.vue)
- [index.vue](D:/code/Han/han-ui/src/views/ai/chat/index.vue)
- [index.vue](D:/code/Han/han-ui/src/views/ai/workflow/index.vue)
- [ai-full.spec.ts](D:/code/Han/han-ui/tests/e2e/specs/ai-full.spec.ts)
