---
description: NiuMa AI 协作开发总规范（Han 分层索引版），默认对每次会话生效
version: 1.0
last_updated: 2026-05-15
---

# NiuMa AI 协作开发总规范

## 1. 文件定位

- 本文件是 `D:\code\Han` 的规则加载索引、执行导航和规则资产地图。
- Han 唯一正式规则文档仍然是 `docs/06-牛马协作总规则.md`。
- 本文件只负责把 `docs/06-牛马协作总规则.md` 提炼成可按任务加载的协作结构，不新增第二套正式规则。
- 本工作区采用“认知核心 + 正式总规则 + 路径规则 + 角色分工 + 记忆闭环”的工程化结构。

## 2. 核心红线

- 功能完整性、数据安全、可验证性、可回滚性，高于代码更短或回复更快。
- 不允许伪完成、伪测试、伪结论、伪引用。
- 若存在不确定性，先明确标记不确定，再调研、验证、给结论。
- 未经明确确认，不允许删除、合并、降级、关闭或弱化已有功能和边界处理。
- 默认全程使用中文沟通，默认所有新增或重写文档使用 UTF-8 编码。
- 结论必须绑定证据来源：代码、文档、日志、测试、配置、线上指标或官方文档。
- 涉及环境位置、依赖来源、安装目标、部署目标、覆盖范围、外部下载、服务启停、数据库变更等关键信息时，未明确说明前必须先确认。

## 3. 加载顺序

1. 系统指令、开发者指令、用户明确指令。
2. `D:\code\Han\AGENTS.md`。
3. `D:\code\Han\docs\06-牛马协作总规则.md`。
4. `D:\code\Han\niumma-rules.md`。
5. 当前任务命中的路径级 `AGENTS.md`。
6. `.codex\rules\*.md`、`.codex\hooks\*.md`、`.codex\skills\*.md`。
7. `docs/`、`sql/README.md`、部署手册、测试手册、运维手册、模块 README、代码注释。

## 4. 使用说明

- `.codex` 下的文档是仓库内工程资产，用于固化规则、角色和记忆。
- 这些文件不是平台自动能力声明，必须由执行者按任务类型主动读取。
- 路径级 `AGENTS.md` 遵循就近生效；更深层目录优先于更上层目录。
- 发现冲突规则时，不可自行猜测处理，必须先指出冲突与取舍方案。

## 5. 最小工作流

1. 先读规则、现状代码、相关文档、历史实现，再下判断。
2. 先确认目标、非目标、输入输出、依赖边界、成功标准和验收标准。
3. 高风险或跨模块任务先给方案，再执行。
4. 按步骤实现，保留边界处理、错误路径、补偿逻辑和兼容行为。
5. 实现后立即验证，不把验证拖到最后。
6. 交付时说明改动、验证、风险、未验证项与后续建议。
7. 需要沉淀经验时，把纠错、观察或演化记录写入 `.codex\memory`；新增正式约束必须同步回写 `docs/06-牛马协作总规则.md`。

## 6. 规则索引

### 6.1 正式总规则

- 文件：`D:\code\Han\docs\06-牛马协作总规则.md`
- 内容：仓库治理、README 治理、文档治理、SQL 治理、部署治理、95 运维治理和协作交付要求。

### 6.2 全局基线

- 文件：`D:\code\Han\.codex\rules\00-global-baseline.md`
- 内容：沟通要求、优先级、AI 红线、默认禁止事项和证据要求。

### 6.3 方案与工作流

- 文件：`D:\code\Han\.codex\rules\10-design-and-workflow.md`
- 内容：需求澄清、方案模板、任务拆解、角色分工原则、流程钩子。

### 6.4 开发实现基线

- 文件：`D:\code\Han\.codex\rules\20-engineering-baseline.md`
- 内容：改动前检查、Java/Vue/SQL 实现要求、A/I/B 分层、接口兼容、同步更新文档配置测试。

### 6.5 验证与交付

- 文件：`D:\code\Han\.codex\rules\30-verification-and-delivery.md`
- 内容：测试分层、验证记录、交付结构、证据要求、未验证项说明。

### 6.6 安全与发布

- 文件：`D:\code\Han\.codex\rules\40-security-and-release.md`
- 内容：输入校验、密钥保护、高风险操作问询、部署门禁、95 发布策略。

### 6.7 文档与知识治理

- 文件：`D:\code\Han\.codex\rules\50-doc-governance.md`
- 内容：正式文档入口、规则组织、文档增量维护、知识归档、避免双份口径。

### 6.8 Han 专项执行规则

- 文件：`D:\code\Han\.codex\rules\60-han-execution.md`
- 内容：Han 微服务技术栈、正式入口、三档部署、SQL 结构、95 发布、A/I/B 开发边界。

### 6.9 流程钩子

- 目录：`D:\code\Han\.codex\hooks`
- 作用：在开始任务、高风险操作前、交付前触发固定检查清单。

### 6.10 项目模式卡

- 目录：`D:\code\Han\.codex\skills`
- 作用：固化 `Protection mode`、`Design first`、`Evidence mode`、`Release gate`。

### 6.11 子角色职责

- 目录：`D:\code\Han\.codex\agents`
- 作用：定义规划、实现、验证三个角色的边界、输入输出和审核关系。

### 6.12 记忆闭环

- 目录：`D:\code\Han\.codex\memory`
- 文件：
  - `corrections.jsonl`
  - `observations.jsonl`
  - `learned-rules.md`
  - `evolution-log.md`
- 作用：记录纠错、观察、提炼规则与系统演化。

## 7. 默认禁止的猜测性操作

- 未明确盘符或目录时，禁止自行全盘扫描 SDK、依赖、安装包或私有资源。
- 未明确来源时，禁止自行联网下载 SDK、运行库、驱动、压缩包、脚本或第三方工具。
- 未明确部署目标时，禁止自行发布到本机、远程机器、测试环境、预发环境或生产环境。
- 未明确覆盖策略时，禁止覆盖旧包、旧配置、旧服务、旧数据库。
- 未明确权限边界时，禁止写入系统目录、程序目录、共享目录、用户目录中的敏感位置。
- 未明确回滚方案时，禁止执行不可逆的安装、升级、迁移、删除、替换操作。

## 8. Han 固定禁止项

- 禁止提交 `han-ui/dist`。
- 禁止提交 `.m2`、`.codex-temp`、日志、压缩包、测试输出。
- 禁止把散装 SQL 放回根目录或重新拆成过细模块目录。
- 禁止继续把根目录旧 compose 作为长期正式入口。
- 禁止在 95 上使用 `/opt/han/source/Han-*` 或 `/opt/han/docker` 作为正式入口。
- 禁止新增乱码文档或双份口径文档。

## 9. 快捷模式映射

- `Protection mode`：读取 `D:\code\Han\.codex\skills\protection-mode.md`。
- `Design first`：读取 `D:\code\Han\.codex\skills\design-first.md`。
- `Evidence mode`：读取 `D:\code\Han\.codex\skills\evidence-mode.md`。
- `Release gate`：读取 `D:\code\Han\.codex\skills\release-gate.md`。
