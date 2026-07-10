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
- `20260610_aivideo_sound_design_prompt.sql`：AI 短剧剧本/资产/分镜 Prompt 增加角色声线、BGM 和音效规划，前置输出 `soundDesign`、`bgmCue` 和 `sfxCues`
- `20260610_aivideo_shot_sound_cues.sql`：AI 短剧分镜表增加 `bgm_cue`、`sfx_cues`，让资产提取出的 BGM/音效计划可被后期语音和剪辑混音阶段读取
- `20260611_ai_builtin_dict_alignment.sql`：补齐 AI 模型类型、供应商和 Prompt 模板分类的系统字典，保证管理端下拉与列表筛选可从公共字典模块读取
- `20260702_ai_prompt_template_audit_columns.sql`：为 `ai_prompt_template` 补齐 `create_by`/`update_by` 审计列，修复 95 旧库 Prompt 模板列表 500
- `20260702_sys_oper_log_module_alignment.sql`：`sys_oper_log` 旧列名（title/business_type）对齐代码侧（module/oper_type），补 `oper_user_id`，修复操作日志写入失败导致的 0 条留痕
- `20260703_ai_chat_multimodal.sql`：AI 对话多模态升级，`ai_model` 增 `supports_vision` 视觉能力标记、`ai_chat_message` 增 `images` 图片附件列
- `20260703_ai_flow_meta.sql`：AI 编排执行引擎升级，`ai_chat_message` 增 `meta` 扩展元数据列（承载 advanced 工作流节点执行时间线）
- `20260703_file_manage_menu.sql`：系统管理新增「文件管理」菜单与 `file:query`/`file:remove` 按钮权限，配套 han-file `/file/list`、`/file/remove` 管理接口
- `20260703_ai_agent_share_key.sql`：`ai_agent` 增 `share_key` 分享链接 key，配套应用发布公开对话接口与免登录分享页
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

当前 master 同步候选共恢复 28 个关联升级脚本：26 个 `*aivideo*.sql`，以及 2 个公共 AI 字典对齐脚本 `20260611_ai_builtin_dict_alignment.sql`、`20260611_ai_dict_options.sql`。正式执行顺序由升级演练脚本按文件名固定，新增、删除或改名时必须同步本节和两套演练清单。

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
- `sql/upgrades/postgres/20260610_aivideo_sound_design_prompt.sql`：更新 AI 短剧剧本生成、资产提取、分镜提取和分镜视频生成内置 Prompt，要求前置输出角色声线、旁白声线、BGM、音效/环境声规划，并为后期语音、音乐音效和混音成片提供结构化依据。
- `sql/upgrades/postgres/20260610_aivideo_shot_sound_cues.sql`：为 `ai_video_shot` 增加 `bgm_cue` 和 `sfx_cues` 两列，用于保存每个分镜的背景音乐与音效触发计划，供剪辑预检、后期语音和后续混音流程使用。
- `sql/upgrades/postgres/20260521_aivideo_mvp0.sql`：创建 AI 短剧项目、版本、角色、场景、分镜、媒体、任务和审核等 MVP 0 基础表。
- `sql/upgrades/postgres/20260527_aivideo_character_image_workflow.sql`：增加角色图 Prompt 模板位并对齐角色、场景、润色和图像生成模板。
- `sql/upgrades/postgres/20260527_aivideo_shot_video_workflow.sql`：增加分镜视频模板位、候选数量默认值和单分镜视频生成模板。
- `sql/upgrades/postgres/20260609_aivideo_prompt_template_alignment.sql`：对齐 Prompt 审计列、变量类型以及连续性、道具和声音策略模板。
- `sql/upgrades/postgres/20260610_aivideo_model_config_alignment.sql`：增加默认禁用的火山 TTS 与 VOD 剪辑模型配置占位，凭据继续由模型管理受控录入。
- `sql/upgrades/postgres/20260610_aivideo_tts_voice_assets.sql`：增加分镜 TTS 时间、说话人、音色字段和角色稳定声线资产。
- `sql/upgrades/postgres/20260611_ai_builtin_dict_alignment.sql`：补齐 AI 模型类型、供应商和 Prompt 分类公共字典。
- `sql/upgrades/postgres/20260611_ai_dict_options.sql`：把 AI 模型类型和 Prompt 分类选择项统一到 `sys_dict_*`。
- `sql/upgrades/postgres/20260611_aivideo_prop_assets.sql`：增加结构化道具资产和道具锚点提取规则。
- `sql/upgrades/postgres/20260615_aivideo_action_budget_prop_link.sql`：增加动作预算、复杂动作拆镜、道具交接和场景连续性硬规则。
- `sql/upgrades/postgres/20260615_aivideo_tts_prompt_alignment.sql`：对齐数据库、full 初始化和 Java 内置后期语音模板语义。
- `sql/upgrades/postgres/20260623_aivideo_short_script_shot_split.sql`：短祝福、口播和单场景内容默认拆为至少 3 个镜头或画面段落。
