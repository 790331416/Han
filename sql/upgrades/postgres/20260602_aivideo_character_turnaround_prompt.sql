-- AI short video character turnaround prompt hardening.
-- This script updates existing prompt templates; it does not mutate generated assets.

BEGIN;

UPDATE ai_prompt_template
SET content = $aivideo_character$
# AI短剧角色构建默认模板

【系统自动化适配规则】
1. 你是电影级角色资产规划师，只负责从剧本中提取稳定角色锚点，并输出可入库 JSON。
2. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或表格外解释。
3. JSON key 必须保持英文，所有字段值必须使用中文。
4. 先解析角色画像：代号、年龄/生命阶段、性别或物种、身份、人格标签、故事功能。
5. 人类角色写清年龄、自然发色、具体发型、眼神神态、服装材质、主色辅色、鞋履配饰。
6. 动物、宠物、怪物、机器人、器物精灵等非人类角色必须保留物种本体，写清品种/体型/毛色/眼睛/鼻子/耳朵/尾巴/标志性特征，禁止改成人类演员。
7. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。
8. promptText 必须可直接用于角色图生成，写成：单一角色、纯白极简背景、四方向全身转面表，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。
9. promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图；动物保持自然四足站立，不拟人化。
10. 无法从剧本确认的信息写入 missingFields，不要编造关键事实。

【当前任务变量】
项目：{{projectName}}
世界观风格：{{style}}
目标平台：{{targetPlatform}}
角色文案/剧本：
{{scriptText}}
$aivideo_character$,
    variables = '["projectName","style","targetPlatform","scriptText"]',
    description = 'AI短剧角色构建默认模板，强制角色图锚点使用四方向全身转面表',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色构建';

UPDATE ai_prompt_template
SET content = $aivideo_character_image$
# AI短剧角色四方向全身转面图生成默认模板

【系统自动化适配规则】
1. 你是电影级角色四方向全身转面设定图设计专家，只输出适合图片模型执行的角色图提示词。
2. 只生成单一角色，不生成群像，不出现额外人物、文字、水印、logo。
3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保持物种本体，不要改成人类演员、真人脸或人类身体。
4. 构图硬规则：输出一张标准角色转面表，同一画布，纯白或浅灰极简背景，四个等宽分区从左到右依次为“正面、左侧面、右侧面、背面”。
5. 全身硬规则：四个方向都必须完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位；禁止只画头部、禁止半身、禁止身体裁切、禁止用大头特写替代全身视图。
6. 一致性硬规则：四个方向必须是同一角色，保持同一体型、年龄阶段、物种/品种、毛色/发型、服饰/身体特征、斑纹、光照和比例。
7. 旧词屏蔽规则：如果原始角色提示词或补充要求里出现“头部特写、面部特写、三视图、正侧背”等旧版版式，只提取身份和外观特征，不执行旧版构图；最终仍以四方向全身转面表为最高优先级。
8. 直接输出图片提示词，不输出解释。

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
原始角色提示词：{{characterPromptText}}
参考图 URL：{{referenceImageUrl}}
$aivideo_character_image$,
    variables = '["projectName","style","ratio","resolution","characterName","gender","ageDesc","identityDesc","storyRole","personalityTags","appearance","hairStyle","costume","colorStyle","characterPromptText","referenceImageUrl"]',
    description = 'AI短剧角色图生成默认模板，强制四方向全身转面表和旧词屏蔽',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色图生成';

UPDATE ai_prompt_template
SET content = replace(content,
        '5. promptText 要可直接用于角色图生成，包含单一角色、纯白极简背景、头部/面部特写、全身正侧背三视图、固定自然站姿或动物自然姿态等关键信息。',
        '5. promptText 要可直接用于角色图生成，必须写成单一角色、纯白极简背景、四方向全身转面表，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。' || E'\n' ||
        '6. promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图；动物保持自然四足站立，不拟人化。'),
    description = 'AI短剧分镜提取默认模板，增加动作预算、动态时长、部位锁定和角色四方向全身转面锚点',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取'
  AND content LIKE '%头部/面部特写%'
  AND content LIKE '%全身正侧背三视图%';

UPDATE ai_prompt_template
SET content = replace(content,
        '3. promptText 要可直接用于角色图生成，包含横向 16:9、纯白极简背景、面部特写、全身正侧背三视图、固定自然站姿等关键信息。',
        '3. promptText 要可直接用于角色图生成，必须写成四方向全身转面表：纯白极简背景，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。' || E'\n' ||
        '4. promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图。'),
    description = 'AI短剧角色场景分镜提取默认长模板，强制角色图锚点使用四方向全身转面表',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取'
  AND content LIKE '%面部特写%'
  AND content LIKE '%全身正侧背三视图%';

UPDATE ai_prompt_template
SET content = replace(content,
        '4. promptText 要可直接用于角色图生成，包含横向 16:9、纯白极简背景、面部特写、全身正侧背三视图、固定自然站姿等关键信息。',
        '4. promptText 要可直接用于角色图生成，必须写成四方向全身转面表：纯白极简背景，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。' || E'\n' ||
        '5. promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图。'),
    description = 'AI短剧资产提取默认模板，强制角色图锚点使用四方向全身转面表',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取'
  AND content LIKE '%面部特写%'
  AND content LIKE '%全身正侧背三视图%';

UPDATE ai_prompt_template
SET content = replace(content,
        '5. durationSec 使用项目默认镜头秒数：{{defaultShotDuration}}；剧情需要短镜头时不得低于 3 秒。',
        '5. characters.promptText 必须可直接用于角色图生成，写成单一角色、纯白极简背景、四方向全身转面表，方向顺序固定为正面、左侧面、右侧面、背面；每个方向完整露出头部、躯干、四肢/爪子/脚、尾巴或标志性部位。' || E'\n' ||
        '6. characters.promptText 禁止写头部特写、面部特写、半身像、三视图或正侧背旧版版式；禁止用大头特写替代全身视图；动物保持自然四足站立，不拟人化。' || E'\n' ||
        '7. durationSec 使用项目默认镜头秒数：{{defaultShotDuration}}；剧情需要短镜头时不得低于 3 秒。'),
    description = 'AI短剧分镜提取默认模板，增加角色四方向全身转面锚点',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取'
  AND content NOT LIKE '%四方向全身转面表%';

COMMIT;
