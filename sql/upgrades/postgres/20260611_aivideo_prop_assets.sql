-- AI short-drama prop assets.
-- Idempotent upgrade: add structured prop anchors and align asset extraction prompt.

CREATE TABLE IF NOT EXISTS ai_video_prop (
    prop_id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT DEFAULT 0,
    prop_name VARCHAR(200) NOT NULL,
    prop_type VARCHAR(100),
    visual_desc TEXT,
    color VARCHAR(100),
    material VARCHAR(100),
    shape VARCHAR(100),
    owner_character_name VARCHAR(128),
    first_shot_no INT,
    last_holder VARCHAR(128),
    continuity_rules TEXT,
    prompt_text TEXT,
    locked_media_id BIGINT,
    confirm_status VARCHAR(32) DEFAULT 'PENDING',
    sort_order INT DEFAULT 0,
    create_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    del_flag INT DEFAULT 0
);

COMMENT ON TABLE ai_video_prop IS 'AI short-drama prop asset';

CREATE INDEX IF NOT EXISTS idx_ai_video_prop_project ON ai_video_prop (project_id);
CREATE INDEX IF NOT EXISTS idx_ai_video_prop_locked_media ON ai_video_prop (locked_media_id);

WITH prop_rules AS (
    SELECT $rules$

【20260611道具资产规则】
1. 资产提取必须输出顶层 "props" 数组，用于把关键道具变成可确认、可生成参考图、可传入视频模型的稳定资产锚点。
2. 凡是跨镜头出现、被递给/接过/展示/交给/传给/拿给/装入/结算/读写/推动剧情的物体，必须作为关键道具输出。
3. 每个关键道具必须写清 propName、propType、visualDesc、color、material、shape、ownerCharacterName、firstShotNo、lastHolder、continuityRules、promptText。
4. 道具交接必须写清谁递给谁、什么道具、从画面哪边来、最后谁拿着；禁止“试卷飘过来”“接过某物”“展示给画外”等无来源动作。
5. 跨镜头连续出现的道具必须锁定颜色、材质、形状、尺寸、归属角色和结束位置；例如蓝色透明收纳盒在下一镜仍必须是同一蓝色透明收纳盒。
6. JSON 示例片段：
"props": [{"propName":"","propType":"","visualDesc":"","color":"","material":"","shape":"","ownerCharacterName":"","firstShotNo":1,"lastHolder":"","continuityRules":"","promptText":""}]
$rules$ AS block
)
UPDATE ai_prompt_template t
SET content = CASE
        WHEN t.content IS NULL OR btrim(t.content) = '' THEN prop_rules.block
        ELSE rtrim(t.content) || prop_rules.block
    END,
    variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText","soundDesign","bgmPlan","sfxPlan"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM prop_rules
WHERE t.template_name = 'AI短剧资产提取'
  AND (COALESCE(t.built_in, 0) = 1 OR COALESCE(t.tenant_id, 0) = 0)
  AND COALESCE(t.content, '') NOT LIKE '%【20260611道具资产规则】%';
