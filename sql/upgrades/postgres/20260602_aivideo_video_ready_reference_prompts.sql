-- AI short-drama video-ready image reference prompts.
-- Align character and scene image prompts with Seedance video generation needs:
-- single-subject character anchors and single-shot scene anchors.

BEGIN;

UPDATE ai_prompt_template
SET content = $aivideo_scene_image$
不能出现其他人, 无人, 纯场景, no humans, empty scene, single shot reference。

Seedance 视频生成专用场景参考图默认提示词。
请生成一张可作为视频首帧/环境锚点的单镜头纯净场景图：绝对无人、无人物、无人物剪影、无脸、无身体部位，画面中不能出现任何角色名或角色痕迹。

## 核心执行逻辑
1. 绝对真空与匿名：画面中严禁出现任何人影，提示词中严禁出现任何角色人名。
2. 场景命名法则：场景必须具备辨识度，避免单一名词。
3. 四大核心要素：必须完整涵盖环境类型、具体时间、空间氛围、视觉主要特征。
4. 视频参考图硬规则：只允许单一镜头画面，禁止拼图、分栏、设定板、地图、俯视平面图、漫画格、文字、水印、logo 或说明标签。
5. Prompt 开头必须保留“不能出现其他人, 无人, 纯场景,”，并包含 no humans、empty scene、single shot reference。
6. 输出控制：不要生成解释、不要生成括号说明，直接生成图片画面。

## 场景设定
项目：{{projectName}}
目标平台：{{targetPlatform}}
视觉风格：{{style}}
画幅构图：{{ratio}} Seedance 视频场景参考图，单镜头画面，极高画质，纯净无人的空间
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
将以上环境细节融合成一段精简、极具冲击力的生图描述词；前景、中景、远景空间关系清晰，地面或可行动区域明确，主光源方向和色调稳定，可作为后续 Seedance 分镜视频首帧/背景锚点；严禁出现人、人物剪影、脸、身体部位、crowd、person、human；严禁拼图、分栏、设定板、漫画格和文字标签。
$aivideo_scene_image$,
    variables = '["projectName","targetPlatform","style","ratio","resolution","sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText"]',
    description = 'AI短剧场景图生成默认模板，强制纯场景无人和单镜头视频环境锚点',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧场景图生成';

UPDATE ai_prompt_template
SET content = $aivideo_character_image$
# AI短剧视频角色锚定图生成默认模板

【系统自动化适配规则】
1. 你是 Seedance 视频生成专用角色参考图设计专家，只输出适合图片模型执行的角色图提示词。
2. 只生成单一主体视频角色锚定图，不生成群像、不生成同款分身、不出现额外人物、文字、水印、logo。
3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保持物种本体，不要改成人类演员、真人脸或人类身体。
4. 构图硬规则：只输出单一镜头里的 3/4 正面或轻微侧正面自然站姿，主体居中，全身完整可见，主体占画面高度 60%-75%。
5. 视频参考硬规则：禁止四方向、三视图、多视图、转面表、分栏、拼图、同款分身、多个角度并排，避免视频模型误识别成多个主体。
6. 全身硬规则：必须完整露出头部/脸部、躯干、四肢/爪子/脚、尾巴或标志性部位；禁止只画头部、禁止半身、禁止身体裁切。
7. 一致性硬规则：突出 2-3 个稳定外观特征，保持同一体型、年龄阶段、物种/品种、毛色/发型、服饰/身体特征、斑纹、光照和比例。
8. 历史版式屏蔽规则：历史输入里的头像、半身、三视图、四方向、正侧背版式只用于识别无效构图，不进入最终构图；最终只允许单主体视频角色锚定图。
9. 直接输出图片提示词，不输出解释。

【当前任务变量】
项目：{{projectName}}
风格：{{style}}
画幅：{{ratio}}
清晰度：{{resolution}}
角色名称：{{characterName}}
性别/物种：{{gender}}
年龄/阶段：{{ageDesc}}
身份定位：{{identityDesc}}
剧情定位：{{storyRole}}
性格标签：{{personalityTags}}
形象描述：{{appearance}}
毛发/发型：{{hairStyle}}
服饰/身体特征：{{costume}}
色彩风格：{{colorStyle}}
净化后的角色外观提示词：{{characterPromptText}}
参考图 URL：{{referenceImageUrl}}
$aivideo_character_image$,
    variables = '["projectName","style","ratio","resolution","characterName","gender","ageDesc","identityDesc","storyRole","personalityTags","appearance","hairStyle","costume","colorStyle","characterPromptText","referenceImageUrl"]',
    description = 'AI短剧角色图生成默认模板，强制单主体视频角色锚定图和旧词屏蔽',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色图生成';

UPDATE ai_prompt_template
SET content = replace(
        replace(
            replace(
                replace(
                    content,
                    'promptText 必须可直接用于角色图生成，写成：单一角色、纯白极简背景、四方向全身转面表，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。',
                    'promptText 必须可直接用于 Seedance 视频角色锚定图生成，写成：单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。'
                ),
                'promptText 要可直接用于角色图生成，必须写成四方向全身转面表：纯白极简背景，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。',
                'promptText 要可直接用于 Seedance 视频角色锚定图生成，必须写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。'
            ),
            'promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图；动物保持自然四足站立，不拟人化。',
            'promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身；动物保持自然四足站立，不拟人化。'
        ),
        'promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图。',
        'promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身。'
    ),
    description = CASE
        WHEN template_name = 'AI短剧角色构建' THEN 'AI短剧角色构建默认模板，强制角色图锚点使用单主体视频参考图'
        ELSE description
    END,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name IN ('AI短剧角色构建', 'AI短剧资产提取');

UPDATE ai_prompt_template
SET content = replace(
        replace(
            replace(
                replace(
                    replace(
                        content,
                        '## 电影级纯净场景规则',
                        '## Seedance 视频场景锚点规则'
                    ),
                    '场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。',
                    '场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference。' || E'\n' ||
                    '5. 场景 promptText 必须写成单镜头视频首帧/环境锚点：前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。'
                ),
                'no humans, empty, landscape only',
                'no humans, empty scene, single shot reference'
            ),
            'Role: 电影级纯净场景设计专家（高辨识度版）',
            'Role: Seedance 视频生成专用场景参考图设计专家'
        ),
        '完整遵守上方“电影级纯净场景设计专家（高辨识度版）”提示词',
        '完整遵守上方“Seedance 视频生成专用场景参考图设计专家”提示词'
    ),
    description = CASE
        WHEN template_name = 'AI短剧场景设计' THEN 'AI短剧场景设计默认模板，强制单镜头视频环境锚点'
        ELSE description
    END,
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name IN ('AI短剧资产提取', 'AI短剧场景设计', 'AI短剧分镜提取');

UPDATE ai_prompt_template
SET content = replace(
        replace(
            content,
            '- 角色一致性：{{characterContinuity}}',
            '- 角色一致性：{{characterContinuity}}' || E'\n' ||
            '- 角色锚定图使用规则：角色图只用于锁定身份、体型、毛色/服饰和标志物；不得把白底/浅灰棚拍背景带入剧情场景，不得把单主体锚定图复制成多只同款主体。'
        ),
        '- 角色一致性锚点：{{characterContinuity}}',
        '- 角色一致性锚点：{{characterContinuity}}' || E'\n' ||
        '- 角色锚定图使用规则：角色图只用于锁定身份、体型、毛色/服饰和标志物；不得把白底/浅灰棚拍背景带入剧情场景，不得把单主体锚定图复制成多只同款主体。'
    ),
    description = 'AI短剧分镜视频生成执行模板，增加动作节拍、构图部位锁定、禁用自动配音和角色锚定图使用边界',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成'
  AND content NOT LIKE '%角色锚定图使用规则：角色图只用于锁定身份%';

COMMIT;
