-- AI short-drama shot video continuity upgrade.
-- Adds tail-frame retention and strengthens the default shot-video prompt.

ALTER TABLE ai_video_shot
    ADD COLUMN IF NOT EXISTS tail_frame_media_id BIGINT;

COMMENT ON COLUMN ai_video_shot.tail_frame_media_id IS 'Selected shot tail-frame reference media ID';

ALTER TABLE ai_prompt_template
    ALTER COLUMN variables TYPE TEXT;

WITH updated AS (
    UPDATE ai_prompt_template
    SET category = 'aivideo_video',
        content = $aivideo_shot_video$
# AI短剧单分镜视频生成默认模板

你是电影级短剧分镜视频导演。请基于已经确认的参考图和单条分镜信息，生成适合视频模型的图生视频提示词。

## 核心目标
1. 只生成当前单个镜头，不生成整剧，不跨镜头扩写。
2. 必须严格执行镜头连续性协议：上一分镜确认后，系统会留存尾帧参考图；下一分镜必须从上一分镜尾帧状态起步。
3. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定；若参考图是上一分镜尾帧，主体位置、姿态、朝向、光影要优先继承。
4. 以“可拍摄、可剪辑、可复现”为优先，动作、运镜、情绪和光影要清晰。
5. 不生成字幕、水印、logo、花字、说明文字或与剧情无关的元素。
6. 遇到“悬浮、飞起、变身、倒地、站起”等强动作词，除非分镜明确高速飞行，否则默认只做缓慢、低幅度、原地附近变化。

## 视频生成要求
- 画幅：{{ratio}}
- 清晰度：{{resolution}}
- 时长：{{durationSec}} 秒
- 参考图：{{referenceImageUrl}}
- 参考图类型：{{referenceFrameType}}
- 项目：{{projectName}}
- 目标平台：{{targetPlatform}}
- 整体风格：{{style}}

## 镜头连续性协议（强制）
- 上一分镜编号：{{previousShotNo}}
- 上一分镜摘要：{{previousShotSummary}}
- 上一分镜结束状态：{{previousEndState}}
- 上一分镜尾帧参考图：{{previousTailFrameUrl}}
- 当前分镜起始状态：{{currentStartState}}
- 当前分镜结束状态：{{currentEndState}}
- 动作边界：{{motionBoundary}}
- 连贯性负面约束：{{continuityNegativePrompt}}

执行要求：
1. 当前镜头第一帧必须贴合“当前分镜起始状态”，不能突然跳到动作中段。
2. 如果存在上一分镜尾帧参考图，必须把它视作当前镜头第一帧构图参考。
3. 如果没有尾帧参考图，也必须按上一分镜结束状态推断衔接，不能无故切换主体位置或姿态。
4. 当前镜头只推进本分镜动作，不提前演到后续镜头。

## 分镜信息
- 集数：{{episodeNo}}
- 镜头号：{{shotNo}}
- 场景名称：{{sceneName}}
- 场景类型：{{sceneType}}
- 时间：{{sceneTime}}
- 天气：{{weather}}
- 氛围：{{atmosphere}}
- 视觉特征：{{visualFeatures}}
- 出场角色：{{characterNames}}
- 景别/镜头类型：{{shotType}}
- 机位：{{cameraPosition}}
- 运镜：{{cameraMovement}}
- 动作：{{actionDesc}}
- 对白：{{dialogue}}
- 旁白：{{voiceOver}}
- 情绪：{{emotion}}
- 原始分镜提示词：{{shotPromptText}}

## 输出格式
请直接输出一段视频模型可用的中文提示词。结构建议：
1. 参考图保持要求。
2. 镜头起始画面。
3. 角色或环境动作。
4. 运镜方式。
5. 光影、氛围和情绪。
6. 负面约束：无字幕、无水印、无 logo、无花字、无无关文字。
$aivideo_shot_video$,
        variables = '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl","referenceFrameType","previousShotNo","previousShotSummary","previousEndState","previousTailFrameUrl","currentStartState","currentEndState","motionBoundary","continuityNegativePrompt","candidateCount"]',
        description = 'AI短剧分镜视频生成默认模板，增加尾帧衔接和单分镜强连续性约束',
        built_in = 1,
        status = '0',
        update_by = 'system',
        update_time = CURRENT_TIMESTAMP
    WHERE template_name = 'AI短剧分镜视频生成'
    RETURNING template_id
),
inserted AS (
    INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status, create_by, create_time, update_by, update_time)
    SELECT NULL, 'AI短剧分镜视频生成', 'aivideo_video',
           $aivideo_shot_video$
# AI短剧单分镜视频生成默认模板

你是电影级短剧分镜视频导演。请基于已经确认的参考图和单条分镜信息，生成适合视频模型的图生视频提示词。

## 核心目标
1. 只生成当前单个镜头，不生成整剧，不跨镜头扩写。
2. 必须严格执行镜头连续性协议：上一分镜确认后，系统会留存尾帧参考图；下一分镜必须从上一分镜尾帧状态起步。
3. 必须保持参考图的空间关系、时间、天气、色调和主体环境稳定；若参考图是上一分镜尾帧，主体位置、姿态、朝向、光影要优先继承。
4. 以“可拍摄、可剪辑、可复现”为优先，动作、运镜、情绪和光影要清晰。
5. 不生成字幕、水印、logo、花字、说明文字或与剧情无关的元素。
6. 遇到“悬浮、飞起、变身、倒地、站起”等强动作词，除非分镜明确高速飞行，否则默认只做缓慢、低幅度、原地附近变化。

## 视频生成要求
- 画幅：{{ratio}}
- 清晰度：{{resolution}}
- 时长：{{durationSec}} 秒
- 参考图：{{referenceImageUrl}}
- 参考图类型：{{referenceFrameType}}
- 项目：{{projectName}}
- 目标平台：{{targetPlatform}}
- 整体风格：{{style}}

## 镜头连续性协议（强制）
- 上一分镜编号：{{previousShotNo}}
- 上一分镜摘要：{{previousShotSummary}}
- 上一分镜结束状态：{{previousEndState}}
- 上一分镜尾帧参考图：{{previousTailFrameUrl}}
- 当前分镜起始状态：{{currentStartState}}
- 当前分镜结束状态：{{currentEndState}}
- 动作边界：{{motionBoundary}}
- 连贯性负面约束：{{continuityNegativePrompt}}

执行要求：
1. 当前镜头第一帧必须贴合“当前分镜起始状态”，不能突然跳到动作中段。
2. 如果存在上一分镜尾帧参考图，必须把它视作当前镜头第一帧构图参考。
3. 如果没有尾帧参考图，也必须按上一分镜结束状态推断衔接，不能无故切换主体位置或姿态。
4. 当前镜头只推进本分镜动作，不提前演到后续镜头。

## 分镜信息
- 集数：{{episodeNo}}
- 镜头号：{{shotNo}}
- 场景名称：{{sceneName}}
- 场景类型：{{sceneType}}
- 时间：{{sceneTime}}
- 天气：{{weather}}
- 氛围：{{atmosphere}}
- 视觉特征：{{visualFeatures}}
- 出场角色：{{characterNames}}
- 景别/镜头类型：{{shotType}}
- 机位：{{cameraPosition}}
- 运镜：{{cameraMovement}}
- 动作：{{actionDesc}}
- 对白：{{dialogue}}
- 旁白：{{voiceOver}}
- 情绪：{{emotion}}
- 原始分镜提示词：{{shotPromptText}}

## 输出格式
请直接输出一段视频模型可用的中文提示词。结构建议：
1. 参考图保持要求。
2. 镜头起始画面。
3. 角色或环境动作。
4. 运镜方式。
5. 光影、氛围和情绪。
6. 负面约束：无字幕、无水印、无 logo、无花字、无无关文字。
$aivideo_shot_video$,
           '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl","referenceFrameType","previousShotNo","previousShotSummary","previousEndState","previousTailFrameUrl","currentStartState","currentEndState","motionBoundary","continuityNegativePrompt","candidateCount"]',
           'AI短剧分镜视频生成默认模板，增加尾帧衔接和单分镜强连续性约束',
           1, '0', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM updated)
      AND NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧分镜视频生成')
    RETURNING template_id
),
tpl AS (
    SELECT template_id FROM updated
    UNION ALL
    SELECT template_id FROM inserted
    UNION ALL
    SELECT template_id FROM ai_prompt_template
    WHERE template_name = 'AI短剧分镜视频生成'
    LIMIT 1
)
UPDATE ai_video_project_setting s
SET video_prompt_template_id = tpl.template_id,
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.template_id IS NOT NULL
  AND (s.video_prompt_template_id IS NULL OR s.video_prompt_template_id <> tpl.template_id);
