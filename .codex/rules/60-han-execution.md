# Han 专项执行规则

## 1. 文件定位

本文件用于约束 Han 企业级微服务平台的长期开发节奏，防止后续跑偏、绕开正式入口、拆出双份规则或破坏三档部署与 SQL 结构。

## 2. 必读文件

每次处理 Han 代码、方案、测试、文档、SQL 或部署前，按任务需要读取：

1. `D:\code\Han\AGENTS.md`
2. `D:\code\Han\docs\06-牛马协作总规则.md`
3. `D:\code\Han\niumma-rules.md`
4. `D:\code\Han\docs\02-开发手册.md`
5. `D:\code\Han\docs\03-部署手册.md`
6. `D:\code\Han\docs\04-测试与验收手册.md`
7. `D:\code\Han\docs\05-运维与95环境手册.md`
8. 当前任务命中的路径级 `AGENTS.md`

## 3. 技术栈锁定

- 后端主工程使用 Java 21、Spring Boot、Spring Cloud、Maven。
- 前端主工程使用 Vue 3、TypeScript、Vite。
- PostgreSQL 是默认数据库，MySQL 8.4 是正式可选数据库；当前一键部署只在 small 档开放 MySQL。
- 服务治理和配置中心使用 Nacos。
- 三档部署使用 `small / medium / full`。

未得到明确确认，不允许把主工程改成其他技术栈或新增平行替代架构。

## 4. 能力边界锁定

不得无理由删除、弱化或跳过以下能力维度：

- Gateway。
- Auth。
- System。
- JobFlow。
- Tenant。
- Workflow。
- Open。
- File。
- AI。
- Gen。
- 多租户。
- OAuth2 / SSO。
- 运行时能力接口。
- 三档部署。
- Nacos 配置。
- PostgreSQL/MySQL 兼容入口与 PostgreSQL 升级。
- 页面级健康回归。
- 95 发布链路。

暂不实现或暂不验证的能力必须明确标记为未验证、暂缓或未完成，不能从计划和文档中静默删除。

## 5. 正式入口锁定

- README 正式入口：`README.md`。
- 文档正式入口：`docs/`。
- SQL 正式入口：`sql/`。
- 部署正式入口：`deploy/`。
- 规则正式入口：`docs/06-牛马协作总规则.md`。

`.codex/`、`niumma-rules.md`、`AGENTS.md` 是协作资产和入口辅助，不得替代正式文档入口。

## 6. SQL 结构锁定

- `sql/tiers/small/small-init.sql`
- `sql/tiers/small/small-init-mysql.sql`
- `sql/tiers/medium/medium-init.sql`
- `sql/tiers/full/full-init.sql`
- `sql/upgrades/postgres/`

禁止把散装 SQL 放回根目录，禁止绕开 tier 结构新增长期 SQL 入口。
禁止把只有驱动或只有编译结果的改动描述为数据库已支持；必须以对应 tier 的干净实例导入和最小业务回归为准。

## 7. 部署结构锁定

- `deploy/small`
- `deploy/medium`
- `deploy/full`

根目录旧 compose 只允许短期过渡，不得继续作为长期正式入口。

## 8. 95 发布锁定

- 95 代码来源只能是 `/opt/han/repo/Han`。
- 95 部署入口只能是 `/opt/han/deploy/{small,medium,full}`。
- 95 发布只能从 `master` 产出。
- 禁止继续使用 `/opt/han/source/Han-*` 或 `/opt/han/docker` 作为正式入口。

## 9. 进度与手册同步

每完成涉及正式入口的任务，必须同步检查：

- 文档结构变更是否更新 `docs/index.md`。
- SQL 变更是否更新 `sql/README.md`。
- 部署结构变更是否更新 `docs/03-部署手册.md`。
- 95 发布或验证变更是否更新 `docs/04-测试与验收手册.md` 和 `docs/05-运维与95环境手册.md`。
- 规则长期约束是否回写 `docs/06-牛马协作总规则.md`。

## 10. 中断恢复规则

会话中断、上下文压缩或换人接手时：

1. 先读 `AGENTS.md`。
2. 再读 `docs/06-牛马协作总规则.md`。
3. 再读 `niumma-rules.md`。
4. 再读任务命中的正式手册和 `.codex/rules`。
5. 以仓库现状和验证证据继续，不凭聊天印象继续改代码。

## 11. 交付规则

每次交付必须说明：

- 本次改动。
- 修改文件。
- 执行过的验证。
- 未验证项。
- 阻塞项。
- 残留风险。
- 下一步建议。
