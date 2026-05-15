-- AI智能体表
CREATE TABLE IF NOT EXISTS ai_agent (
    agent_id       BIGSERIAL PRIMARY KEY,
    agent_name     VARCHAR(100) NOT NULL,
    description    TEXT,
    avatar         VARCHAR(500),
    system_prompt  TEXT,
    prologue       TEXT,
    model_id       BIGINT,
    knowledge_base_ids TEXT,
    mcp_server_ids TEXT,
    temperature    NUMERIC(3,2) DEFAULT 0.7,
    max_tokens     INT DEFAULT 2048,
    published      CHAR(1) DEFAULT '0',
    status         CHAR(1) DEFAULT '0',
    tenant_id      BIGINT,
    create_by      VARCHAR(64),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by      VARCHAR(64),
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag       INT DEFAULT 0
);

ALTER TABLE ai_agent
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS avatar VARCHAR(500),
    ADD COLUMN IF NOT EXISTS system_prompt TEXT,
    ADD COLUMN IF NOT EXISTS prologue TEXT,
    ADD COLUMN IF NOT EXISTS model_id BIGINT,
    ADD COLUMN IF NOT EXISTS knowledge_base_ids TEXT,
    ADD COLUMN IF NOT EXISTS mcp_server_ids TEXT,
    ADD COLUMN IF NOT EXISTS temperature NUMERIC(3,2) DEFAULT 0.7,
    ADD COLUMN IF NOT EXISTS max_tokens INT DEFAULT 2048,
    ADD COLUMN IF NOT EXISTS published CHAR(1) DEFAULT '0',
    ADD COLUMN IF NOT EXISTS status CHAR(1) DEFAULT '0',
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT,
    ADD COLUMN IF NOT EXISTS create_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS update_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_agent'
          AND column_name = 'deleted'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_agent'
          AND column_name = 'del_flag'
    ) THEN
        ALTER TABLE ai_agent RENAME COLUMN deleted TO del_flag;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_agent'
          AND column_name = 'del_flag'
    ) THEN
        ALTER TABLE ai_agent ADD COLUMN del_flag INT DEFAULT 0;
    END IF;

    UPDATE ai_agent SET del_flag = COALESCE(del_flag, 0) WHERE del_flag IS NULL;
    ALTER TABLE ai_agent ALTER COLUMN del_flag SET DEFAULT 0;
END $$;

COMMENT ON TABLE ai_agent IS 'AI智能体';
COMMENT ON COLUMN ai_agent.agent_id IS '智能体ID';
COMMENT ON COLUMN ai_agent.agent_name IS '智能体名称';
COMMENT ON COLUMN ai_agent.description IS '描述';
COMMENT ON COLUMN ai_agent.avatar IS '头像';
COMMENT ON COLUMN ai_agent.system_prompt IS '系统提示词（角色设定）';
COMMENT ON COLUMN ai_agent.prologue IS '开场白';
COMMENT ON COLUMN ai_agent.model_id IS '关联LLM模型ID';
COMMENT ON COLUMN ai_agent.knowledge_base_ids IS '关联知识库ID列表(JSON)';
COMMENT ON COLUMN ai_agent.mcp_server_ids IS '关联MCP服务ID列表(JSON)';
COMMENT ON COLUMN ai_agent.temperature IS '温度参数';
COMMENT ON COLUMN ai_agent.max_tokens IS '最大Token数';
COMMENT ON COLUMN ai_agent.published IS '是否发布(0未发布 1已发布)';
COMMENT ON COLUMN ai_agent.status IS '状态(0正常 1停用)';
COMMENT ON COLUMN ai_agent.del_flag IS '删除标志(0存在 1删除)';
