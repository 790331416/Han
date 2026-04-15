# Han 仓库协作入口

进入本仓库的 Codex、自动化代理或其他协作助手，开始任何实现前都必须先遵守 [docs/06-牛马协作总规则.md](docs/06-%E7%89%9B%E9%A9%AC%E5%8D%8F%E4%BD%9C%E6%80%BB%E8%A7%84%E5%88%99.md)。

本仓库的强制执行口径如下：

1. 全程使用中文沟通。
2. 所有新增或重写文档必须使用 UTF-8 编码，禁止乱码。
3. 未经明确授权，不得删除功能、简化已有能力、合并导致行为缩水的业务路径，或删掉边界处理、补偿逻辑、错误分支。
4. 正式入口固定为：
   - `README.md`
   - `docs/`
   - `sql/`
   - `deploy/`
5. 正式规则文档只有一份：`docs/06-牛马协作总规则.md`。
6. SQL 只能使用当前正式结构：
   - `sql/tiers/small/small-init.sql`
   - `sql/tiers/medium/medium-init.sql`
   - `sql/tiers/full/full-init.sql`
   - `sql/upgrades/postgres/`
7. 95 环境只能从 `/opt/han/repo/Han` 和 `/opt/han/deploy/{small,medium,full}` 发布。
8. 任何涉及文档、SQL、部署结构、95 发布的变更，都必须同步更新对应手册和验证记录。
9. 交付时必须说明：
   - 改了什么
   - 验证了什么
   - 还有什么未验证或残留风险

如果本文件与其他零散说明冲突，以 `docs/06-牛马协作总规则.md` 为准。
