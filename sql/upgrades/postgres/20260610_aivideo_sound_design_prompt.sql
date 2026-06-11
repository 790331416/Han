-- AIVideo sound-design prompt alignment.
-- Purpose: make script/asset/storyboard templates produce voice, BGM and SFX planning before post-production.

WITH sound_rules AS (
    SELECT $rules$

【20260610声音设计资产规则】
1. 剧本生成必须增加“声音设计”小节：为每个主要角色定义角色声线、语速、情绪范围、推荐 voiceType/参考音频需求；旁白必须单独定义旁白声线。
2. 剧本生成必须规划背景音乐：每个场景或剧情段写清 BGM 风格、情绪、作用范围、起止镜头、淡入淡出和有人声时的压低规则。
3. 剧本生成必须规划音效/环境声：翻纸声、脚步声、门铃、雨声、雷声、塑料碰撞声等要绑定到动作或场景，不得写成旁白。
4. 资产提取必须输出顶层 soundDesign：包含 voiceProfiles、narrationProfile、bgmPlan、sfxPlan；这是后期语音、音乐音效和混音成片的前置资产。
5. 每个分镜必须输出 bgmCue 和 sfxCues：bgmCue 写当前镜头继承/切换/静音的背景音乐意图；sfxCues 写与动作绑定的音效名称、触发点和音量倾向。
6. 剧本阶段只定义声音意图，后期语音只合成 dialogue 和明确需要播出的 voiceOver；BGM 与音效进入“音乐音效/混音成片”轨道，不得让分镜视频模型自行改写声音。
$rules$ AS block
)
UPDATE ai_prompt_template t
SET content = CASE
        WHEN t.content IS NULL OR btrim(t.content) = '' THEN sound_rules.block
        ELSE rtrim(t.content) || sound_rules.block
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM sound_rules
WHERE t.template_name IN (
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧分镜提取',
    'AI短剧分镜视频生成',
    'AI短剧后期语音合成'
)
AND (COALESCE(t.built_in, 0) = 1 OR COALESCE(t.tenant_id, 0) = 0)
AND COALESCE(t.content, '') NOT LIKE '%【20260610声音设计资产规则】%';

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText","soundDesign","bgmPlan","sfxPlan"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取'
  AND (COALESCE(built_in, 0) = 1 OR COALESCE(tenant_id, 0) = 0);

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText","soundDesign","bgmPlan","sfxPlan"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取'
  AND (COALESCE(built_in, 0) = 1 OR COALESCE(tenant_id, 0) = 0);

UPDATE ai_prompt_template
SET variables = '["projectName","style","ratio","resolution","durationSec","shotNo","cameraMove","actionDesc","dialogue","voiceOver","innerThought","emotion","bgmCue","sfxCues","characterAnchors","sceneAnchor","propAnchors","continuityRequirement","referenceAudioUrls","referenceVideoUrl"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成'
  AND (COALESCE(built_in, 0) = 1 OR COALESCE(tenant_id, 0) = 0);
