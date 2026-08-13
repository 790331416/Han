-- Prompt 模板表对齐脚本
-- 目标：
-- 1. 补齐 ai_prompt_template 表，字段与当前代码实体保持一致
-- 2. 为历史半成品表补充缺失列
-- 3. 在空表场景下补入内置模板，确保 Prompt 管理页可直接验收

CREATE TABLE IF NOT EXISTS ai_prompt_template (
    template_id     BIGSERIAL       PRIMARY KEY,
    tenant_id       BIGINT          DEFAULT 0,
    template_name   VARCHAR(200)    NOT NULL,
    category        VARCHAR(30)     NOT NULL DEFAULT 'system',
    content         TEXT            NOT NULL,
    variables       TEXT            DEFAULT '[]',
    description     VARCHAR(1000)   DEFAULT '',
    built_in        INTEGER         DEFAULT 0,
    status          CHAR(1)         DEFAULT '0',
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

-- 20260812 修正：下面的 ALTER COLUMN ... SET DEFAULT 会引用 tenant_id / category /
-- variables / description / built_in / status 六列，但原来的 ADD COLUMN 列表只补了四个审计列。
-- 头部注释自己就写了「为历史半成品表补充缺失列」，缺其中任一列就会报
-- column ... does not exist 并中断整条升级链。这里把六列一并补上。
ALTER TABLE ai_prompt_template
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS category VARCHAR(30) DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS variables TEXT DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS description VARCHAR(1000) DEFAULT '',
    ADD COLUMN IF NOT EXISTS built_in INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status CHAR(1) DEFAULT '0',
    ADD COLUMN IF NOT EXISTS create_by VARCHAR(64) DEFAULT '',
    ADD COLUMN IF NOT EXISTS update_by VARCHAR(64) DEFAULT '',
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 列宽以 phase8 这套为权威口径（template_name 200 / category 30 / description 1000 /
-- variables TEXT）。full-init.sql 历史上是 100 / 20 / 500 / TEXT，两类环境长期分叉，
-- 这里做幂等的扩宽，把存量库统一过去；扩宽不会截断数据。
DO $$
DECLARE
    v_widen RECORD;
BEGIN
    FOR v_widen IN
        SELECT * FROM (VALUES
            ('template_name', 'VARCHAR(200)', 200),
            ('category', 'VARCHAR(30)', 30),
            ('description', 'VARCHAR(1000)', 1000)
        ) AS t(col, target_type, target_len)
    LOOP
        CONTINUE WHEN NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'ai_prompt_template'
              AND column_name = v_widen.col
              AND character_maximum_length IS NOT NULL
              AND character_maximum_length < v_widen.target_len
        );
        EXECUTE format('ALTER TABLE ai_prompt_template ALTER COLUMN %I TYPE %s',
                       v_widen.col, v_widen.target_type);
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_prompt_template'
          AND column_name = 'variables'
          AND data_type <> 'text'
    ) THEN
        ALTER TABLE ai_prompt_template ALTER COLUMN variables TYPE TEXT;
    END IF;
END $$;

ALTER TABLE ai_prompt_template
    ALTER COLUMN tenant_id SET DEFAULT 0,
    ALTER COLUMN category SET DEFAULT 'system',
    ALTER COLUMN variables SET DEFAULT '[]',
    ALTER COLUMN description SET DEFAULT '',
    ALTER COLUMN built_in SET DEFAULT 0,
    ALTER COLUMN status SET DEFAULT '0',
    ALTER COLUMN create_by SET DEFAULT '',
    ALTER COLUMN create_time SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN update_by SET DEFAULT '',
    ALTER COLUMN update_time SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_prompt_tpl_tenant
    ON ai_prompt_template(tenant_id);

INSERT INTO ai_prompt_template (
    tenant_id, template_name, category, content, variables, description, built_in, status, create_by, update_by
)
SELECT 0, '通用助手', 'system', '你是一个智能助手，请用专业、简洁的方式回答用户的问题。', '[]', '通用对话场景的系统提示词', 1, '0', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = '通用助手' AND built_in = 1);

INSERT INTO ai_prompt_template (
    tenant_id, template_name, category, content, variables, description, built_in, status, create_by, update_by
)
SELECT 0, '翻译助手', 'system', '你是一位专业的翻译专家。请将用户输入的内容翻译为{{targetLang}}，保持原文语义和风格。', '["targetLang"]', '多语言翻译场景', 1, '0', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = '翻译助手' AND built_in = 1);

INSERT INTO ai_prompt_template (
    tenant_id, template_name, category, content, variables, description, built_in, status, create_by, update_by
)
SELECT 0, '代码审查', 'system', '你是一位资深的{{language}}开发工程师，请对用户提供的代码进行审查，指出潜在问题并给出改进建议。', '["language"]', '代码审查场景', 1, '0', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = '代码审查' AND built_in = 1);

INSERT INTO ai_prompt_template (
    tenant_id, template_name, category, content, variables, description, built_in, status, create_by, update_by
)
SELECT 0, '文档总结', 'system', '请对以下内容进行总结，提取关键要点，用简洁的条目形式输出，不超过{{maxPoints}}条。', '["maxPoints"]', '长文档摘要场景', 1, '0', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = '文档总结' AND built_in = 1);

INSERT INTO ai_prompt_template (
    tenant_id, template_name, category, content, variables, description, built_in, status, create_by, update_by
)
SELECT 0, 'SQL生成', 'system', '你是一位数据库专家，请根据用户的自然语言描述生成对应的{{dbType}} SQL语句。请确保SQL语法正确且高效。', '["dbType"]', 'SQL语句生成场景', 1, '0', 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'SQL生成' AND built_in = 1);
