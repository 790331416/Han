-- =============================================
-- 20260720 AI 应用对话调优参数（幂等）· 对标 MaxKB 全局 P1（G1-2 多轮记忆 + G1-11 应用级检索参数 + G1-10 开场白推荐问题，按决策 D14 合并为单脚本）
-- ai_agent 增加四列（前三列允许 NULL，NULL 时保持既有默认行为不变）：
--   history_limit        对话历史注入条数（NULL=默认 12 条）
--   retrieval_top_k      知识库检索返回条数（NULL=默认 5 条）
--   similarity_threshold 向量检索相似度阈值（NULL=默认 0.30）
--   suggested_questions  开场推荐问题（JSON 字符串数组，最多 5 条；NULL/空=不展示）
-- ai_workflow 增加一列：
--   suggested_questions  开场推荐问题（JSON 字符串数组，最多 5 条；默认 '[]'）
-- 回滚：
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS history_limit;
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS retrieval_top_k;
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS similarity_threshold;
--   ALTER TABLE ai_agent DROP COLUMN IF EXISTS suggested_questions;
--   ALTER TABLE ai_workflow DROP COLUMN IF EXISTS suggested_questions;
-- =============================================

ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS history_limit INT;
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS retrieval_top_k INT;
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS similarity_threshold NUMERIC(4,3);
ALTER TABLE ai_agent ADD COLUMN IF NOT EXISTS suggested_questions TEXT;
ALTER TABLE ai_workflow ADD COLUMN IF NOT EXISTS suggested_questions TEXT DEFAULT '[]';

COMMENT ON COLUMN ai_agent.history_limit IS '对话历史注入条数（NULL=默认12）';
COMMENT ON COLUMN ai_agent.retrieval_top_k IS '知识库检索返回条数（NULL=默认5）';
COMMENT ON COLUMN ai_agent.similarity_threshold IS '向量检索相似度阈值（NULL=默认0.30）';
COMMENT ON COLUMN ai_agent.suggested_questions IS '开场推荐问题（JSON字符串数组，最多5条）';
COMMENT ON COLUMN ai_workflow.suggested_questions IS '开场推荐问题（JSON字符串数组，最多5条）';
