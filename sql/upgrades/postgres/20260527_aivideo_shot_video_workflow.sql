-- AI short-drama MVP3 shot video workflow upgrade.
-- Idempotent. No API keys or secrets are stored here.

ALTER TABLE ai_video_project_setting
    ADD COLUMN IF NOT EXISTS video_prompt_template_id BIGINT;

ALTER TABLE ai_video_project_setting
    ALTER COLUMN video_candidate_count SET DEFAULT 1;

WITH updated AS (
    UPDATE ai_prompt_template
    SET category = 'aivideo_video',
        content = $aivideo_shot_video$
# AI短剧单分镜视频生成默认模板

你是电影级短剧分镜视频导演。请基于已经确认的场景图和单条分镜信息，生成适合视频模型的图生视频提示词。

## 核心目标
1. 只生成当前单个镜头，不生成整剧，不跨镜头扩写。
2. 必须保持参考场景图的空间关系、时间、天气、色调和主体环境稳定。
3. 以“可拍摄、可剪辑、可复现”为优先，动作、运镜、情绪和光影要清晰。
4. 不生成字幕、水印、logo、花字、说明文字或与剧情无关的元素。
5. 若参考图是纯场景，允许根据分镜描述加入必要角色运动；若只需要空镜，明确“保持无人纯场景”。

## 视频生成要求
- 画幅：{{ratio}}
- 清晰度：{{resolution}}
- 时长：{{durationSec}} 秒
- 参考图：{{referenceImageUrl}}
- 项目：{{projectName}}
- 目标平台：{{targetPlatform}}
- 整体风格：{{style}}

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
        variables = '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl"]',
        description = 'AI短剧分镜视频生成默认模板，服务于 MVP3 单分镜图生视频',
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

你是电影级短剧分镜视频导演。请基于已经确认的场景图和单条分镜信息，生成适合视频模型的图生视频提示词。

## 核心目标
1. 只生成当前单个镜头，不生成整剧，不跨镜头扩写。
2. 必须保持参考场景图的空间关系、时间、天气、色调和主体环境稳定。
3. 以“可拍摄、可剪辑、可复现”为优先，动作、运镜、情绪和光影要清晰。
4. 不生成字幕、水印、logo、花字、说明文字或与剧情无关的元素。
5. 若参考图是纯场景，允许根据分镜描述加入必要角色运动；若只需要空镜，明确“保持无人纯场景”。

## 视频生成要求
- 画幅：{{ratio}}
- 清晰度：{{resolution}}
- 时长：{{durationSec}} 秒
- 参考图：{{referenceImageUrl}}
- 项目：{{projectName}}
- 目标平台：{{targetPlatform}}
- 整体风格：{{style}}

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
           '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl"]',
           'AI短剧分镜视频生成默认模板，服务于 MVP3 单分镜图生视频',
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
    video_candidate_count = CASE
        WHEN COALESCE(s.video_candidate_count, 0) < 1 THEN 1
        ELSE s.video_candidate_count
    END,
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.template_id IS NOT NULL
  AND (
      s.video_prompt_template_id IS NULL
      OR s.video_prompt_template_id <> tpl.template_id
      OR COALESCE(s.video_candidate_count, 0) < 1
  );
