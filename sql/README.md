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
- `20260703_file_manage_menu.sql`：文件管理菜单与 `file:query`/`file:remove` 按钮权限（其中 `file:query` 已由 `20260813_file_perms_alignment.sql` 下线，本脚本保留原样以记录历史迁移事实）
- `20260715_sys_dict_type_exact_duplicate_alignment.sql`：软删除完全一致的重复字典类型，保留最小 ID
- `20260720_ai_agent_chat_tuning.sql`：`ai_agent`/`ai_workflow` 对话调优列
- `20260720_wechat_social_login.sql`：微信扫码登录唯一索引、`sys.login.wechatEnabled` 开关与社交解绑权限。**本脚本会物理删除违反新唯一规则的历史社交绑定行**，执行前必须备份；脚本自身会先把待删行写进 `sys_user_social_conflict_backup_20260720`
- `20260812_permission_seed_alignment.sql`：补齐 AI / job / tenant / OSS / 开放平台 / 日志与在线用户的权限点种子，并把日志与监控权限串统一到 `monitor:*` 口径
- `20260812_unique_constraint_del_flag_alignment.sql`：`sys_user_social` 结构兜底；把角色、岗位、字典、参数、客户端上不带 `del_flag` 条件的唯一约束换成部分唯一索引；存在重复数据时只告警跳过，不中断升级链
- `20260813_file_perms_alignment.sql`：文件列表接口的权限串由 `file:query` 改为 `file:list`，与全仓 `:list`/`:query` 约定一致；先给只持有 `file:query` 的角色补授 `file:list`，再下线 `file:query` 菜单，升级前后可见范围不变

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
| `medium` | `sql/tiers/medium/medium-init.sql` | `sql/tiers/medium/medium-init-mysql.sql` | `sql/tiers/medium/medium-nacos-derby-import.sql` | small + tenant、workflow、open、file |
| `full` | `sql/tiers/full/full-init.sql` | `sql/tiers/full/full-init-mysql.sql` | `sql/tiers/full/full-nacos-derby-import.sql` | medium + ai、gen |

PostgreSQL 是默认数据库，MySQL 8.4 为正式可选数据库，三档全新初始化均已提供独立脚本，
且三档都完成了 PostgreSQL 18.1 与 MySQL 8.4.10 的实库对照导入（严格模式零错误、
表/菜单/权限/字典数量逐项一致），证据见兼容手册 §1.1。服务层回归尚未做。
增量升级脚本按数据库分目录：`sql/upgrades/postgres/` 与 `sql/upgrades/mysql/`，
MySQL 通道从 2026-08-11 起算，此前的历史变更已烘焙在 `*-init-mysql.sql` 里，不回港。
口径与边界以 [PostgreSQL/MySQL 兼容与切换手册](../docs/11-PostgreSQL-MySQL兼容与切换手册.md) 为准。

### 菜单与权限点的档位划分

划分遵循四条硬规则，全部由 `scripts/checks/check_sql_layout.py` 的静态门禁保证，
破坏任意一条都会导致门禁失败：

1. **同一档位的 PostgreSQL 与 MySQL 播种菜单逐条相同**（菜单 ID、名称、权限串全等），
   换数据库不会换出另一套菜单。（`check_tier_menu_division`）
2. **逐级追加**：small ⊆ medium ⊆ full，上层只允许在下层基础上增加。（同上）
3. **按钮型菜单的权限串必须有后端接口**：后端没有 `@PreAuthorize("@ss.hasAuthority('x')")`
   或 `@RequiresPermission("x")` 引用它，就是点了没反应的死按钮。（`check_button_perms_have_endpoint`）
4. **种子的 `tenant_id` 两库必须一致**：一边写 1 一边留 NULL，会被租户过滤掉、整页为空。
   （`check_seed_tenant_parity`）
5. **同表种子行数两库必须相等**：漏播单行既不会导入报错，也不会被表数/菜单数这类
   粗粒度对比发现。（`check_seed_row_count_parity`；`sys_menu` 由规则 1 逐条比对，不重复计）

当前实际划分（数量为静态播种的菜单条数，已在真实库导入核对）：

| 档位 | 菜单数 | 相对下一档新增的内容 |
| --- | --- | --- |
| `small` | 65 | 基线：系统管理、系统监控、任务调度 |
| `medium` | 100 | +35：租户管理与套餐配额、工作流、开放平台、文件管理、OSS 配置 |
| `full` | 135 | +35：代码生成，以及 AI 模型/知识库/MCP/智能体/编排/Prompt/Token 统计/对话 |

**只播该档实际部署了模块的菜单。** 判据有两层：

- 该功能依赖的业务表在本档 init 里建没建：`tool:gen:*` 依赖 `gen_table`（只在 full）、
  `tenant:*` 依赖 `sys_tenant`（medium 起）、`ai:*` 依赖 `ai_model`（只在 full）。
  播了模块表不存在的父菜单，升级脚本 `20260812_permission_seed_alignment.sql`
  会顺着父菜单继续补子权限点，最终显示一批点不开的死菜单。
- 后端接口与前端页面是否真的存在。历史上「客户端管理」「系统接口」两个菜单在
  后端 Controller、前端页面、前端路由三处都不存在，「字典导出」「参数导出」两个按钮
  后端也没有对应接口，均已移除。

页面型菜单（`menu_type = 'C'`）的权限串允许只被前端路由使用而没有同名接口。
按钮型菜单（`'F'`）不适用这条豁免，它的唯一作用就是给接口做鉴权。

权限串后缀遵循全仓统一约定（与若依一致）：`:list` 挂列表接口（`GET /list`）
并兼作页面可见性权限，`:query` 挂按主键查详情的接口（`GET /{id}`）。
han-file 曾是唯一例外——列表接口挂的是 `file:query`，菜单里播的却是 `file:list`，
两者对不上且没有详情接口；`20260813_file_perms_alignment.sql` 已把接口改回
`file:list` 并下线 `file:query`，升级时会先给只有 `file:query` 的角色补授
`file:list`，可见范围不变。

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

### AI 短剧（aivideo）升级脚本

以下脚本随 `aivideo-dev` 分支并入，只作用于 full 档的 `ai_video_*` 表与 aivideo Prompt 模板，
不影响 small/medium。执行顺序同样按文件名升序。

- `20260521_aivideo_mvp0.sql`：AI 短剧 MVP 0 表结构增量脚本
- `20260521_aivideo_mvp1_text.sql`：AI 短剧 MVP 1 文本链路 Prompt 模板种子
- `20260526_aivideo_prompt_stream.sql`：AI 短剧润色长 Prompt、Prompt 配置继承与流式生成配套升级
- `20260526_aivideo_mvp2_scene_image.sql`：AI 短剧 MVP 2 单场景纯场景图候选生成配置升级脚本
- `20260527_aivideo_media_preview_access.sql`：AI 短剧受控媒体预览、素材访问策略和候选图默认值清理升级脚本
- `20260527_aivideo_scene_prompt_and_candidate_fill.sql`：AI 短剧场景图默认 Prompt 替换为参考词，并保持默认 2 张候选配置
- `20260529_aivideo_shot_video_continuity.sql`：AI 短剧分镜视频尾帧衔接字段与默认 Prompt 强约束升级脚本
- `20260601_aivideo_shot_video_av_character_scene_continuity.sql`：AI 短剧分镜视频默认 Prompt 补充音画双轨、角色一致性和场景连续性强约束
- `20260601_aivideo_shot_action_budget.sql`：AI 短剧剧本/分镜/视频 Prompt 增加动作预算、动态 5/6/8 秒、构图部位锁定和视频禁用自动配音配套模板
- `20260602_aivideo_character_turnaround_prompt.sql`：AI 短剧角色构建和角色图 Prompt 强制四方向全身转面表，屏蔽头部特写/三视图旧版版式
- `20260602_aivideo_character_turnaround_prompt_sanitize.sql`：AI 短剧角色图模板补充“净化后的角色外观提示词”说明，配合后端净化历史头像/三视图版式词
- `20260602_aivideo_video_ready_reference_prompts.sql`：AI 短剧角色图/场景图 Prompt 切换为 Seedance 视频参考素材友好规则，强制单主体角色锚点、单镜头场景锚点，并补充分镜视频对角色锚定图的使用边界
- `20260603_aivideo_shot_spatial_continuity.sql`：AI 短剧分镜提取 Prompt 增加剧情空间连续性硬约束，禁止广告牌/高处危机后无过渡跳到狗窝、室内等未铺垫地点
- `20260605_aivideo_shot_transition_plan.sql`：AI 短剧分镜增加转场关系、后期拼接组和默认转场效果字段，区分连续镜头与切场镜头
- `20260607_aivideo_audio_track_prompt.sql`：AI 短剧剧本/分镜/视频 Prompt 改为对白、旁白/画外音、心声/心理活动三轨规则，避免把心理画面朗读成配音
- `20260610_aivideo_sound_design_prompt.sql`：AI 短剧剧本/资产/分镜 Prompt 增加角色声线、BGM 和音效规划，前置输出 `soundDesign`、`bgmCue` 和 `sfxCues`
- `20260610_aivideo_shot_sound_cues.sql`：AI 短剧分镜表增加 `bgm_cue`、`sfx_cues`，让资产提取出的 BGM/音效计划可被后期语音和剪辑混音阶段读取
- `20260715_aivideo_admin_menu_alignment.sql`：补齐 AI 短剧任务监管、基础配置菜单及查询/编辑权限，并只关联有效超管角色
