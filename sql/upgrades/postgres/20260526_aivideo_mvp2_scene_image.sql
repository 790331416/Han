-- AI short-drama MVP2 scene image generation upgrade.
-- Idempotent. No API keys or secrets are stored here.

ALTER TABLE ai_video_project_setting
    ADD COLUMN IF NOT EXISTS scene_image_prompt_template_id BIGINT;

ALTER TABLE ai_video_project_setting
    ALTER COLUMN image_candidate_count SET DEFAULT 2;

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧场景图生成', 'aivideo_image',
$$# AI短剧电影级纯净场景图生成

请基于以下场景信息生成一张短剧可用的电影级纯净场景图。

## 强制规则
1. 纯场景、无人、无人物、无人物剪影、无脸、无身体部位，不出现任何角色。
2. 画面必须可作为后续分镜视频背景，空间关系清晰，主体环境明确。
3. 保留场景气氛、时间、天气、色调、道具和视觉特征。
4. 必须包含 no humans, empty scene, landscape only。
5. 不输出解释，只输出可直接用于图片模型的中文提示词。

项目：{{projectName}}
目标平台：{{targetPlatform}}
风格：{{style}}
画幅：{{ratio}}
清晰度：{{resolution}}

场景名称：{{sceneName}}
场景类型：{{sceneType}}
时间：{{timeDesc}}
天气：{{weather}}
氛围：{{atmosphere}}
视觉特征：{{visualFeatures}}
色调：{{colorTone}}
道具：{{props}}
禁用元素：{{negativeElements}}
原始场景提示词：{{scenePromptText}}$$,
'["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
'AI短剧场景图生成默认模板，强制纯场景无人，服务于 MVP2 单场景候选图', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景图生成');

WITH tpl AS (
    SELECT MAX(template_id) AS scene_image_id
    FROM ai_prompt_template
    WHERE template_name = 'AI短剧场景图生成'
)
UPDATE ai_video_project_setting s
SET scene_image_prompt_template_id = tpl.scene_image_id,
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.scene_image_id IS NOT NULL
  AND s.scene_image_prompt_template_id IS NULL;

UPDATE ai_video_project_setting
SET image_candidate_count = 2,
    update_time = CURRENT_TIMESTAMP
WHERE project_id IS NULL
  AND COALESCE(image_candidate_count, 0) <> 2;
