-- AI short-drama scene image prompt default upgrade.
-- Idempotent. No API keys or secrets are stored here.

WITH updated AS (
    UPDATE ai_prompt_template
    SET category = 'aivideo_image',
        content = $aivideo_scene_image$
不能出现其他人, 无人, 纯场景, no humans, empty, landscape only。

电影级纯净场景设计专家（高辨识度版）默认提示词。
请生成一张短剧可用的纯净场景图：绝对无人、无人物、无人物剪影、无脸、无身体部位，画面中不能出现任何角色名或角色痕迹。

## 核心执行逻辑
1. 绝对真空与匿名：画面中严禁出现任何人影，提示词中严禁出现任何角色人名。
2. 场景命名法则：场景必须具备辨识度，避免单一名词。
3. 四大核心要素：必须完整涵盖环境类型、具体时间、空间氛围、视觉主要特征。
4. Prompt 开头必须保留“不能出现其他人, 无人, 纯场景,”。
5. 输出控制：不要生成解释、不要生成括号说明，直接生成图片画面。

## 场景设定
项目：{{projectName}}
目标平台：{{targetPlatform}}
视觉风格：{{style}}
画幅构图：{{ratio}} 电影级场景设定图，极高画质，纯净无人的空间
清晰度：{{resolution}}

场景名称：{{sceneName}}
环境类型：{{sceneType}}
时间时刻：{{timeDesc}}
天气光线：{{weather}}
空间氛围：{{atmosphere}}
主要特征：{{visualFeatures}}
建议色调：{{colorTone}}
核心道具：{{props}}
禁用元素：{{negativeElements}}
原始场景提示词：{{scenePromptText}}

## 画面要求
将以上环境细节融合成一段精简、极具冲击力的生图描述词；前景、中景、远景空间关系清晰，主体环境明确，可作为后续分镜视频背景；严禁出现人、人物剪影、脸、身体部位、crowd、person、human。
$aivideo_scene_image$,
        variables = '["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
        description = 'AI短剧场景图生成默认模板，来自电影级纯净场景设计专家参考词，强制纯场景无人',
        built_in = 1,
        status = '0',
        update_by = 'system',
        update_time = CURRENT_TIMESTAMP
    WHERE template_name = 'AI短剧场景图生成'
    RETURNING template_id
),
inserted AS (
    INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status, create_by, create_time, update_by, update_time)
    SELECT NULL, 'AI短剧场景图生成', 'aivideo_image',
           $aivideo_scene_image$
不能出现其他人, 无人, 纯场景, no humans, empty, landscape only。

电影级纯净场景设计专家（高辨识度版）默认提示词。
请生成一张短剧可用的纯净场景图：绝对无人、无人物、无人物剪影、无脸、无身体部位，画面中不能出现任何角色名或角色痕迹。

## 核心执行逻辑
1. 绝对真空与匿名：画面中严禁出现任何人影，提示词中严禁出现任何角色人名。
2. 场景命名法则：场景必须具备辨识度，避免单一名词。
3. 四大核心要素：必须完整涵盖环境类型、具体时间、空间氛围、视觉主要特征。
4. Prompt 开头必须保留“不能出现其他人, 无人, 纯场景,”。
5. 输出控制：不要生成解释、不要生成括号说明，直接生成图片画面。

## 场景设定
项目：{{projectName}}
目标平台：{{targetPlatform}}
视觉风格：{{style}}
画幅构图：{{ratio}} 电影级场景设定图，极高画质，纯净无人的空间
清晰度：{{resolution}}

场景名称：{{sceneName}}
环境类型：{{sceneType}}
时间时刻：{{timeDesc}}
天气光线：{{weather}}
空间氛围：{{atmosphere}}
主要特征：{{visualFeatures}}
建议色调：{{colorTone}}
核心道具：{{props}}
禁用元素：{{negativeElements}}
原始场景提示词：{{scenePromptText}}

## 画面要求
将以上环境细节融合成一段精简、极具冲击力的生图描述词；前景、中景、远景空间关系清晰，主体环境明确，可作为后续分镜视频背景；严禁出现人、人物剪影、脸、身体部位、crowd、person、human。
$aivideo_scene_image$,
           '["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
           'AI短剧场景图生成默认模板，来自电影级纯净场景设计专家参考词，强制纯场景无人',
           1, '0', 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM updated)
      AND NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景图生成')
    RETURNING template_id
),
tpl AS (
    SELECT template_id FROM updated
    UNION ALL
    SELECT template_id FROM inserted
    UNION ALL
    SELECT template_id FROM ai_prompt_template
    WHERE template_name = 'AI短剧场景图生成'
    LIMIT 1
)
UPDATE ai_video_project_setting s
SET scene_image_prompt_template_id = tpl.template_id,
    image_candidate_count = CASE
        WHEN s.project_id IS NULL OR COALESCE(s.image_candidate_count, 0) = 3 THEN 2
        ELSE s.image_candidate_count
    END,
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.template_id IS NOT NULL
  AND (
      s.scene_image_prompt_template_id IS NULL
      OR s.scene_image_prompt_template_id <> tpl.template_id
      OR s.project_id IS NULL
      OR COALESCE(s.image_candidate_count, 0) = 3
  );
