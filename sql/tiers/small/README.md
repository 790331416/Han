# small SQL 说明

`small` 只覆盖核心链路所需数据库与配置导入。

## 正式入口

- PostgreSQL：`sql/tiers/small/small-init.sql`
- Nacos：`sql/tiers/small/small-nacos-derby-import.sql`

## 包含范围

- `system`
- `job`

## 初始化顺序

1. 导入 `small-init.sql`
2. 导入 `small-nacos-derby-import.sql`

## 说明

- `small` 不包含 `tenant`、`workflow`、`open`、`file`、`ai`、`gen`
- 历史按模块拆分的 SQL 已归档到 `sql/archive/tier-modular-legacy/small/`
