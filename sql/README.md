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
  - `20260521_aivideo_mvp0.sql`：AI 短剧 MVP 0 表结构增量脚本
- `archive/`
  - 已退役的旧 SQL、旧拆分结构与历史母本

## 使用规则

- 初始化只认 `sql/tiers/<tier>/<tier>-init.sql`
- Nacos 导入只认 `sql/tiers/<tier>/<tier>-nacos-derby-import.sql`
- 增量升级只认 `sql/upgrades/postgres/`
- 不再从 `sql/` 根目录、旧 `postgres/`、旧 `upgrade/`、旧拆分模块目录寻找正式初始化脚本
- PostgreSQL 脚本禁止继续使用 MySQL 写法，例如列内 `COMMENT`、`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`AFTER`、`USE <db>`
- `sys_user` 初始化结构必须包含登录链路依赖的 `pwd_update_time`、`pwd_reset_flag`、`totp_secret` 与 `totp_enabled`
- PostgreSQL 增量升级脚本不得新建 `deleted` 列；软删除列统一使用 `del_flag`
- 修改 `sql/upgrades/postgres/` 后，必须至少执行结构检查；涉及旧库兼容时还要执行升级演练脚本

## 升级演练

95 或其他 Linux/Docker 环境可执行：

```bash
bash deploy/scripts/rehearse-postgres-upgrades.sh
```

该脚本使用临时 PostgreSQL 容器验证两类场景：

- `clean_full`：先导入 `sql/tiers/full/full-init.sql`，再按固定顺序重放 `sql/upgrades/postgres/` 升级脚本，验证当前初始化结构上升级脚本可重复执行。
- `legacy_synthetic`：构造带旧列名和缺列的合成旧库，再执行同一组升级脚本，验证旧库兼容迁移路径。

演练完成后会校验：

- `public` schema 不再残留 `deleted` 列
- `sys_user.pwd_reset_flag`
- `sys_user.totp_enabled`
- `ai_agent.del_flag`
- `sys_menu.sort`

针对真实逻辑备份或 95 当前运行库，可执行备份恢复型演练：

```bash
bash deploy/scripts/rehearse-postgres-backup-upgrades.sh --from-compose-tier full
bash deploy/scripts/rehearse-postgres-backup-upgrades.sh --backup /path/to/backup.sql
```

该脚本只在临时 PostgreSQL 容器中恢复备份并重放 `sql/upgrades/postgres/`，不会写入 `/opt/han/deploy/{small,medium,full}` 的运行库。支持 `.sql`、`.sql.gz`、`.dump`、`.backup`、`.tar` 备份文件。

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
  - AI 短剧：MVP 0 表结构只进入 full tier，表名前缀为 `ai_video_`

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
## AI 短剧 SQL 变更记录

- `sql/upgrades/postgres/20260521_aivideo_mvp1_text.sql`：AI 短剧 MVP 1 文本链路 Prompt 模板种子，和 `sql/tiers/full/full-init.sql` 保持同步。
