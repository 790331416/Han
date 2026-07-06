-- =============================================
-- 20260703 AI 编排执行引擎升级（幂等）
-- ai_chat_message 增加扩展元数据列 meta（JSON: {nodeTraces:[...]}，
-- 承载 advanced 工作流编排的节点执行时间线）
-- 回滚：
--   ALTER TABLE ai_chat_message DROP COLUMN IF EXISTS meta;
-- =============================================

DO $$
BEGIN
    IF to_regclass('public.ai_chat_message') IS NULL THEN
        RETURN;
    END IF;

    EXECUTE 'ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS meta TEXT';
    EXECUTE 'COMMENT ON COLUMN ai_chat_message.meta IS ''扩展元数据JSON {nodeTraces:[...]}''';
END $$;
