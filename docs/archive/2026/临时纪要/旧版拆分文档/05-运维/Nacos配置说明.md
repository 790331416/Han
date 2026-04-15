# Nacos 配置说明

## 正式位置

- 共享模板：
  - `sql/shared/nacos/templates/jobflow-scheduler.yml`
- tier 导入脚本：
  - `sql/tiers/small/nacos/derby-import.sql`
  - `sql/tiers/medium/nacos/derby-import.sql`
  - `sql/tiers/full/nacos/derby-import.sql`

## 原则

- Nacos 导入脚本与 PostgreSQL 初始化脚本分开管理
- tier 专用配置不得散落在 `sql/` 根目录
- `sql/config/nacos/` 已退役，不再作为正式入口
- 95 导入前先备份原始 Derby 数据目录
