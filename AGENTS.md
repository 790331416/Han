# Han 工作区认知核心

## 1. 项目定位

- 项目名称：Han。
- 工作区根目录：`D:\code\Han`。
- 当前定位：本目录承载 Han 企业级微服务平台的代码、文档、SQL、部署资产、规则资产和后续工程化沉淀。
- 当前目标：建立一套可执行的 Codex 协作规则，把沟通、设计、实现、验证、交付和复盘沉淀为仓库内资产。

## 2. 强制入口

- 本仓库唯一正式规则文档是 `docs/06-牛马协作总规则.md`。
- 本仓库的分层规则索引是 `niumma-rules.md`，用于按任务类型加载 `.codex` 下的规则资产。
- 进入本仓库的每一次对话、任务、代码修改或文档修改，都必须先读取并遵守 `docs/06-牛马协作总规则.md`，再按任务类型读取 `niumma-rules.md` 和 `.codex` 下的对应规则。
- `.codex` 目录中的文档是本仓库的规则资产、角色资产和演化资产；它们不是替代 `docs/06-牛马协作总规则.md` 的第二套正式规则。
- 进入具体子目录前，继续读取当前路径最近的 `AGENTS.md`；当前仓库暂未设置更深层路径级 `AGENTS.md`。

## 3. 默认工作模式

- 默认全程使用中文回复、中文说明、中文文档。
- 默认所有新增或重写文档使用 UTF-8 编码，禁止乱码。
- 回复默认结论先行、尽量精简：第一句先给结果，过程和证据放后面；靠删内容做短而不是压缩句子；简单问题直接用散文答，不套标题分节。完整口径见 `docs/06-牛马协作总规则.md` 第 17 节。
- 默认启用 `Protection mode`、`Design first`、`Evidence mode`。
- 未验证、未执行、未确认的内容，不得表述为已完成。
- 未经明确授权，不得删除、合并、降级、关闭或弱化已有功能和边界处理。
- 涉及打包、下载、安装、部署、发布、改配置、服务启停、数据库操作时，只要目标环境、盘符、目录、版本、来源、覆盖范围、回滚方式不明确，必须先确认。

## 4. Han 固定边界

- 正式入口固定为 `README.md`、`docs/`、`sql/`、`deploy/`。
- 正式规则文档只有一份：`docs/06-牛马协作总规则.md`。
- SQL 只能使用当前正式结构：
  - `sql/tiers/small/small-init.sql`
  - `sql/tiers/small/small-init-mysql.sql`
  - `sql/tiers/medium/medium-init.sql`
  - `sql/tiers/full/full-init.sql`
  - `sql/upgrades/postgres/`
- PostgreSQL 是兼容默认值；MySQL 8.4 是正式可选数据库。当前一键部署支持矩阵为 `small=PostgreSQL/MySQL`、`medium/full=PostgreSQL`，不得把尚未实库验证的 medium/full MySQL 表述为已支持。
- 三档正式部署入口固定为：
  - `deploy/small`
  - `deploy/medium`
  - `deploy/full`
- 95 环境只能从 `/opt/han/repo/Han` 和 `/opt/han/deploy/{small,medium,full}` 发布。

## 5. 完成标准

- 完成必须同时满足：目标明确、改动落地、验证有证据、风险已说明、未验证项已标注。
- 交付必须包含：改了什么、验证了什么、还有什么未验证或残留风险。
- 涉及文档、SQL、部署结构或 95 发布的变更，必须同步更新对应手册和验证记录。
- 高风险改动必须附带回滚思路、发布门禁或观察指标。

## 6. 防偏航策略

- 先读规则、现状代码、相关文档、历史实现，再下判断。
- 先做需求澄清与方案设计，再进入高风险实现。
- 发现规则冲突、上下文缺失或环境信息不明确时，先指出冲突和缺口，再请求确认。
- 不把建议包装成事实，不把推测说成结论。
- 文档默认增量维护，不围绕同一主题无序新增重复文档。

## 7. 规则资产地图

| 场景 | 必读文件 |
| --- | --- |
| 所有任务 | `D:\code\Han\docs\06-牛马协作总规则.md` |
| 分层规则索引 | `D:\code\Han\niumma-rules.md` |
| 方案设计、任务拆解 | `D:\code\Han\.codex\rules\10-design-and-workflow.md` |
| 日常开发与改代码 | `D:\code\Han\.codex\rules\20-engineering-baseline.md` |
| 测试、验收、汇报 | `D:\code\Han\.codex\rules\30-verification-and-delivery.md` |
| 回复的组织与精简 | `D:\code\Han\docs\06-牛马协作总规则.md` 第 17 节 |
| 安全、部署、发布、高风险操作 | `D:\code\Han\.codex\rules\40-security-and-release.md` |
| 文档维护、规则沉淀 | `D:\code\Han\.codex\rules\50-doc-governance.md` |
| Han 专项执行规则 | `D:\code\Han\.codex\rules\60-han-execution.md` |
| 开发手册 | `D:\code\Han\docs\02-开发手册.md` |
| 部署手册 | `D:\code\Han\docs\03-部署手册.md` |
| 测试与验收 | `D:\code\Han\docs\04-测试与验收手册.md` |
| 95 运维 | `D:\code\Han\docs\05-运维与95环境手册.md` |
| AI 短剧开发 | `D:\code\Han\docs\08-AI短剧开发手册.md` |

## 8. 自进化目录结构

- `.codex\rules`：按主题拆分后的执行规则。
- `.codex\hooks`：关键时刻要触发的流程清单。
- `.codex\skills`：项目内部可复用模式卡。
- `.codex\agents`：子角色职责设计稿。
- `.codex\memory`：纠错、观察、规则提炼、演化日志。

## 9. 冲突处理

- 系统指令、开发者指令、用户明确指令优先。
- Han 仓库规则冲突时，以 `docs/06-牛马协作总规则.md` 为准。
- `.codex`、`niumma-rules.md`、其他手册若与 `docs/06-牛马协作总规则.md` 冲突，必须先指出冲突点和取舍影响，禁止自行猜测。
