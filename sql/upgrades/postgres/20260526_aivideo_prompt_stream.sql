-- AI short-drama prompt streaming upgrade.
-- Idempotent. No API keys or secrets are stored here.

ALTER TABLE ai_prompt_template
    ADD COLUMN IF NOT EXISTS create_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS update_by VARCHAR(64);

UPDATE ai_prompt_template
SET create_by = COALESCE(create_by, 'system'),
    update_by = COALESCE(update_by, 'system')
WHERE create_by IS NULL
   OR update_by IS NULL;

UPDATE ai_prompt_template
SET content = $aivideo_polish$
# 顶级小说推文改文指令（纯文案版）

## 任务说明
你是一个顶级的小说推文改文专家。你的唯一任务是将小说原文改写为适合短视频配音的“第一人称解说文案”。要求全程紧扣完播率、转粉率两大核心，生成的文案需自带情绪、自带画面、自带悬念，可直接用于抖音/快手等短视频平台的小说推文配音与制作。

## 第一维度：爆款开头重构（最高优先级）
1. 严禁套用原文第一句，必须从原文核心冲突中重构开头。
2. 开头单段 30-50 字，严禁超过 55 字。
3. 1 秒抓住注意力，3 秒内抛出核心冲突或悬念。
4. 按网文类型匹配钩子：
   - 爽文/反套路文：优先使用经典反转钩、极致反差钩、身份错位钩。
   - 悬疑/灵异文：优先使用猎奇悬疑钩、夸张反常钩、结局先行钩。
   - 虐恋/情感文：优先使用人性抉择钩、荒诞现实钩。

## 第二维度：转换密度与节奏
1. 每 1000 字原文重构 38-40 段，误差不超过 5%。
2. 单段字数不超过 50 字，严禁出现 60 字以上长句。
3. 每段独立成意，适配短视频字幕和配音节奏。
4. 高潮、反转、情绪爆发段可以拆成 2 段，每段不少于 20 字。
5. 过渡段压缩至 15-20 字，保证节奏不拖沓。

## 第三维度：角色名称与视角
1. 自动识别原文角色，统一称呼格式。
2. 主角优先改为第一人称“我”，配角使用全名或昵称，避免他/她混淆。
3. 多角色同时出现时，优先标注核心角色名称，次要角色可在后续段落补全称呼。
4. 同一段落不得混用视角；切换视角时必须明确替换角色名称。

## 第四维度：台词格式升级
1. 台词必须使用：角色名说（情绪提示）：“台词内容”。
2. 情绪提示必须具体、具象，例如轻蔑地嗤笑、颤抖着嘶吼、温柔低吟、冷漠冰冷。
3. 多句连续台词可合并为一段，但必须标注整体情绪。
4. 简短对话拆分为独立段，增强节奏感。

## 第五维度：关联词与衔接
仅使用以下关联词，且同一复句或相邻段落不得重复：
- 基础词库：然而、却、不过、殊不知、岂料、果然、果不其然、谁知、哪料到、竟然、偏偏、不料、没想到。
- 进阶词库：即便……也、不但不……反而、之所以……是因为、由于……因此、由此可见。

## 第六维度：五感与画面落地
1. 先感知，后行动：人物动作、对话、内心活动前，优先加入视觉、听觉、触觉或嗅觉触发。
2. 每 3 段至少出现 1 种五感细节。
3. 视觉要具体到颜色、动作幅度、神态细节。
4. 听觉要包含拟声词、环境音或语气音。
5. 触觉要包含温度、压力、疼痛或材质触感。
6. 嗅觉要包含可感知气味。
7. 可使用起手式：见此情形、目睹、听到这话、感受着、指尖触到、鼻尖萦绕、映入眼帘、入耳皆是。

## 第七维度：详略得当
1. 必须详写关键对话、情绪爆发点、核心反转、角色神态。
2. 过渡性环境描写、重复动作、非核心角色行为用 1-2 句概括。
3. 每段至少包含 1 个可具象化的画面或动作细节。

## 第八维度：核心禁忌
1. 所有动作描写后尽量形成动作闭环，避免悬空动作。
2. 删除无意义语气词，只保留能增强情绪爆发的语气词。
3. 不要解释改写思路，不要输出分析过程。
4. 不要加入 BGM、音效、剪辑说明或视频制作备注。
5. 不得输出“以下是改写”之类的开场说明，直接输出正文。

## 第九维度：爆款钩子
1. 每段结尾尽量预留一个悬念点或情绪推进点。
2. 最后一段必须抛出追剧悬念，引导继续观看，但不要写生硬营销口号。

## 输出格式
1. 首段必须是重构后的爆款开头，单独成行。
2. 段与段之间空一行。
3. 台词按“角色名说（情绪提示）：“台词内容””格式单独成行。

## 验收标准
1. 开头 3 秒无废话，能快速抓住注意力。
2. 单段不超过 50 字，节奏适配配音。
3. 每 1000 字至少包含 3 个情绪爆发点。
4. 画面可直接转化为镜头。
5. 无原文第一句套用、无重复关联词、无冗余语气词、无视角混乱。

## 项目信息
项目：{{projectName}}
风格：{{style}}
目标平台：{{targetPlatform}}

## 原文
{{rawText}}
$aivideo_polish$,
    variables = '["projectName","style","targetPlatform","rawText"]',
    description = 'AI短剧原文润色默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧原文润色';

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧原文润色', 'aivideo_text',
$aivideo_polish$
# 顶级小说推文改文指令（纯文案版）

## 任务说明
你是一个顶级的小说推文改文专家。你的唯一任务是将小说原文改写为适合短视频配音的“第一人称解说文案”。要求紧扣完播率、转粉率，生成自带情绪、自带画面、自带悬念的短视频配音文案。

## 执行要求
1. 严禁套用原文第一句，必须重构 30-50 字爆款开头。
2. 每 1000 字原文重构 38-40 段，每段不超过 50 字。
3. 主角优先改为第一人称“我”，配角使用全名或昵称。
4. 台词格式：角色名说（情绪提示）：“台词内容”。
5. 每 3 段至少出现 1 种视觉、听觉、触觉或嗅觉细节。
6. 每段结尾尽量保留悬念点或情绪推进点。
7. 只输出润色后的正文，不输出分析、解释、BGM、音效或剪辑说明。

项目：{{projectName}}
风格：{{style}}
目标平台：{{targetPlatform}}

原文：
{{rawText}}
$aivideo_polish$,
'["projectName","style","targetPlatform","rawText"]', 'AI短剧原文润色默认长模板，来自AIVideo参考材料优化版', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧原文润色');

UPDATE ai_prompt_template
SET content = $aivideo_script$
# 短剧剧本与分镜拆解指令

你是顶级影视剧导演与分镜规划专家。请把润色文案改写为适合 AI 视频生成的短剧剧本，并为后续分镜拆解保留清晰结构。

## 输出要求
1. 按场次组织，每场包含人物、地点、光影、动作、对白、旁白和情绪提示。
2. 对白与旁白必须分开：角色直接说的话标注为“角色名说：“台词内容””；旁白、心理活动和氛围渲染标注为“（画外音：内容）”。
3. 场景延续时必须注明“延续上个分镜场景，机位微调”。
4. 动作要有衔接，前一镜头结尾姿态必须能触发后一镜头起始动作。
5. 画面不得凭空出现文案外人物、地点或道具。
6. 输出短剧剧本正文，不输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}

润色文本：
{{polishedText}}
$aivideo_script$,
    variables = '["projectName","targetPlatform","ratio","polishedText"]',
    description = 'AI短剧剧本生成默认模板，兼顾后续分镜拆解',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧剧本生成';

UPDATE ai_prompt_template
SET content = $aivideo_asset$
# AI短剧人物 / 场景 / 分镜提取指令

请严格依据下面三组参考提示词规则，从短剧剧本中提取【人物、场景、分镜】。
必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。
JSON key 必须保持英文，所有字段值必须使用中文。

## 角色构建规则
1. 你是电影级角色概念设计师，需要先解析角色心理画像：代号、生理年龄、性别、社会身份、人格标签、故事功能。
2. 每个角色必须输出鲜明、可区分的视觉方案：年龄、自然发色、具体发型、眼神神态、服装材质、主色辅色、鞋履配饰。
3. 多角色必须在主色调、款式剪裁、面料质感上显著区别，严禁视觉雷同。
4. promptText 要可直接用于角色图生成，包含横向 16:9、纯白极简背景、面部特写、全身正侧背三视图、固定自然站姿等关键信息。

## 电影级纯净场景规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。
3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。

## 剧本分镜规则
1. 你是顶级影视剧导演与分镜规划专家，需要面向 Seedance 2.0 / 即梦 2.0 的视频生成逻辑拆解镜头。
2. 全局禁止出现其他人；画面必须通过单人特写、主观视角或环境遮挡，把视觉重心锁定在当前核心主角。
3. 严格区分 dialogue 和 voiceOver：角色直接说的话写入 dialogue；旁白、心理活动、环境氛围写入 voiceOver。
4. 每个分镜必须明确地点；延续场景时在 sceneName 或 actionDesc 中体现“延续上个分镜场景，机位微调”。
5. 动作要衔接，不能瞬移；镜头需包含微动作、眼神、呼吸、肢体、环境变化等可拍内容。
6. shotType、cameraPosition、cameraMovement 要优先使用专业运镜词，如极焦特写、近景推轨、环绕摇镜、慢动作/延时、手持震动。
7. durationSec 使用项目镜头秒数：{{defaultShotDuration}}；如果剧情确实需要短镜头，也不得低于 3 秒。

## 输出 JSON 结构
{
  "characters": [
    {
      "characterName": "",
      "gender": "",
      "ageDesc": "",
      "identityDesc": "",
      "personalityTags": [""],
      "storyRole": "",
      "relationshipDesc": "",
      "appearance": "",
      "hairStyle": "",
      "costume": "",
      "colorStyle": "",
      "negativeTraits": "",
      "promptText": "",
      "completeness": "",
      "missingFields": [""]
    }
  ],
  "scenes": [
    {
      "sceneName": "",
      "sceneType": "",
      "episodeNo": 1,
      "timeDesc": "",
      "weather": "",
      "atmosphere": "",
      "visualFeatures": "",
      "colorTone": "",
      "props": "",
      "negativeElements": "",
      "promptText": "",
      "completeness": "",
      "missingFields": [""]
    }
  ],
  "shots": [
    {
      "episodeNo": 1,
      "shotNo": 1,
      "durationSec": {{defaultShotDuration}},
      "sceneName": "",
      "characterNames": [""],
      "shotType": "",
      "cameraPosition": "",
      "cameraMovement": "",
      "actionDesc": "",
      "dialogue": "",
      "voiceOver": "",
      "emotion": "",
      "promptText": ""
    }
  ]
}

## 项目信息
项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
风格：{{style}}
默认镜头秒数：{{defaultShotDuration}}

## 剧本
{{scriptText}}
$aivideo_asset$,
    variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText"]',
    description = 'AI短剧人物场景分镜提取默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取';

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧资产提取', 'aivideo_text',
$aivideo_asset$
# AI短剧人物 / 场景 / 分镜提取指令

请严格依据角色构建、电影级纯净场景、剧本分镜三组参考规则，从短剧剧本中提取人物、场景、分镜。
必须只输出 JSON 对象；JSON key 必须保持英文，所有字段值必须使用中文。
角色 promptText 必须可直接用于人物图生成；场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头；分镜必须区分 dialogue 与 voiceOver。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
风格：{{style}}
默认镜头秒数：{{defaultShotDuration}}

剧本：
{{scriptText}}
$aivideo_asset$,
'["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText"]', 'AI短剧人物场景分镜提取默认长模板，来自AIVideo参考材料优化版', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧资产提取');

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧资产提取') AS asset_id
    FROM ai_prompt_template
    WHERE template_name IN ('AI短剧原文润色', 'AI短剧剧本生成', 'AI短剧资产提取')
),
tenant_ids AS (
    SELECT 0::BIGINT AS tenant_id
    UNION
    SELECT DISTINCT tenant_id FROM ai_video_project WHERE tenant_id IS NOT NULL
    UNION
    SELECT DISTINCT tenant_id FROM ai_video_project_setting WHERE tenant_id IS NOT NULL
)
INSERT INTO ai_video_project_setting (
    project_id, tenant_id, polish_prompt_template_id, script_prompt_template_id,
    character_prompt_template_id, scene_prompt_template_id, shot_prompt_template_id,
    default_ratio, default_resolution, default_shot_duration,
    image_candidate_count, video_candidate_count, preview_mode, content_audit_enabled,
    create_by, create_time, update_by, update_time
)
SELECT NULL, tenant_ids.tenant_id, tpl.polish_id, tpl.script_id,
       tpl.asset_id, tpl.asset_id, tpl.asset_id,
       '9:16', '720p', 5, 3, 1, '1', '1',
       'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
FROM tenant_ids CROSS JOIN tpl
WHERE tpl.polish_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM ai_video_project_setting s
      WHERE s.project_id IS NULL AND COALESCE(s.tenant_id, 0) = COALESCE(tenant_ids.tenant_id, 0)
  );

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧资产提取') AS asset_id
    FROM ai_prompt_template
    WHERE template_name IN ('AI短剧原文润色', 'AI短剧剧本生成', 'AI短剧资产提取')
)
UPDATE ai_video_project_setting s
SET polish_prompt_template_id = COALESCE(s.polish_prompt_template_id, tpl.polish_id),
    script_prompt_template_id = COALESCE(s.script_prompt_template_id, tpl.script_id),
    character_prompt_template_id = COALESCE(s.character_prompt_template_id, tpl.asset_id),
    scene_prompt_template_id = COALESCE(s.scene_prompt_template_id, tpl.asset_id),
    shot_prompt_template_id = COALESCE(s.shot_prompt_template_id, tpl.asset_id),
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.polish_id IS NOT NULL
  AND (
      s.polish_prompt_template_id IS NULL
      OR s.script_prompt_template_id IS NULL
      OR s.character_prompt_template_id IS NULL
      OR s.scene_prompt_template_id IS NULL
      OR s.shot_prompt_template_id IS NULL
  );
