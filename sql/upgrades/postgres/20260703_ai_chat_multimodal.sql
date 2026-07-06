-- =============================================
-- 20260703 AI 对话多模态升级（幂等）
-- 1) ai_model 增加视觉能力标记（图片理解开关，'1'支持/'0'不支持）
-- 2) ai_chat_message 增加图片附件列（JSON: [{fileId,url,name}]，
--    承载多模态输入图与对话内生成图）
-- 回滚：
--   ALTER TABLE ai_model DROP COLUMN IF EXISTS supports_vision;
--   ALTER TABLE ai_chat_message DROP COLUMN IF EXISTS images;
-- =============================================

DO $$
BEGIN
    IF to_regclass('public.ai_model') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE ai_model ADD COLUMN IF NOT EXISTS supports_vision CHAR(1) DEFAULT ''0''';
        EXECUTE 'COMMENT ON COLUMN ai_model.supports_vision IS ''是否支持视觉输入(图片理解) 1支持 0不支持''';
    END IF;

    IF to_regclass('public.ai_chat_message') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE ai_chat_message ADD COLUMN IF NOT EXISTS images TEXT';
        EXECUTE 'COMMENT ON COLUMN ai_chat_message.images IS ''图片附件JSON [{fileId,url,name}]''';
    END IF;
END $$;
