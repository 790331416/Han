# Han SQL 入口说明

`sql/` 是 Han 仓库唯一正式 SQL 入口。

## 当前正式结构

- `tiers/`
  - `small/small-init.sql`
  - `small/small-init-mysql.sql`
  - `small/small-nacos-derby-import.sql`
  - `medium/medium-init.sql`
  - `medium/medium-nacos-derby-import.sql`
  - `full/full-init.sql`
  - `full/full-nacos-derby-import.sql`
- `upgrades/postgres/`
  - PostgreSQL 正式增量升级脚本，清单见下方「升级脚本清单」
- `archive/`
  - 已退役的旧 SQL、旧拆分结构与历史母本

## 使用规则

- PostgreSQL 初始化只认 `sql/tiers/<tier>/<tier>-init.sql`
- MySQL 初始化当前只认 `sql/tiers/small/small-init-mysql.sql`
- Nacos 导入只认 `sql/tiers/<tier>/<tier>-nacos-derby-import.sql`
- PostgreSQL 增量升级只认 `sql/upgrades/postgres/`；MySQL 当前仅支持 clean small 初始化，暂无存量升级入口
- 不再从 `sql/` 根目录、旧 `postgres/`、旧 `upgrade/`、旧拆分模块目录寻找正式初始化脚本
- PostgreSQL 脚本禁止继续使用 MySQL 写法，例如列内 `COMMENT`、`AUTO_INCREMENT`、`ON UPDATE CURRENT_TIMESTAMP`、`AFTER`、`USE <db>`
- `sys_user` 初始化结构必须包含登录链路依赖的 `pwd_update_time`、`pwd_reset_flag`、`totp_secret` 与 `totp_enabled`
- PostgreSQL 增量升级脚本不得新建 `deleted` 列；软删除列统一使用 `del_flag`
- 新增升级脚本必须幂等（`IF NOT EXISTS` / `ON CONFLICT DO NOTHING` / `WHERE NOT EXISTS`），并同步登记进
  `deploy/scripts/rehearse-postgres-upgrades.sh` 与 `deploy/scripts/rehearse-postgres-backup-upgrades.sh`
  的 `UPGRADE_FILES` 数组，否则 `scripts/checks/check_sql_layout.py` 会报未登记
- 修改 `sql/upgrades/postgres/` 后，必须至少执行结构检查；涉及旧库兼容时还要执行升级演练脚本

## Nacos 配置正文的落位

根目录 `nacos/` 目录当前是空的，**各服务的 Nacos 配置正文内嵌在
`sql/tiers/<tier>/<tier>-nacos-derby-import.sql` 的 `nacos.config_info.content` 字段里**。
要改某个服务的 Nacos 运行配置（端口、数据源、上传大小、超时等），改的就是这几个文件。

- 每档覆盖的配置：small 5 个、medium 9 个、full 11 个，
  含 `application-shared.yml` 与各服务的 `han-<service>.yml`
- 配置优先级：模块自带的 `application.yml` / `application-docker.yml` **高于** 通过
  `spring.config.import: optional:nacos:han-<service>.yml` 导入的 Nacos 配置。
  也就是说，只有模块本地没写这个键时，在这里补的值才会生效
- 改完必须确认 YAML 缩进正确：正文是 SQL 单引号字符串，缩进错了不会报 SQL 错，
  只会在服务启动时静默丢配置

## 初始化与升级脚本的一致性约定

三档 `*-init.sql` 是全新部署的唯一结构来源，`upgrades/postgres/` 只服务存量库。
两侧必须表达同一个最终结构，否则会出现「老环境正常、新装环境报错」这一类只有新部署才踩的坑。

- 升级脚本新建的表和列，必须同步补进对应档位的 `*-init.sql`
- 升级脚本修正过的约束（例如带 `WHERE del_flag = 0` 的部分唯一索引），必须同步回 `*-init.sql`
- 升级脚本播下的菜单与权限点，必须同步补进 `*-init.sql`
- 菜单 ID 只有一套：`*-init.sql` 与 `phase9_base_menu_backfill.sql` 共用同一批编号
  （目录 1-7 / 500，页面菜单 100-110、200-204、210-211、300-301、310-313、400-402、410、510-517，
  按钮权限 1001-1092 与 1100 段）。新增菜单不得另起一套编号

## 权限标识口径

同一功能的权限串在「后端 `@PreAuthorize` / `sys_menu.perms` / 前端路由 `meta.permission`」三处必须一致，
**以后端注解为准**。历史上日志与监控存在多套写法，现已统一为：

| 功能 | 统一后的权限串 |
| --- | --- |
| 在线用户 | `monitor:online:list`、`monitor:online:forceLogout` |
| 操作日志 | `monitor:operlog:list`、`monitor:operlog:export`、`monitor:operlog:remove` |
| 登录日志 | `monitor:loginlog:list`、`monitor:loginlog:export`、`monitor:loginlog:remove` |
| 缓存监控 | `monitor:cache:list` |
| 服务监控 | `monitor:server:list` |

已废弃的写法：`system:operlog:list`、`system:loginlog:list`、`system:monitor:server`、
`system:monitor:cache`、`monitor:logininfor:*`。

## 升级脚本清单

执行顺序以 `deploy/scripts/rehearse-postgres-upgrades.sh` 的 `UPGRADE_FILES` 数组为准，
**不得按文件名字典序执行**（依赖顺序与字典序不同，例如 `phase1_tenant.sql` 必须早于所有依赖 `tenant_id` 的脚本）。

- `20260415_upgrade_phase1_legacy.sql`：旧库基础表结构补齐与列名归一
- `phase1_tenant.sql`：补 `tenant_id` 列、回填平台租户 1 并建租户过滤索引
- `20260415_system_del_flag_alignment.sql`：`deleted` 列统一改名为 `del_flag`
- `phase3_security.sql`：安全相关列与索引补齐
- `phase4_management.sql`：管理端相关表补齐
- `phase5_unique_constraint.sql`：唯一约束改为带 `WHERE del_flag = 0` 的部分唯一索引，支持多租户同名与软删后重建
- `phase6_notice_center.sql`：通知中心表结构
- `phase7_login_log_alignment.sql`：登录日志旧列名对齐
- `phase8_prompt_template_alignment.sql`：`ai_prompt_template` 历史半成品表补列与内置模板补齐；同时把列宽统一到权威口径（`template_name` 200 / `category` 30 / `description` 1000 / `variables` TEXT）
- `phase9_base_menu_backfill.sql`：为存量库回填基线菜单与超管授权，按 `perms` 语义键去重
- `phase10_sys_dept_leader_id_compat.sql`：`sys_dept.leader_id` 兼容
- `20260415_upgrade_phase2_ai_legacy.sql`：AI 模块旧库表结构与内置 Prompt 模板
- `20260415_ai_agent_backfill.sql`：`ai_agent` 表兜底建表与列补齐
- `20260415_gen_table_migration.sql`：代码生成器元数据表迁移
- `20260415_gen_tenant_alignment.sql`：代码生成器表租户列对齐
- `20260415_job_log_tenant_alignment.sql`：`sys_job_log` 租户列对齐
- `jobflow_v1_trace_id.sql`：`sys_job_log.trace_id`
- `20260415_ai_chat_message_tenant_alignment.sql`：`ai_chat_message` 租户列与索引
- `20260415_ip_location_migration.sql`：IP 归属地列
- `20260415_password_policy_migration.sql`：密码策略列
- `20260415_social_login_migration.sql`：`sys_user_social` 社交登录绑定表
- `20260415_tenant_del_flag_alignment.sql`：租户相关表软删列对齐
- `20260415_totp_2fa_migration.sql`：TOTP 双因子列
- `20260415_system_login_log_message_alignment.sql`：`sys_login_log.message` 列名对齐
- `20260415_system_post_sort_alignment.sql`：`sys_post.sort` 改名为 `post_sort`，与 `SysPostPo.postSort` 对齐
- `20260612_ai_generic_dict_alignment.sql`：通用 AI 字典对齐（明确排除业务字典）
- `20260702_ai_prompt_template_audit_columns.sql`：为 `ai_prompt_template` 补 `create_by`/`update_by` 审计列
- `20260702_sys_oper_log_module_alignment.sql`：`sys_oper_log` 旧列名对齐为 `module`/`oper_type`，补 `oper_user_id`
- `20260703_ai_agent_share_key.sql`：`ai_agent.share_key` 分享链接 key
- `20260703_ai_chat_multimodal.sql`：`ai_model.supports_vision`、`ai_chat_message.images`
- `20260703_ai_flow_meta.sql`：`ai_chat_message.meta` 工作流节点执行时间线
- `20260703_file_manage_menu.sql`：文件管理菜单与 `file:query`/`file:remove` 按钮权限
- `20260715_sys_dict_type_exact_duplicate_alignment.sql`：软删除完全一致的重复字典类型，保留最小 ID
- `20260720_ai_agent_chat_tuning.sql`：`ai_agent`/`ai_workflow` 对话调优列
- `20260720_wechat_social_login.sql`：微信扫码登录唯一索引、`sys.login.wechatEnabled` 开关与社交解绑权限。**本脚本会物理删除违反新唯一规则的历史社交绑定行**，执行前必须备份；脚本自身会先把待删行写进 `sys_user_social_conflict_backup_20260720`
- `20260812_permission_seed_alignment.sql`：补齐 AI / job / tenant / OSS / 开放平台 / 日志与在线用户的权限点种子，并把日志与监控权限串统一到 `monitor:*` 口径
- `20260812_unique_constraint_del_flag_alignment.sql`：`sys_user_social` 结构兜底；把角色、岗位、字典、参数、客户端上不带 `del_flag` 条件的唯一约束换成部分唯一索引；存在重复数据时只告警跳过，不中断升级链

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

| 档位 | PostgreSQL 初始化 SQL | MySQL 8.4 初始化 SQL | Nacos 导入 SQL | 覆盖模块 |
| --- | --- | --- | --- | --- |
| `small` | `sql/tiers/small/small-init.sql` | `sql/tiers/small/small-init-mysql.sql` | `sql/tiers/small/small-nacos-derby-import.sql` | gateway、auth、system、job |
| `medium` | `sql/tiers/medium/medium-init.sql` | 暂未提供 | `sql/tiers/medium/medium-nacos-derby-import.sql` | small + tenant、workflow、open、file |
| `full` | `sql/tiers/full/full-init.sql` | 暂未提供 | `sql/tiers/full/full-nacos-derby-import.sql` | medium + ai、gen |

PostgreSQL 是默认数据库，MySQL 8.4 为正式可选数据库但仅开放 small 档全新初始化，
口径与边界以 [PostgreSQL/MySQL 兼容与切换手册](../docs/11-PostgreSQL-MySQL兼容与切换手册.md) 为准。

菜单与权限点按档位裁剪：small 只播系统、监控与任务调度；medium 追加 OSS 配置、工作流、开放平台与租户配额；
AI 相关菜单与权限点只进 full。

## 归档说明

- `sql/archive/legacy-root/`
  - 根目录旧散装 SQL
- `sql/archive/postgres-legacy/`
  - 旧 PostgreSQL 母本
- `sql/archive/upgrade-legacy/`
  - 旧升级脚本。其中 10 个文件与 `sql/upgrades/postgres/` 同名，且有 5 个内容已经分叉，
    排障时请以 `sql/upgrades/postgres/` 下的现役版本为准
- `sql/archive/tier-modular-legacy/`
  - 上一轮按模块拆分的 tier SQL
- `sql/archive/shared-legacy/`
  - 旧共享模板与共享基础脚本
