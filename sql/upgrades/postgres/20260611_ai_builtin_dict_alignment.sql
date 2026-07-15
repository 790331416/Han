-- AI built-in dictionary alignment.
-- Adds only missing compatibility columns and built-in rows; user-maintained dictionary data is not overwritten.

BEGIN;

ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS dict_name VARCHAR(100);
ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 0;
ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
ALTER TABLE sys_dict_type ADD COLUMN IF NOT EXISTS del_flag SMALLINT DEFAULT 0;

ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS dict_label VARCHAR(100);
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS dict_value VARCHAR(100);
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS dict_sort INT DEFAULT 0;
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS css_class VARCHAR(100);
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS list_class VARCHAR(100);
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS is_default SMALLINT DEFAULT 0;
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 0;
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS remark VARCHAR(500);
ALTER TABLE sys_dict_data ADD COLUMN IF NOT EXISTS del_flag SMALLINT DEFAULT 0;

CREATE TEMP TABLE IF NOT EXISTS tmp_ai_builtin_target_tenants (
    tenant_id BIGINT PRIMARY KEY
) ON COMMIT DROP;
TRUNCATE tmp_ai_builtin_target_tenants;
INSERT INTO tmp_ai_builtin_target_tenants (tenant_id) VALUES (0) ON CONFLICT DO NOTHING;

DO $$
DECLARE
    v_active_condition TEXT := '';
BEGIN
    IF to_regclass('public.sys_tenant') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'del_flag'
    ) THEN
        v_active_condition := ' AND COALESCE(del_flag, 0) = 0';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'deleted'
    ) THEN
        v_active_condition := ' AND COALESCE(deleted, 0) = 0';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'id'
    ) THEN
        EXECUTE 'INSERT INTO tmp_ai_builtin_target_tenants (tenant_id) SELECT id::BIGINT FROM sys_tenant WHERE id IS NOT NULL'
            || v_active_condition || ' ON CONFLICT DO NOTHING';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'tenant_id'
    ) THEN
        EXECUTE 'INSERT INTO tmp_ai_builtin_target_tenants (tenant_id) SELECT tenant_id::BIGINT FROM sys_tenant WHERE tenant_id IS NOT NULL'
            || v_active_condition || ' ON CONFLICT DO NOTHING';
    END IF;
END $$;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_builtin_target_tenants
),
type_items(dict_name, dict_type, remark, status) AS (
    VALUES
        ('AI模型类型', 'ai_model_type', 'AI模型管理模型类型列表', 0),
        ('AI模型供应商', 'ai_model_provider', 'AI模型管理供应商列表', 0),
        ('AI Prompt模板分类', 'ai_prompt_category', 'AI Prompt模板分类列表', 0)
),
type_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN type_items item
),
missing_types AS (
    SELECT
        candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type) AS rn
    FROM type_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_dict_type target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
    )
),
type_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_type
)
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark)
SELECT
    type_base.max_id + missing_types.rn,
    missing_types.tenant_id,
    missing_types.dict_name,
    missing_types.dict_type,
    missing_types.status,
    missing_types.remark
FROM missing_types
CROSS JOIN type_base;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_builtin_target_tenants
),
data_items(dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) AS (
    VALUES
        ('ai_model_type', '大语言模型', 'LLM', 10, '', 'primary', 1, 0),
        ('ai_model_type', '图片生成模型', 'IMAGE', 20, '', 'success', 0, 0),
        ('ai_model_type', '视频生成模型', 'VIDEO', 30, '', 'warning', 0, 0),
        ('ai_model_type', '视频剪辑合成', 'VIDEO_EDIT', 40, '', 'warning', 0, 0),
        ('ai_model_type', '向量模型', 'EMBEDDING', 50, '', 'info', 0, 0),
        ('ai_model_type', '重排模型', 'RERANK', 60, '', 'info', 0, 0),
        ('ai_model_type', '语音合成', 'TTS', 70, '', 'success', 0, 0),
        ('ai_model_type', '语音识别', 'STT', 80, '', 'info', 0, 0),
        ('ai_model_provider', 'OpenAI', 'openai', 10, '', 'primary', 0, 0),
        ('ai_model_provider', '火山引擎/方舟', 'volcengine', 20, '', 'warning', 1, 0),
        ('ai_model_provider', 'DeepSeek', 'deepseek', 30, '', 'success', 0, 0),
        ('ai_model_provider', '通义千问', 'qwen', 40, '', 'success', 0, 0),
        ('ai_model_provider', '智谱AI', 'zhipu', 50, '', 'primary', 0, 0),
        ('ai_model_provider', '百度千帆', 'baidu', 60, '', 'primary', 0, 0),
        ('ai_model_provider', 'Ollama', 'ollama', 70, '', 'info', 0, 0),
        ('ai_model_provider', 'Azure OpenAI', 'azure', 80, '', 'primary', 0, 0),
        ('ai_model_provider', 'Anthropic', 'anthropic', 90, '', 'info', 0, 0),
        ('ai_model_provider', 'SiliconFlow', 'siliconflow', 100, '', 'success', 0, 0),
        ('ai_model_provider', 'Coze(扣子)', 'coze', 110, '', 'warning', 0, 0),
        ('ai_model_provider', 'DIFY', 'dify', 120, '', 'info', 0, 0),
        ('ai_model_provider', 'FastGPT', 'fastgpt', 130, '', 'info', 0, 0),
        ('ai_prompt_category', '系统提示词', 'system', 10, '', 'primary', 1, 0),
        ('ai_prompt_category', '用户模板', 'user', 20, '', 'success', 0, 0),
        ('ai_prompt_category', '助手模板', 'assistant', 30, '', 'warning', 0, 0),
        ('ai_prompt_category', 'AIVideo 文本润色', 'aivideo_text', 40, '', 'primary', 0, 0),
        ('ai_prompt_category', 'AIVideo 剧本生成', 'aivideo_script', 50, '', 'primary', 0, 0),
        ('ai_prompt_category', 'AIVideo 资产提取', 'aivideo_asset', 60, '', 'success', 0, 0),
        ('ai_prompt_category', 'AIVideo 分镜提取', 'aivideo_storyboard', 70, '', 'warning', 0, 0),
        ('ai_prompt_category', 'AIVideo 图片生成', 'aivideo_image', 80, '', 'success', 0, 0),
        ('ai_prompt_category', 'AIVideo 视频生成', 'aivideo_video', 90, '', 'warning', 0, 0),
        ('ai_prompt_category', 'AIVideo 语音合成', 'aivideo_tts', 100, '', 'info', 0, 0)
),
data_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN data_items item
),
missing_data AS (
    SELECT
        candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type, candidate.dict_sort, candidate.dict_value) AS rn
    FROM data_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_dict_data target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
          AND target.dict_value = candidate.dict_value
    )
),
data_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_data
)
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT
    data_base.max_id + missing_data.rn,
    missing_data.tenant_id,
    missing_data.dict_type,
    missing_data.dict_label,
    missing_data.dict_value,
    missing_data.dict_sort,
    missing_data.css_class,
    missing_data.list_class,
    missing_data.is_default,
    missing_data.status
FROM missing_data
CROSS JOIN data_base;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_builtin_target_tenants
),
type_items(dict_name, dict_type, remark, status) AS (
    VALUES
        ('通用启停状态', 'sys_normal_disable', '系统通用启停状态字典', 0),
        ('AI知识库类型', 'ai_kb_type', 'AI知识库类型列表', 0),
        ('AI MCP传输类型', 'ai_mcp_transport_type', 'AI MCP 传输类型列表', 0),
        ('AI工作流类型', 'ai_workflow_type', 'AI工作流类型列表', 0),
        ('AI知识库索引状态', 'ai_knowledge_index_status', 'AI知识库索引状态列表', 0),
        ('AIVideo 项目阶段', 'aivideo_project_stage', 'AI短剧项目阶段列表', 0),
        ('AIVideo 项目状态', 'aivideo_project_status', 'AI短剧项目状态列表', 0),
        ('AIVideo 任务状态', 'aivideo_task_status', 'AI短剧任务状态列表', 0),
        ('AIVideo 画幅', 'aivideo_ratio', 'AI短剧项目画幅列表', 0),
        ('AIVideo 清晰度', 'aivideo_resolution', 'AI短剧项目清晰度列表', 0),
        ('AIVideo 视觉风格', 'aivideo_visual_style', 'AI短剧视觉风格列表', 0),
        ('AIVideo 生成策略', 'aivideo_generation_strategy', 'AI短剧视频生成策略列表', 0),
        ('AIVideo 声音模式', 'aivideo_audio_mode', 'AI短剧声音模式列表', 0),
        ('AIVideo 字幕模式', 'aivideo_subtitle_mode', 'AI短剧字幕模式列表', 0),
        ('AIVideo 参考素材策略', 'aivideo_reference_strategy', 'AI短剧参考素材策略列表', 0),
        ('AIVideo 动作强度', 'aivideo_action_intensity', 'AI短剧动作强度列表', 0),
        ('AIVideo 连续性强度', 'aivideo_continuity_level', 'AI短剧连续性强度列表', 0),
        ('AIVideo 多角色策略', 'aivideo_multi_role_strategy', 'AI短剧多角色策略列表', 0),
        ('AIVideo 角色造型类型', 'aivideo_character_design_type', 'AI短剧角色造型类型列表', 0),
        ('AIVideo 素材访问策略', 'aivideo_media_access_policy', 'AI短剧素材访问策略列表', 0)
),
type_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN type_items item
),
missing_types AS (
    SELECT
        candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type) AS rn
    FROM type_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_dict_type target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
    )
),
type_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_type
)
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark)
SELECT
    type_base.max_id + missing_types.rn,
    missing_types.tenant_id,
    missing_types.dict_name,
    missing_types.dict_type,
    missing_types.status,
    missing_types.remark
FROM missing_types
CROSS JOIN type_base;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_builtin_target_tenants
),
data_items(dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) AS (
    VALUES
        ('sys_normal_disable', '正常', '0', 1, '', 'primary', 1, 0),
        ('sys_normal_disable', '停用', '1', 2, '', 'danger', 0, 0),
        ('ai_kb_type', '通用知识库', 'general', 10, '', 'primary', 1, 0),
        ('ai_kb_type', 'QA问答库', 'qa', 20, '', 'success', 0, 0),
        ('ai_kb_type', '网页爬取', 'web', 30, '', 'warning', 0, 0),
        ('ai_mcp_transport_type', 'SSE', 'sse', 10, '', 'primary', 1, 0),
        ('ai_mcp_transport_type', 'Streamable HTTP', 'streamable_http', 20, '', 'success', 0, 0),
        ('ai_mcp_transport_type', 'Stdio', 'stdio', 30, '', 'info', 0, 0),
        ('ai_workflow_type', '简单对话', 'simple', 10, '', 'primary', 1, 0),
        ('ai_workflow_type', '高级编排', 'advanced', 20, '', 'success', 0, 0),
        ('ai_knowledge_index_status', '待处理', 'pending', 10, '', 'info', 0, 0),
        ('ai_knowledge_index_status', '索引中', 'indexing', 20, '', 'warning', 0, 0),
        ('ai_knowledge_index_status', '已完成', 'completed', 30, '', 'success', 0, 0),
        ('ai_knowledge_index_status', '失败', 'failed', 40, '', 'danger', 0, 0),
        ('aivideo_project_stage', '草稿', 'DRAFT', 10, '', 'info', 0, 0),
        ('aivideo_project_stage', '原文已保存', 'DOCUMENT_SAVED', 20, '', 'primary', 0, 0),
        ('aivideo_project_stage', '文档已确认', 'DOCUMENT_PARSED', 30, '', 'primary', 0, 0),
        ('aivideo_project_stage', '润色已确认', 'POLISH_CONFIRMED', 40, '', 'success', 0, 0),
        ('aivideo_project_stage', '剧本已确认', 'SCRIPT_CONFIRMED', 50, '', 'success', 0, 0),
        ('aivideo_project_stage', '资产已确认', 'ASSET_CONFIRMED', 60, '', 'success', 0, 0),
        ('aivideo_project_stage', '视频生成中', 'VIDEO_GENERATING', 70, '', 'warning', 0, 0),
        ('aivideo_project_stage', '视频已确认', 'VIDEO_CONFIRMED', 80, '', 'success', 0, 0),
        ('aivideo_project_status', '草稿', 'DRAFT', 10, '', 'info', 0, 0),
        ('aivideo_project_status', '进行中', 'RUNNING', 20, '', 'warning', 0, 0),
        ('aivideo_project_status', '暂停', 'PAUSED', 30, '', 'info', 0, 0),
        ('aivideo_project_status', '已完成', 'FINISHED', 40, '', 'success', 0, 0),
        ('aivideo_project_status', '已归档', 'ARCHIVED', 50, '', 'danger', 0, 0),
        ('aivideo_task_status', '待执行', 'PENDING', 10, '', 'info', 0, 0),
        ('aivideo_task_status', '执行中', 'RUNNING', 20, '', 'warning', 0, 0),
        ('aivideo_task_status', '成功', 'SUCCESS', 30, '', 'success', 0, 0),
        ('aivideo_task_status', '失败', 'FAILED', 40, '', 'danger', 0, 0),
        ('aivideo_task_status', '已取消', 'CANCELED', 50, '', 'info', 0, 0),
        ('aivideo_ratio', '9:16', '9:16', 10, '', 'primary', 1, 0),
        ('aivideo_ratio', '16:9', '16:9', 20, '', 'success', 0, 0),
        ('aivideo_ratio', '1:1', '1:1', 30, '', 'info', 0, 0),
        ('aivideo_ratio', '4:3', '4:3', 40, '', 'warning', 0, 0),
        ('aivideo_resolution', '720p', '720p', 10, '', 'primary', 1, 0),
        ('aivideo_resolution', '1080p', '1080p', 20, '', 'success', 0, 0),
        ('aivideo_resolution', '2K', '2K', 30, '', 'warning', 0, 0),
        ('aivideo_visual_style', '写实电影感', '写实电影感', 10, '', 'primary', 0, 0),
        ('aivideo_visual_style', '3D 国漫 CG', '3D 国漫 CG', 20, '', 'success', 0, 0),
        ('aivideo_visual_style', '2D 日漫', '2D 日漫', 30, '', 'warning', 0, 0),
        ('aivideo_visual_style', '复古胶片', '复古胶片', 40, '', 'info', 0, 0),
        ('aivideo_visual_style', '赛博朋克', '赛博朋克', 50, '', 'danger', 0, 0),
        ('aivideo_visual_style', '童话绘本', '童话绘本', 60, '', 'success', 0, 0),
        ('aivideo_visual_style', '国风水墨', '国风水墨', 70, '', 'primary', 0, 0),
        ('aivideo_generation_strategy', '自动', 'AUTO', 10, '', 'primary', 1, 0),
        ('aivideo_generation_strategy', '视频延长', 'VIDEO_EXTEND', 20, '', 'warning', 0, 0),
        ('aivideo_generation_strategy', '分段拼接', 'SEGMENT_STITCH', 30, '', 'success', 0, 0),
        ('aivideo_generation_strategy', '轨道补齐', 'TRACK_FILL', 40, '', 'info', 0, 0),
        ('aivideo_audio_mode', '静音', 'SILENT', 10, '', 'info', 0, 0),
        ('aivideo_audio_mode', '原生有声', 'NATIVE_AUDIO', 20, '', 'success', 0, 0),
        ('aivideo_audio_mode', '参考音频有声', 'REFERENCE_AUDIO', 30, '', 'warning', 0, 0),
        ('aivideo_audio_mode', '后期 TTS', 'POST_TTS', 40, '', 'primary', 0, 0),
        ('aivideo_subtitle_mode', '无字幕', 'NONE', 10, '', 'info', 0, 0),
        ('aivideo_subtitle_mode', '底部字幕', 'BOTTOM', 20, '', 'primary', 0, 0),
        ('aivideo_subtitle_mode', '气泡台词', 'BUBBLE', 30, '', 'success', 0, 0),
        ('aivideo_subtitle_mode', '标题文字', 'TITLE', 40, '', 'warning', 0, 0),
        ('aivideo_reference_strategy', '角色锚定', 'CHARACTER_ANCHOR', 10, '', 'primary', 0, 0),
        ('aivideo_reference_strategy', '场景定调', 'SCENE_TONE', 20, '', 'success', 0, 0),
        ('aivideo_reference_strategy', '运镜参考', 'CAMERA_REFERENCE', 30, '', 'warning', 0, 0),
        ('aivideo_reference_strategy', '动作参考', 'ACTION_REFERENCE', 40, '', 'info', 0, 0),
        ('aivideo_reference_strategy', '音频参考', 'AUDIO_REFERENCE', 50, '', 'warning', 0, 0),
        ('aivideo_reference_strategy', '角色 + 场景', 'CHARACTER_SCENE', 60, '', 'primary', 1, 0),
        ('aivideo_action_intensity', '低缓动作', 'LOW', 10, '', 'info', 0, 0),
        ('aivideo_action_intensity', '普通动作', 'NORMAL', 20, '', 'primary', 1, 0),
        ('aivideo_action_intensity', '强动作', 'STRONG', 30, '', 'warning', 0, 0),
        ('aivideo_continuity_level', '普通', 'NORMAL', 10, '', 'info', 0, 0),
        ('aivideo_continuity_level', '严格', 'STRICT', 20, '', 'primary', 1, 0),
        ('aivideo_continuity_level', '极严格', 'ULTRA_STRICT', 30, '', 'warning', 0, 0),
        ('aivideo_multi_role_strategy', '单角色优先', 'SINGLE_FIRST', 10, '', 'primary', 1, 0),
        ('aivideo_multi_role_strategy', '多角色允许', 'MULTI_ALLOWED', 20, '', 'success', 0, 0),
        ('aivideo_multi_role_strategy', '超过 4 人自动拆镜', 'SPLIT_OVER_FOUR', 30, '', 'warning', 0, 0),
        ('aivideo_character_design_type', '自动', 'AUTO', 10, '', 'info', 1, 0),
        ('aivideo_character_design_type', '写实自然比例', 'REALISTIC_NATURAL', 20, '', 'primary', 0, 0),
        ('aivideo_character_design_type', '半写实卡通', 'SEMI_REAL_CARTOON', 30, '', 'success', 0, 0),
        ('aivideo_character_design_type', '3D动漫/国漫CG', 'THREE_D_ANIME_CG', 40, '', 'warning', 0, 0),
        ('aivideo_character_design_type', '2D动漫/日漫', 'TWO_D_ANIME', 50, '', 'warning', 0, 0),
        ('aivideo_character_design_type', 'Q版萌系全身', 'CHIBI_FULL_BODY', 60, '', 'success', 0, 0),
        ('aivideo_character_design_type', '低龄儿童绘本', 'CHILDREN_PICTURE_BOOK', 70, '', 'info', 0, 0),
        ('aivideo_character_design_type', '动物本体萌化', 'ANIMAL_BODY_CUTE', 80, '', 'success', 0, 0),
        ('aivideo_character_design_type', '拟人化角色', 'ANTHROPOMORPHIC', 90, '', 'primary', 0, 0),
        ('aivideo_character_design_type', '怪物/夸张反派', 'MONSTER_VILLAIN', 100, '', 'danger', 0, 0),
        ('aivideo_media_access_policy', '登录可见', 'PRIVATE', 10, '', 'info', 1, 0),
        ('aivideo_media_access_policy', '公开可见', 'PUBLIC', 20, '', 'success', 0, 0)
  ),
data_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN data_items item
),
missing_data AS (
    SELECT
        candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type, candidate.dict_sort, candidate.dict_value) AS rn
    FROM data_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_dict_data target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
          AND target.dict_value = candidate.dict_value
    )
),
data_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_data
)
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT
    data_base.max_id + missing_data.rn,
    missing_data.tenant_id,
    missing_data.dict_type,
    missing_data.dict_label,
    missing_data.dict_value,
    missing_data.dict_sort,
    missing_data.css_class,
    missing_data.list_class,
    missing_data.is_default,
    missing_data.status
FROM missing_data
CROSS JOIN data_base;

COMMIT;
