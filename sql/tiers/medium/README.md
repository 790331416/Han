# medium SQL 说明

`medium` 覆盖标准联调环境所需数据库与配置导入。

## 正式入口

- PostgreSQL：`sql/tiers/medium/medium-init.sql`
- Nacos：`sql/tiers/medium/medium-nacos-derby-import.sql`

## 包含范围

- `system`
- `job`
- `tenant`
- `workflow`
- `open`
- `file`

## 初始化顺序

1. 导入 `medium-init.sql`
2. 导入 `medium-nacos-derby-import.sql`

## 说明

- `workflow` 业务扩展表已经收进大 SQL
- Flowable 引擎运行时表仍由引擎自身维护
- 历史按模块拆分的 SQL 已归档到 `sql/archive/tier-modular-legacy/medium/`
