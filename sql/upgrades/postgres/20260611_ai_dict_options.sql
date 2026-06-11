-- AI model and Prompt category dictionaries.
-- Source of truth for AI model type / Prompt category selectors is sys_dict_*.

BEGIN;

INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark)
SELECT next_id, 'AI模型类型', 'ai_model_type', 0, 'AI模型管理模型类型列表'
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM sys_dict_type) seq
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_model_type'
);

INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark)
SELECT next_id, 'AI Prompt模板分类', 'ai_prompt_category', 0, 'AI Prompt模板分类列表'
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM sys_dict_type) seq
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_prompt_category'
);

INSERT INTO sys_dict_type (id, dict_name, dict_type, status, remark)
SELECT next_id, 'AI模型供应商', 'ai_model_provider', 0, 'AI模型管理供应商列表'
FROM (SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM sys_dict_type) seq
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_model_provider'
);

WITH items(dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status) AS (
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
missing AS (
    SELECT
        item.*,
        ROW_NUMBER() OVER (ORDER BY item.dict_type, item.dict_sort, item.dict_value) AS rn
    FROM items item
    WHERE NOT EXISTS (
        SELECT 1
        FROM sys_dict_data data
        WHERE data.dict_type = item.dict_type
          AND data.dict_value = item.dict_value
    )
),
base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_data
)
INSERT INTO sys_dict_data (id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT
    base.max_id + missing.rn,
    missing.dict_type,
    missing.dict_label,
    missing.dict_value,
    missing.dict_sort,
    missing.css_class,
    missing.list_class,
    missing.is_default,
    missing.status
FROM missing
CROSS JOIN base;

UPDATE sys_dict_type target
SET dict_name = item.dict_name,
    status = item.status,
    remark = item.remark
FROM (
    VALUES
        ('ai_model_type', 'AI模型类型', 0, 'AI模型管理模型类型列表'),
        ('ai_model_provider', 'AI模型供应商', 0, 'AI模型管理供应商列表'),
        ('ai_prompt_category', 'AI Prompt模板分类', 0, 'AI Prompt模板分类列表')
) AS item(dict_type, dict_name, status, remark)
WHERE target.dict_type = item.dict_type;

UPDATE sys_dict_data target
SET dict_label = item.dict_label,
    dict_sort = item.dict_sort,
    css_class = item.css_class,
    list_class = item.list_class,
    is_default = item.is_default,
    status = item.status
FROM (
    VALUES
        ('ai_model_type', 'LLM', '大语言模型', 10, '', 'primary', 1, 0),
        ('ai_model_type', 'IMAGE', '图片生成模型', 20, '', 'success', 0, 0),
        ('ai_model_type', 'VIDEO', '视频生成模型', 30, '', 'warning', 0, 0),
        ('ai_model_type', 'VIDEO_EDIT', '视频剪辑合成', 40, '', 'warning', 0, 0),
        ('ai_model_type', 'EMBEDDING', '向量模型', 50, '', 'info', 0, 0),
        ('ai_model_type', 'RERANK', '重排模型', 60, '', 'info', 0, 0),
        ('ai_model_type', 'TTS', '语音合成', 70, '', 'success', 0, 0),
        ('ai_model_type', 'STT', '语音识别', 80, '', 'info', 0, 0),
        ('ai_model_provider', 'openai', 'OpenAI', 10, '', 'primary', 0, 0),
        ('ai_model_provider', 'volcengine', '火山引擎/方舟', 20, '', 'warning', 1, 0),
        ('ai_model_provider', 'deepseek', 'DeepSeek', 30, '', 'success', 0, 0),
        ('ai_model_provider', 'qwen', '通义千问', 40, '', 'success', 0, 0),
        ('ai_model_provider', 'zhipu', '智谱AI', 50, '', 'primary', 0, 0),
        ('ai_model_provider', 'baidu', '百度千帆', 60, '', 'primary', 0, 0),
        ('ai_model_provider', 'ollama', 'Ollama', 70, '', 'info', 0, 0),
        ('ai_model_provider', 'azure', 'Azure OpenAI', 80, '', 'primary', 0, 0),
        ('ai_model_provider', 'anthropic', 'Anthropic', 90, '', 'info', 0, 0),
        ('ai_model_provider', 'siliconflow', 'SiliconFlow', 100, '', 'success', 0, 0),
        ('ai_model_provider', 'coze', 'Coze(扣子)', 110, '', 'warning', 0, 0),
        ('ai_model_provider', 'dify', 'DIFY', 120, '', 'info', 0, 0),
        ('ai_model_provider', 'fastgpt', 'FastGPT', 130, '', 'info', 0, 0),
        ('ai_prompt_category', 'system', '系统提示词', 10, '', 'primary', 1, 0),
        ('ai_prompt_category', 'user', '用户模板', 20, '', 'success', 0, 0),
        ('ai_prompt_category', 'assistant', '助手模板', 30, '', 'warning', 0, 0),
        ('ai_prompt_category', 'aivideo_text', 'AIVideo 文本润色', 40, '', 'primary', 0, 0),
        ('ai_prompt_category', 'aivideo_script', 'AIVideo 剧本生成', 50, '', 'primary', 0, 0),
        ('ai_prompt_category', 'aivideo_asset', 'AIVideo 资产提取', 60, '', 'success', 0, 0),
        ('ai_prompt_category', 'aivideo_storyboard', 'AIVideo 分镜提取', 70, '', 'warning', 0, 0),
        ('ai_prompt_category', 'aivideo_image', 'AIVideo 图片生成', 80, '', 'success', 0, 0),
        ('ai_prompt_category', 'aivideo_video', 'AIVideo 视频生成', 90, '', 'warning', 0, 0),
        ('ai_prompt_category', 'aivideo_tts', 'AIVideo 语音合成', 100, '', 'info', 0, 0)
) AS item(dict_type, dict_value, dict_label, dict_sort, css_class, list_class, is_default, status)
WHERE target.dict_type = item.dict_type
  AND target.dict_value = item.dict_value;

COMMIT;
