-- =============================================
-- 20260720 AI 应用对话调优参数（幂等）· 对标 MaxKB 全局 P1（G1-2 多轮记忆 + G1-11 应用级检索参数，按决策 D14 合并为单脚本）
-- ai_agent 增加三列（均允许 NULL，NULL 时保持既有默认行为不变）：
--   history_limit        对话历史注入条数（NULL=默认 12 条）
--   retrieval_top_k      知识库检索返回条数（NULL=默认 5 条）
--   similarity_threshold 向量检索相似度阈值（NULL=默认 0.30）
-- 回滚：
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS history_limit;
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS retrieval_top_k;
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS similarity_threshold;
-- =============================================

ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS history_limit INT;
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS retrieval_top_k INT;
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS similarity_threshold NUMERIC(4,3);

COMMENT ON COLUMN ai_agent.history_limit IS '对话历史注入条数（NULL=默认12）';
COMMENT ON COLUMN ai_agent.retrieval_top_k IS '知识库检索返回条数（NULL=默认5）';
COMMENT ON COLUMN ai_agent.similarity_threshold IS '向量检索相似度阈值（NULL=默认0.30）';
