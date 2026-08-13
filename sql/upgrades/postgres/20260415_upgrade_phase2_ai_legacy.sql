-- =============================================
-- AI模块第二阶段升级SQL
-- 新增：Prompt模板表、Token用量表
-- =============================================

-- Prompt模板表
CREATE TABLE IF NOT EXISTS ai_prompt_template (
    template_id   BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT,
    template_name VARCHAR(100)  NOT NULL,
    category      VARCHAR(20)   NOT NULL DEFAULT 'system',
    content       TEXT          NOT NULL,
    variables     VARCHAR(500),
    description   VARCHAR(500),
    built_in      INT           NOT NULL DEFAULT 0,
    status        CHAR(1)       NOT NULL DEFAULT '0',
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  ai_prompt_template IS 'Prompt模板表';
COMMENT ON COLUMN ai_prompt_template.template_id   IS '模板ID';
COMMENT ON COLUMN ai_prompt_template.tenant_id     IS '租户ID';
COMMENT ON COLUMN ai_prompt_template.template_name IS '模板名称';
COMMENT ON COLUMN ai_prompt_template.category      IS '分类（system/user/assistant）';
COMMENT ON COLUMN ai_prompt_template.content       IS '模板内容（支持{{变量}}占位符）';
COMMENT ON COLUMN ai_prompt_template.variables     IS '变量列表（JSON数组）';
COMMENT ON COLUMN ai_prompt_template.description   IS '使用场景说明';
COMMENT ON COLUMN ai_prompt_template.built_in      IS '是否内置（1内置 0自定义）';
COMMENT ON COLUMN ai_prompt_template.status        IS '状态（0正常 1停用）';

-- Token用量记录表
CREATE TABLE IF NOT EXISTS ai_token_usage (
    usage_id          BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT,
    user_id           BIGINT,
    conversation_id   BIGINT,
    model_id          BIGINT,
    model_name        VARCHAR(100),
    prompt_tokens     INT          DEFAULT 0,
    completion_tokens INT          DEFAULT 0,
    total_tokens      INT          DEFAULT 0,
    create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  ai_token_usage IS 'AI Token用量记录表';
COMMENT ON COLUMN ai_token_usage.usage_id          IS '记录ID';
COMMENT ON COLUMN ai_token_usage.tenant_id         IS '租户ID';
COMMENT ON COLUMN ai_token_usage.user_id           IS '用户ID';
COMMENT ON COLUMN ai_token_usage.conversation_id   IS '会话ID';
COMMENT ON COLUMN ai_token_usage.model_id          IS '模型ID';
COMMENT ON COLUMN ai_token_usage.model_name        IS '模型名称';
COMMENT ON COLUMN ai_token_usage.prompt_tokens     IS '提示词Token数';
COMMENT ON COLUMN ai_token_usage.completion_tokens IS '回复Token数';
COMMENT ON COLUMN ai_token_usage.total_tokens      IS '总Token数';

-- 索引
CREATE INDEX IF NOT EXISTS idx_token_usage_tenant ON ai_token_usage(tenant_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_user   ON ai_token_usage(user_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_time   ON ai_token_usage(create_time);
CREATE INDEX IF NOT EXISTS idx_prompt_tpl_tenant  ON ai_prompt_template(tenant_id);

-- 内置Prompt模板（示例数据）
-- 20260812 修正：原来是无条件 INSERT ... VALUES，是全部升级脚本里唯一一处没有冲突处理的
-- 数据插入。ai_prompt_template 上没有 template_name 的唯一约束，clean_full 路径下
-- full-init 已经插过同样 5 条，本脚本再插一遍就变成 2 份，每重放一次再多 1 份。
-- 改为与 phase8_prompt_template_alignment.sql 一致的 SELECT ... WHERE NOT EXISTS 守卫。
INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT v.tenant_id, v.template_name, v.category, v.content, v.variables, v.description, v.built_in, v.status
FROM (VALUES
(NULL::BIGINT, '通用助手', 'system', '你是一个智能助手，请用专业、简洁的方式回答用户的问题。', NULL::TEXT, '通用对话场景的系统提示词', 1, '0'),
(NULL, '翻译助手', 'system', '你是一位专业的翻译专家。请将用户输入的内容翻译为{{targetLang}}，保持原文语义和风格。', '["targetLang"]', '多语言翻译场景', 1, '0'),
(NULL, '代码审查', 'system', '你是一位资深的{{language}}开发工程师，请对用户提供的代码进行审查，指出潜在问题并给出改进建议。', '["language"]', '代码审查场景', 1, '0'),
(NULL, '文档总结', 'system', '请对以下内容进行总结，提取关键要点，用简洁的条目形式输出，不超过{{maxPoints}}条。', '["maxPoints"]', '长文档摘要场景', 1, '0'),
(NULL, 'SQL生成', 'system', '你是一位数据库专家，请根据用户的自然语言描述生成对应的{{dbType}} SQL语句。请确保SQL语法正确且高效。', '["dbType"]', 'SQL语句生成场景', 1, '0')
) AS v(tenant_id, template_name, category, content, variables, description, built_in, status)
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_template t WHERE t.template_name = v.template_name
);
