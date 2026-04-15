# full SQL 说明

`full` 覆盖完整能力环境所需数据库与配置导入。

## 正式入口

- PostgreSQL：`sql/tiers/full/full-init.sql`
- Nacos：`sql/tiers/full/full-nacos-derby-import.sql`

## 包含范围

- `system`
- `job`
- `tenant`
- `workflow`
- `open`
- `file`
- `ai`
- `gen`

## 初始化顺序

1. 导入 `full-init.sql`
2. 导入 `full-nacos-derby-import.sql`

## 说明

- `ai` 与 `gen` 只在 `full` 出现
- `AI Graph` 当前仍按未开发边界管理
- 历史按模块拆分的 SQL 已归档到 `sql/archive/tier-modular-legacy/full/`
