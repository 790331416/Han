# Han SQL 入口说明

`sql/` 是 Han 仓库唯一正式 SQL 入口。

## 当前正式结构

- `tiers/`
  - `small/small-init.sql`
  - `small/small-nacos-derby-import.sql`
  - `medium/medium-init.sql`
  - `medium/medium-nacos-derby-import.sql`
  - `full/full-init.sql`
  - `full/full-nacos-derby-import.sql`
- `upgrades/postgres/`
  - PostgreSQL 正式增量升级脚本
- `archive/`
  - 已退役的旧 SQL、旧拆分结构与历史母本

## 使用规则

- 初始化只认 `sql/tiers/<tier>/<tier>-init.sql`
- Nacos 导入只认 `sql/tiers/<tier>/<tier>-nacos-derby-import.sql`
- 增量升级只认 `sql/upgrades/postgres/`
- 不再从 `sql/` 根目录、旧 `postgres/`、旧 `upgrade/`、旧拆分模块目录寻找正式初始化脚本
- PostgreSQL 脚本禁止继续使用 MySQL 写法，例如列内 `COMMENT`、`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`AFTER`、`USE <db>`
- `sys_user` 初始化结构必须包含登录链路依赖的 `pwd_update_time` 与 `pwd_reset_flag`

## 三档说明

- `small`
  - PostgreSQL：`sql/tiers/small/small-init.sql`
  - Nacos：`sql/tiers/small/small-nacos-derby-import.sql`
- `medium`
  - PostgreSQL：`sql/tiers/medium/medium-init.sql`
  - Nacos：`sql/tiers/medium/medium-nacos-derby-import.sql`
- `full`
  - PostgreSQL：`sql/tiers/full/full-init.sql`
  - Nacos：`sql/tiers/full/full-nacos-derby-import.sql`

## 归档说明

- `sql/archive/legacy-root/`
  - 根目录旧散装 SQL
- `sql/archive/postgres-legacy/`
  - 旧 PostgreSQL 母本
- `sql/archive/upgrade-legacy/`
  - 旧升级脚本
- `sql/archive/tier-modular-legacy/`
  - 上一轮按模块拆分的 tier SQL
- `sql/archive/shared-legacy/`
  - 旧共享模板与共享基础脚本
