-- 本文件由仓库整理脚本从现有 PostgreSQL 正式脚本拆分生成。
-- 如需调整结构，请同步更新 manifest 与 sql/README.md。

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

-- =============================================
-- 24. 知识库表
-- =============================================
CREATE TABLE ai_knowledge_base (
    kb_id               BIGSERIAL       PRIMARY KEY,
    kb_name             VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    kb_type             VARCHAR(20)     NOT NULL DEFAULT 'general',
    embedding_model_id  BIGINT,
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

-- =============================================
-- 25. 知识库文档表
-- =============================================
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
CREATE INDEX idx_ai_document_kb_id ON ai_document(kb_id);

-- =============================================
-- 26. 知识库段落表
-- =============================================
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
    deleted        INTEGER         DEFAULT 0
);
COMMENT ON TABLE ai_paragraph IS '知识库段落表';
CREATE INDEX idx_ai_paragraph_doc ON ai_paragraph(doc_id);
CREATE INDEX idx_ai_paragraph_kb ON ai_paragraph(kb_id);

-- =============================================
-- 27. MCP服务器配置表
-- =============================================
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

-- =============================================
-- 28. AI工作流表
-- =============================================
CREATE TABLE ai_workflow (
    workflow_id         BIGSERIAL       PRIMARY KEY,
    workflow_name       VARCHAR(200)    NOT NULL,
    description         VARCHAR(1000)   DEFAULT '',
    workflow_type       VARCHAR(20)     NOT NULL DEFAULT 'simple',
    model_id            BIGINT,
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

-- =============================================
-- 29. AI对话会话表
-- =============================================
CREATE TABLE ai_conversation (
    conversation_id BIGSERIAL       PRIMARY KEY,
    title           VARCHAR(500)    DEFAULT '新对话',
    workflow_id     BIGINT,
    model_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    message_count   INTEGER         DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_conversation IS 'AI对话会话表';
CREATE INDEX idx_ai_conversation_user ON ai_conversation(user_id);

-- =============================================
-- 30. AI对话消息表
-- =============================================
CREATE TABLE ai_chat_message (
    message_id      BIGSERIAL       PRIMARY KEY,
    conversation_id BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    content         TEXT            NOT NULL,
    token_count     INTEGER         DEFAULT 0,
    sort_order      INTEGER         DEFAULT 0,
    tenant_id       BIGINT          DEFAULT 0,
    create_time     TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_chat_message IS 'AI对话消息表';
CREATE INDEX idx_ai_chat_message_conversation ON ai_chat_message(conversation_id);
CREATE INDEX idx_ai_chat_message_tenant ON ai_chat_message(tenant_id);

-- =============================================
-- 31. AI智能体表
-- =============================================
CREATE TABLE ai_agent (
    agent_id       BIGSERIAL       PRIMARY KEY,
    agent_name     VARCHAR(100)    NOT NULL,
    description    TEXT,
    avatar         VARCHAR(500),
    system_prompt  TEXT,
    prologue       TEXT,
    model_id       BIGINT,
    knowledge_base_ids TEXT,
    mcp_server_ids TEXT,
    temperature    NUMERIC(3,2)    DEFAULT 0.7,
    max_tokens     INT             DEFAULT 2048,
    published      CHAR(1)         DEFAULT '0',
    status         CHAR(1)         DEFAULT '0',
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    deleted        INT             DEFAULT 0
);
COMMENT ON TABLE ai_agent IS 'AI智能体';

-- =============================================
-- 32. Prompt模板表
-- =============================================
CREATE TABLE ai_prompt_template (
    template_id   BIGSERIAL       PRIMARY KEY,
    tenant_id     BIGINT,
    template_name VARCHAR(100)    NOT NULL,
    category      VARCHAR(20)     NOT NULL DEFAULT 'system',
    content       TEXT            NOT NULL,
    variables     VARCHAR(500),
    description   VARCHAR(500),
    built_in      INT             NOT NULL DEFAULT 0,
    status        CHAR(1)         NOT NULL DEFAULT '0',
    create_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_prompt_template IS 'Prompt模板表';
CREATE INDEX idx_prompt_tpl_tenant ON ai_prompt_template(tenant_id);

-- =============================================
-- 33. Token用量记录表
-- =============================================
CREATE TABLE ai_token_usage (
    usage_id          BIGSERIAL       PRIMARY KEY,
    tenant_id         BIGINT,
    user_id           BIGINT,
    conversation_id   BIGINT,
    model_id          BIGINT,
    model_name        VARCHAR(100),
    prompt_tokens     INT             DEFAULT 0,
    completion_tokens INT             DEFAULT 0,
    total_tokens      INT             DEFAULT 0,
    create_time       TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_token_usage IS 'AI Token用量记录表';
CREATE INDEX idx_token_usage_tenant ON ai_token_usage(tenant_id);
CREATE INDEX idx_token_usage_user ON ai_token_usage(user_id);
CREATE INDEX idx_token_usage_time ON ai_token_usage(create_time);

-- =============================================
-- 34. 知识图谱节点表
-- =============================================
CREATE TABLE ai_graph_node (
    node_id        BIGSERIAL       PRIMARY KEY,
    kb_id          BIGINT,
    node_name      VARCHAR(200)    NOT NULL,
    node_type      VARCHAR(50)     NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_graph_node IS '知识图谱节点';
CREATE INDEX idx_graph_node_kb ON ai_graph_node(kb_id);
CREATE INDEX idx_graph_node_type ON ai_graph_node(node_type);

-- =============================================
-- 35. 知识图谱关系表
-- =============================================
CREATE TABLE ai_graph_edge (
    edge_id        BIGSERIAL       PRIMARY KEY,
    kb_id          BIGINT,
    source_node_id BIGINT          NOT NULL,
    target_node_id BIGINT          NOT NULL,
    relation_type  VARCHAR(100)    NOT NULL,
    properties     TEXT,
    tenant_id      BIGINT,
    create_time    TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_graph_edge IS '知识图谱关系';
CREATE INDEX idx_graph_edge_kb ON ai_graph_edge(kb_id);
CREATE INDEX idx_graph_edge_source ON ai_graph_edge(source_node_id);
CREATE INDEX idx_graph_edge_target ON ai_graph_edge(target_node_id);
