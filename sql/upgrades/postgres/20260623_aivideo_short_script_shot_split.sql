-- AIVideo short-content script split hard rule.
-- Keep database prompt templates aligned with Java fallback and built-in registry.

BEGIN;

WITH hard_rules AS (
    SELECT $rules$

【20260623短内容拆镜硬规则】
1. 短祝福、口播、单场景、单角色内容不能默认压成 1 个镜头成片；除非用户明确写“一镜到底/只要一个镜头”，否则至少拆出 3 个镜头或 3 个画面段落：建立主体/道具锚点、核心动作、台词或结尾状态。
2. 剧本生成阶段不得输出“总时长6s，共1个镜头”来承包整片；应输出分镜建议：Shot01 建立人物和场景，Shot02 执行关键道具/动作，Shot03 完成台词/反应/结尾定格。
3. 分镜提取阶段如果剧本只有一个 Shot 建议，仍必须按动作节拍和声音节奏二次拆分；同一台词可用画外音承接，不能为了省镜头把所有动作塞进一个视频。
4. 首镜起始状态必须是“主体已在位”，禁止模型自行生成“从画外走入/跑入/走出来”的入场动作；如果需要入场，必须单独写成一个入场镜头。
$rules$ AS block
)
UPDATE ai_prompt_template t
SET content = CASE
        WHEN t.content IS NULL OR btrim(t.content) = '' THEN hard_rules.block
        ELSE rtrim(t.content) || hard_rules.block
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM hard_rules
WHERE t.template_name IN (
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧分镜提取',
    'AI短剧分镜视频生成'
)
AND (COALESCE(t.built_in, 0) = 1 OR COALESCE(t.tenant_id, 0) = 0)
AND COALESCE(t.content, '') NOT LIKE '%20260623短内容拆镜硬规则%';

COMMIT;
