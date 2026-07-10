-- AIVideo action budget, complex-action split and prop-link prompt hard rules.
-- Keep this upgrade aligned with AiPromptTemplateBuiltinRegistry.

BEGIN;

WITH hard_rules AS (
    SELECT $rules$

【20260615动作预算与道具关联硬规则】
1. 5 秒镜头只允许 1 个主动作 + 1 个反应/表情 + 1 个结尾状态；6 秒镜头允许 2 个连续动作 + 结尾状态；8 秒镜头允许 3 个连续动作 + 明确结尾状态。
2. 拔剑、出鞘、挥砍、指向目标、施法、打斗、救援、倒地起身、悬浮、爆炸、变身、俯冲、落水等强动作要额外占预算；5 秒镜头里强动作只能作为唯一核心，不得再塞指向、转身、复杂表情和第二个结果动作。
3. 超过 3 个 action beat 必须自动拆成多个 shots，不允许硬塞。复杂动作推荐拆法：镜头A=起始状态+核心强动作；镜头B=结果状态+指向/反应/结尾状态。
4. 分镜动作必须分字段写清楚：起始状态、动作节拍、画内人物、画外/离场人物、道具状态、结尾状态；不要把场内人数、离场说明、动作和台词混成一句。
5. 出现武器、手持物、发光物、交接物或剧情推进物时，必须在 props 中建立同名道具资产；如寒光剑、长剑、法杖、盾牌、收纳盒、试卷、账本、存折、价格标签等。
6. 道具交接必须写清 giver、receiver、prop、screenDirection、finalOwner；禁止“接过某物”“从画外飘来”“展示给画外”这类不可拍描述。
7. 同一场景非硬切时，上镜画内人物默认继续在场；如果不出现，必须写清离场、画外位置、单人反应、裁切或反打原因。
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
AND COALESCE(t.content, '') NOT LIKE '%20260615动作预算与道具关联硬规则%';

COMMIT;
