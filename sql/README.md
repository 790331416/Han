# Han SQL 入口说明

`sql/` 是 Han 仓库唯一正式 SQL 入口。

## 目录结构

- `shared/`
  - PostgreSQL 共享基础脚本
  - Nacos 共享模板
- `tiers/`
  - `small`
  - `medium`
  - `full`
- `upgrades/postgres/`
  - PostgreSQL 增量升级脚本
- `archive/`
  - 历史散装 SQL 与旧格式脚本

## 使用规则

- 初始化以 `tiers/<tier>/` 为准
- 升级以 `upgrades/postgres/` 为准
- 不再从 `sql/` 根目录直接寻找正式初始化脚本

## 当前说明

- 当前 PostgreSQL 正式口径优先来源于 `sql/postgres/reinit.sql` 与 `sql/postgres/init.sql`
- 旧的 `han_*.sql`、`xuman_*.sql` 中存在 MySQL 风格脚本，后续作为历史来源逐步归档，不再作为三档 PostgreSQL 正式入口
- 95 Derby Nacos 导入脚本已经进入 `sql/tiers/<tier>/nacos/derby-import.sql`
