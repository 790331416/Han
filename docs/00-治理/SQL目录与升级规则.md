# SQL目录与升级规则

## 1. 目录结构

- `sql/shared/`：共享模板与共享基础脚本
- `sql/tiers/<tier>/`：按三档分层的正式初始化脚本
- `sql/upgrades/postgres/`：PostgreSQL 增量升级脚本
- `sql/archive/`：历史散装 SQL、旧格式脚本

## 2. tier 规则

每个 tier 固定包含：

- `manifest.md`
- `nacos/derby-import.sql`
- `postgres/<module>/`

## 3. PostgreSQL 模块规则

每个模块目录至少包含：

- `00-schema.sql`
- `10-seed.sql`

按需增加：

- `90-fixup.sql`

## 4. 升级脚本规则

- 升级脚本只允许进入 `sql/upgrades/postgres/`
- 文件名建议使用 `YYYYMMDD_<主题>.sql`
- 升级脚本必须在对应 tier `manifest.md` 中说明适用范围
