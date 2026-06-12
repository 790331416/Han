-- AI built-in dictionary alignment.
-- This script only inserts missing built-in dictionary rows and does not overwrite user-maintained dictionary data.

BEGIN;

WITH target_tenants AS (
    SELECT DISTINCT tenant_id
    FROM (
        VALUES (0::BIGINT)
        UNION ALL
        SELECT id::BIGINT FROM sys_tenant WHERE COALESCE(del_flag, 0) = 0
        UNION ALL
        SELECT tenant_id::BIGINT FROM sys_tenant WHERE tenant_id IS NOT NULL AND COALESCE(del_flag, 0) = 0
    ) source(tenant_id)
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
    SELECT DISTINCT tenant_id
    FROM (
        VALUES (0::BIGINT)
        UNION ALL
        SELECT id::BIGINT FROM sys_tenant WHERE COALESCE(del_flag, 0) = 0
        UNION ALL
        SELECT tenant_id::BIGINT FROM sys_tenant WHERE tenant_id IS NOT NULL AND COALESCE(del_flag, 0) = 0
    ) source(tenant_id)
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

COMMIT;
