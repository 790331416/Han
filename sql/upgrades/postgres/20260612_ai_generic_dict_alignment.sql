-- Generic AI dictionary alignment (no AIVideo business).
-- Only inserts missing generic AI dictionary rows; does not overwrite user-maintained data.
-- Idempotent and tenant-aware. AIVideo business dictionaries are intentionally excluded
-- and should be provided by the AIVideo business module / its own upgrade SQL.

BEGIN;

CREATE TEMP TABLE IF NOT EXISTS tmp_ai_target_tenants (
    tenant_id BIGINT PRIMARY KEY
) ON COMMIT DROP;
TRUNCATE tmp_ai_target_tenants;
INSERT INTO tmp_ai_target_tenants (tenant_id) VALUES (0) ON CONFLICT DO NOTHING;

DO $$
DECLARE
    v_where TEXT := '';
    v_tenant_where TEXT := ' WHERE tenant_id IS NOT NULL';
BEGIN
    IF to_regclass('public.sys_tenant') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'del_flag'
    ) THEN
        v_where := ' WHERE COALESCE(del_flag, 0) = 0';
        v_tenant_where := ' WHERE COALESCE(del_flag, 0) = 0 AND tenant_id IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'id'
    ) THEN
        EXECUTE 'INSERT INTO tmp_ai_target_tenants (tenant_id) SELECT id::BIGINT FROM sys_tenant' || v_where || ' ON CONFLICT DO NOTHING';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'sys_tenant' AND column_name = 'tenant_id'
    ) THEN
        EXECUTE 'INSERT INTO tmp_ai_target_tenants (tenant_id) SELECT tenant_id::BIGINT FROM sys_tenant' || v_tenant_where || ' ON CONFLICT DO NOTHING';
    END IF;
END $$;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_target_tenants
),
type_items(dict_name, dict_type, remark, status) AS (
    VALUES
        ('AI模型类型', 'ai_model_type', 'AI模型管理模型类型列表', 0),
        ('AI模型供应商', 'ai_model_provider', 'AI模型管理供应商列表', 0),
        ('AI Prompt模板分类', 'ai_prompt_category', 'AI Prompt模板分类列表', 0),
        ('AI知识库类型', 'ai_kb_type', 'AI知识库类型列表', 0),
        ('AI MCP传输类型', 'ai_mcp_transport_type', 'AI MCP 传输类型列表', 0),
        ('AI工作流类型', 'ai_workflow_type', 'AI工作流类型列表', 0),
        ('AI知识库索引状态', 'ai_knowledge_index_status', 'AI知识库索引状态列表', 0)
),
type_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN type_items item
),
missing_types AS (
    SELECT candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type) AS rn
    FROM type_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1 FROM sys_dict_type target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
    )
),
type_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_type
)
INSERT INTO sys_dict_type (id, tenant_id, dict_name, dict_type, status, remark)
SELECT type_base.max_id + missing_types.rn, missing_types.tenant_id,
    missing_types.dict_name, missing_types.dict_type, missing_types.status, missing_types.remark
FROM missing_types CROSS JOIN type_base;

WITH target_tenants AS (
    SELECT tenant_id FROM tmp_ai_target_tenants
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
        ('ai_knowledge_index_status', '失败', 'failed', 40, '', 'danger', 0, 0)
),
data_candidates AS (
    SELECT tenants.tenant_id, item.*
    FROM target_tenants tenants
    CROSS JOIN data_items item
),
missing_data AS (
    SELECT candidate.*,
        ROW_NUMBER() OVER (ORDER BY candidate.tenant_id, candidate.dict_type, candidate.dict_sort, candidate.dict_value) AS rn
    FROM data_candidates candidate
    WHERE NOT EXISTS (
        SELECT 1 FROM sys_dict_data target
        WHERE target.tenant_id IS NOT DISTINCT FROM candidate.tenant_id
          AND target.dict_type = candidate.dict_type
          AND target.dict_value = candidate.dict_value
    )
),
data_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_dict_data
)
INSERT INTO sys_dict_data (id, tenant_id, dict_type, dict_label, dict_value, dict_sort, css_class, list_class, is_default, status)
SELECT data_base.max_id + missing_data.rn, missing_data.tenant_id,
    missing_data.dict_type, missing_data.dict_label, missing_data.dict_value,
    missing_data.dict_sort, missing_data.css_class, missing_data.list_class,
    missing_data.is_default, missing_data.status
FROM missing_data CROSS JOIN data_base;

COMMIT;
