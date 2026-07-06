-- =============================================
-- 20260703 AI 应用发布分享升级（幂等）
-- ai_agent 增加 share_key 列（公开分享链接 key，发布时生成，重置后旧链接失效），
-- 配套 /ai/share/{shareKey}/profile|chat 公开对话接口与 /chat/share/:shareKey 免登录页
-- 回滚：
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS share_key;
-- =============================================

ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS share_key VARCHAR(64);
COMMENT ON COLUMN ai_agent.share_key IS '公开分享链接 key（发布时生成，重置后旧链接失效）';
