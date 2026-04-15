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
  - PostgreSQL 正式增量升级脚本
- `archive/`
  - 已被新结构吸收的历史 SQL 与旧入口

## 使用规则

- 初始化只以 `tiers/<tier>/` 为准
- 升级只以 `upgrades/postgres/` 为准
- Nacos 模板只以 `shared/nacos/templates/` 为准
- 不再从 `sql/` 根目录、`sql/postgres/`、`sql/upgrade/`、`sql/config/` 寻找正式初始化或升级脚本

## 当前口径

- `small / medium / full` 的 PostgreSQL 初始化脚本已经固定在 `sql/tiers/`
- `jobflow-scheduler.yml` 的正式模板位置是 `sql/shared/nacos/templates/jobflow-scheduler.yml`
- `sql/archive/` 中的内容只用于历史追溯，不再作为正式部署入口
