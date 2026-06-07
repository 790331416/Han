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
- `ai_video_project`
- `aivideo_text` 默认 Prompt 模板

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
  - AI 短剧：MVP 0 表结构、MVP 1 默认 Prompt 模板和 `han-aivideo.yml` 运行配置只进入 full tier，表名前缀为 `ai_video_`

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
- `sql/upgrades/postgres/20260526_aivideo_prompt_stream.sql`：AI 短剧润色长 Prompt 入库、项目/全局 Prompt 模板 ID 补齐，并配合润色流式生成链路。
- `sql/upgrades/postgres/20260526_aivideo_mvp2_scene_image.sql`：AI 短剧 MVP 2 场景图 Prompt 模板、`scene_image_prompt_template_id` 和默认候选图数量升级脚本，和 `sql/tiers/full/full-init.sql` 保持同步。
- `sql/upgrades/postgres/20260527_aivideo_media_preview_access.sql`：新增 `media_access_policy`，把历史场景图文件地址归一为受控 `/file/public/...` 路径，并把旧项目候选图数量 3 清理为默认 2。
- `sql/upgrades/postgres/20260527_aivideo_scene_prompt_and_candidate_fill.sql`：更新 `AI短剧场景图生成` 内置模板为 BOSS 提供的“电影级纯净场景设计专家”默认词，并确保全局配置继续指向默认 2 张候选图。
- `sql/upgrades/postgres/20260529_aivideo_shot_video_continuity.sql`：新增 `ai_video_shot.tail_frame_media_id`，更新分镜视频默认 Prompt 为尾帧衔接强约束模板，下一分镜可继承上一分镜尾帧参考图。
- `sql/upgrades/postgres/20260601_aivideo_shot_video_av_character_scene_continuity.sql`：更新分镜视频默认 Prompt，强制区分对白与旁白，禁止视频阶段新增或改变配音，并把角色完整外观锚点和场景背景连续性写入模板变量。
- `sql/upgrades/postgres/20260601_aivideo_shot_action_budget.sql`：更新剧本、分镜和分镜视频模板，引入动作预算、动态 5/6/8 秒、强动作拆镜、目标部位可见、发光部位锁定，并归一历史项目和分镜秒数。
- `sql/upgrades/postgres/20260602_aivideo_character_turnaround_prompt.sql`：更新角色构建和角色图默认模板，把角色图硬锁为正面、左侧面、右侧面、背面四方向全身转面表，并禁止头部特写/半身/三视图旧版版式替代。
- `sql/upgrades/postgres/20260602_aivideo_character_turnaround_prompt_sanitize.sql`：更新角色图模板说明为“净化后的角色外观提示词”，配合后端在角色图和分镜视频生成前净化历史头像/三视图版式词。
- `sql/upgrades/postgres/20260602_aivideo_video_ready_reference_prompts.sql`：把角色图从四方向设定表切换为单主体视频角色锚定图，把场景图强化为可供 Seedance 首帧/环境锚定的单镜头纯场景图，并同步资产提取/角色构建模板和分镜视频角色锚定图使用边界。
- `sql/upgrades/postgres/20260603_aivideo_shot_spatial_continuity.sql`：更新分镜提取默认模板，增加剧情空间连续性硬约束，要求相邻分镜承接主体位置、危险目标和结尾状态，并禁止无铺垫跳转到狗窝、室内、窝口等新地点。
- `sql/upgrades/postgres/20260605_aivideo_shot_transition_plan.sql`：新增 `transition_before_type`、`transition_before_desc`、`transition_effect`、`stitch_group_no`，让分镜表显式显示开场/连续/切场，并为后期视频剪辑 API 拼接和转场提供结构化计划。
- `sql/upgrades/postgres/20260607_aivideo_audio_track_prompt.sql`：更新 AI 短剧剧本生成、分镜提取和分镜视频生成内置 Prompt，把声音规则拆成“对白可口型、旁白/画外音可发声但不口型、心声/心理活动默认不朗读”，并要求低声报数/耳语/读出等写入 `dialogue`。
