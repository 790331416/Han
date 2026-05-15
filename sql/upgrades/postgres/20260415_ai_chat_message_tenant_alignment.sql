DO $$
BEGIN
    IF to_regclass('public.ai_chat_message') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ai_chat_message'
          AND column_name = 'tenant_id'
    ) THEN
        EXECUTE 'ALTER TABLE ai_chat_message ADD COLUMN tenant_id BIGINT DEFAULT 0';
    END IF;

    IF to_regclass('public.ai_conversation') IS NOT NULL THEN
        UPDATE ai_chat_message msg
        SET tenant_id = COALESCE(conv.tenant_id, 0)
        FROM ai_conversation conv
        WHERE msg.conversation_id = conv.conversation_id
          AND (msg.tenant_id IS NULL OR msg.tenant_id = 0);
    END IF;

    UPDATE ai_chat_message
    SET tenant_id = COALESCE(tenant_id, 0)
    WHERE tenant_id IS NULL;

    EXECUTE 'CREATE INDEX IF NOT EXISTS idx_ai_chat_message_tenant ON ai_chat_message (tenant_id)';
END $$;
