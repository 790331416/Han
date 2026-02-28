-- =============================================
-- HAN Cloud AI模块数据库脚本
-- 包含: AI模型管理、知识库、MCP服务、AI工作流
-- =============================================

-- ----------------------------
-- AI模型配置表
-- ----------------------------
DROP TABLE IF EXISTS ai_model;
CREATE TABLE ai_model (
    model_id        BIGSERIAL       PRIMARY KEY,
    model_name      VARCHAR(100)    NOT NULL,
    model_type      VARCHAR(20)     NOT NULL DEFAULT 'LLM',
    provider        VARCHAR(50)     NOT NULL DEFAULT 'openai',
    model_code      VARCHAR(100)    NOT NULL,
    base_url        VARCHAR(500)    NOT NULL,
    api_key         VARCHAR(500)    DEFAULT '',
    max_tokens      INTEGER         DEFAULT 2048,
    temperature     NUMERIC(3,2)    DEFAULT 0.70,
    status          CHAR(1)         DEFAULT '0',
    remark          VARCHAR(500)    DEFAULT '',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_model IS 'AI模型配置表';
COMMENT ON COLUMN ai_model.model_id IS '模型ID';
COMMENT ON COLUMN ai_model.model_name IS '模型名称';
COMMENT ON COLUMN ai_model.model_type IS '模型类型: LLM/EMBEDDING/RERANK/TTS/STT';
COMMENT ON COLUMN ai_model.provider IS '供应商: openai/deepseek/zhipu/qwen/ollama/azure';
COMMENT ON COLUMN ai_model.model_code IS '模型标识';
COMMENT ON COLUMN ai_model.base_url IS 'API Base URL';
COMMENT ON COLUMN ai_model.api_key IS 'API Key(加密存储)';
COMMENT ON COLUMN ai_model.max_tokens IS '最大Token数';
COMMENT ON COLUMN ai_model.temperature IS '温度参数';
COMMENT ON COLUMN ai_model.status IS '状态(0正常 1停用)';

-- ----------------------------
-- 知识库表
-- ----------------------------
DROP TABLE IF EXISTS ai_knowledge_base;
CREATE TABLE ai_knowledge_base (
    kb_id               BIGSERIAL       PRIMARY KEY,
    kb_name             VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    kb_type             VARCHAR(20)     NOT NULL DEFAULT 'general',
    embedding_model_id  BIGINT          DEFAULT NULL,
    document_count      INTEGER         DEFAULT 0,
    paragraph_count     INTEGER         DEFAULT 0,
    char_count          BIGINT          DEFAULT 0,
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_knowledge_base IS '知识库表';
COMMENT ON COLUMN ai_knowledge_base.kb_type IS '知识库类型: general/qa/web';

-- ----------------------------
-- 知识库文档表
-- ----------------------------
DROP TABLE IF EXISTS ai_document;
CREATE TABLE ai_document (
    doc_id          BIGSERIAL       PRIMARY KEY,
    kb_id           BIGINT          NOT NULL,
    doc_name        VARCHAR(500)    NOT NULL,
    doc_type        VARCHAR(20)     DEFAULT 'txt',
    file_path       VARCHAR(1000)   DEFAULT '',
    file_size       BIGINT          DEFAULT 0,
    char_count      BIGINT          DEFAULT 0,
    paragraph_count INTEGER         DEFAULT 0,
    index_status    VARCHAR(20)     DEFAULT 'pending',
    index_error     TEXT            DEFAULT '',
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_document IS '知识库文档表';
COMMENT ON COLUMN ai_document.doc_type IS '文档类型: txt/pdf/md/docx/html/url';
COMMENT ON COLUMN ai_document.index_status IS '索引状态: pending/indexing/completed/failed';

CREATE INDEX idx_ai_document_kb_id ON ai_document(kb_id);

-- ----------------------------
-- 知识库段落表(向量存储)
-- ----------------------------
DROP TABLE IF EXISTS ai_paragraph;
CREATE TABLE ai_paragraph (
    para_id         BIGSERIAL       PRIMARY KEY,
    doc_id          BIGINT          NOT NULL,
    kb_id           BIGINT          NOT NULL,
    title           VARCHAR(500)    DEFAULT '',
    content         TEXT            NOT NULL,
    char_count      INTEGER         DEFAULT 0,
    hit_count       INTEGER         DEFAULT 0,
    status          CHAR(1)         DEFAULT '0',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_paragraph IS '知识库段落表';

CREATE INDEX idx_ai_paragraph_doc_id ON ai_paragraph(doc_id);
CREATE INDEX idx_ai_paragraph_kb_id ON ai_paragraph(kb_id);

-- ----------------------------
-- MCP服务器配置表
-- ----------------------------
DROP TABLE IF EXISTS ai_mcp_server;
CREATE TABLE ai_mcp_server (
    mcp_id          BIGSERIAL       PRIMARY KEY,
    server_name     VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000)   DEFAULT '',
    transport_type  VARCHAR(30)     NOT NULL DEFAULT 'sse',
    command         VARCHAR(500)    DEFAULT '',
    args            TEXT            DEFAULT '[]',
    env_vars        TEXT            DEFAULT '{}',
    url             VARCHAR(500)    DEFAULT '',
    tools           TEXT            DEFAULT '[]',
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_mcp_server IS 'MCP服务器配置表';
COMMENT ON COLUMN ai_mcp_server.transport_type IS '传输类型: stdio/sse/streamable_http';

-- ----------------------------
-- AI工作流表
-- ----------------------------
DROP TABLE IF EXISTS ai_workflow;
CREATE TABLE ai_workflow (
    workflow_id         BIGSERIAL       PRIMARY KEY,
    workflow_name       VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    workflow_type       VARCHAR(20)     NOT NULL DEFAULT 'simple',
    model_id            BIGINT          DEFAULT NULL,
    knowledge_base_ids  TEXT            DEFAULT '[]',
    mcp_server_ids      TEXT            DEFAULT '[]',
    system_prompt       TEXT            DEFAULT '',
    flow_config         TEXT            DEFAULT '{}',
    prologue            VARCHAR(2000)   DEFAULT '',
    published           CHAR(1)         DEFAULT '0',
    status              CHAR(1)         DEFAULT '0',
    tenant_id           BIGINT          DEFAULT 0,
    create_by           VARCHAR(64)     DEFAULT '',
    create_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by           VARCHAR(64)     DEFAULT '',
    update_time         TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_workflow IS 'AI工作流表';
COMMENT ON COLUMN ai_workflow.workflow_type IS '工作流类型: simple/advanced';

-- ----------------------------
-- AI对话会话表
-- ----------------------------
DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation (
    conversation_id BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(500)    DEFAULT '新对话',
    workflow_id     BIGINT          DEFAULT NULL,
    model_id        BIGINT          DEFAULT NULL,
    user_id         BIGINT          NOT NULL,
    message_count   INTEGER         DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_conversation IS 'AI对话会话表';
COMMENT ON COLUMN ai_conversation.workflow_id IS '关联的工作流ID（可选）';
COMMENT ON COLUMN ai_conversation.model_id IS '关联的模型ID';

CREATE INDEX idx_ai_conversation_user ON ai_conversation(user_id);

-- ----------------------------
-- AI对话消息表
-- ----------------------------
DROP TABLE IF EXISTS ai_chat_message;
CREATE TABLE ai_chat_message (
    message_id      BIGSERIAL       PRIMARY KEY,
    conversation_id BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    content         TEXT            NOT NULL,
    token_count     INTEGER         DEFAULT 0,
    sort_order      INTEGER         DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_chat_message IS 'AI对话消息表';
COMMENT ON COLUMN ai_chat_message.role IS '角色: user/assistant/system';
COMMENT ON COLUMN ai_chat_message.sort_order IS '消息排序号';

CREATE INDEX idx_ai_chat_message_conversation ON ai_chat_message(conversation_id);

-- ----------------------------
-- pgvector 向量扩展（需要 PostgreSQL 安装 pgvector 扩展）
-- ----------------------------
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pgvector 扩展不可用，embedding 列将使用 TEXT 类型: %', SQLERRM;
END;
$$;

-- ----------------------------
-- AI知识库段落表
-- ----------------------------
DROP TABLE IF EXISTS ai_paragraph;
CREATE TABLE ai_paragraph (
    paragraph_id    BIGSERIAL       PRIMARY KEY,
    doc_id          BIGINT          NOT NULL,
    kb_id           BIGINT          NOT NULL,
    title           VARCHAR(500)    DEFAULT '',
    content         TEXT            NOT NULL,
    char_count      INTEGER         DEFAULT 0,
    hit_count       INTEGER         DEFAULT 0,
    embedding       TEXT,
    status          CHAR(1)         DEFAULT '0',
    tenant_id       BIGINT          DEFAULT 0,
    create_by       VARCHAR(64)     DEFAULT '',
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by       VARCHAR(64)     DEFAULT '',
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    del_flag        INTEGER         DEFAULT 0
);

COMMENT ON TABLE ai_paragraph IS 'AI知识库段落表';
COMMENT ON COLUMN ai_paragraph.doc_id IS '所属文档ID';
COMMENT ON COLUMN ai_paragraph.kb_id IS '所属知识库ID';
COMMENT ON COLUMN ai_paragraph.title IS '段落标题';
COMMENT ON COLUMN ai_paragraph.content IS '段落内容';
COMMENT ON COLUMN ai_paragraph.char_count IS '字符数';
COMMENT ON COLUMN ai_paragraph.hit_count IS '命中次数';
COMMENT ON COLUMN ai_paragraph.embedding IS '向量嵌入(默认1536维，可根据模型调整)';
COMMENT ON COLUMN ai_paragraph.status IS '状态(0正常 1停用)';

CREATE INDEX idx_ai_paragraph_doc ON ai_paragraph(doc_id);
CREATE INDEX idx_ai_paragraph_kb ON ai_paragraph(kb_id);
-- HNSW向量索引（余弦距离），大幅提升向量检索速度
-- CREATE INDEX idx_ai_paragraph_embedding ON ai_paragraph USING hnsw (embedding vector_cosine_ops);

-- ----------------------------
-- 初始化数据: 预置AI模型供应商
-- ----------------------------
INSERT INTO ai_model (model_name, model_type, provider, model_code, base_url, api_key, max_tokens, temperature, status, remark) VALUES
('DeepSeek Chat', 'LLM', 'deepseek', 'deepseek-chat', 'https://api.deepseek.com/v1', '', 4096, 0.70, '1', 'DeepSeek对话模型，需配置API Key'),
('DeepSeek Reasoner', 'LLM', 'deepseek', 'deepseek-reasoner', 'https://api.deepseek.com/v1', '', 8192, 0.00, '1', 'DeepSeek推理模型，需配置API Key'),
('通义千问 Plus', 'LLM', 'qwen', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 4096, 0.70, '1', '阿里通义千问Plus，需配置API Key'),
('智谱 GLM-4', 'LLM', 'zhipu', 'glm-4', 'https://open.bigmodel.cn/api/paas/v4', '', 4096, 0.70, '1', '智谱AI GLM-4，需配置API Key'),
('OpenAI GPT-4o', 'LLM', 'openai', 'gpt-4o', 'https://api.openai.com/v1', '', 4096, 0.70, '1', 'OpenAI GPT-4o，需配置API Key'),
('Ollama 本地模型', 'LLM', 'ollama', 'llama3', 'http://localhost:11434/v1', '', 4096, 0.70, '1', 'Ollama本地部署模型');
