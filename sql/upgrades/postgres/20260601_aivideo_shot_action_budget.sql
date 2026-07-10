-- AI short video shot action budget and execution prompt upgrade.
-- This script updates prompt templates used by existing databases.

BEGIN;

UPDATE ai_prompt_template
SET content = $aivideo_script$
# 短剧剧本与镜头拆分规划指令

你是顶级影视剧导演与分镜规划专家。请把润色文案改写为适合 AI 视频生成的短剧剧本，并为后续分镜拆解保留清晰结构。

## 输出要求
1. 按场次组织，每场包含人物、地点、光影、动作、对白、旁白和情绪提示。
2. 对白与旁白必须分开：角色直接说的话标注为“角色名说：“台词内容””；旁白、心理活动和氛围渲染标注为“（画外音：内容）”。
3. 场景延续时必须注明“延续上个分镜场景，机位微调”。
4. 动作要有衔接，前一镜头结尾姿态必须能触发后一镜头起始动作。
5. 每个场次末尾必须增加【镜头拆分建议】，写清：建议镜头数、每个镜头主动作、是否包含强动作、建议时长 5/6/8 秒、结尾状态。
6. 动作预算：5 秒=1 个主动作 + 1 个反应/表情 + 1 个结尾状态；6 秒=2 个连续动作 + 结尾状态；8 秒=3 个连续动作 + 明确结尾状态。
7. 超过 3 个动作 beat 必须建议拆镜，不要硬塞进一个镜头。
8. 倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等强动作额外占预算，优先单独作为一个镜头核心。
9. 画面不得凭空出现文案外人物、地点或道具。
10. 输出短剧剧本正文，不输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}

润色文本：
{{polishedText}}
$aivideo_script$,
    variables = '["projectName","targetPlatform","ratio","polishedText"]',
    description = 'AI短剧剧本生成默认模板，增加镜头拆分建议和动作预算',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧剧本生成';

UPDATE ai_prompt_template
SET category = 'aivideo_asset',
    content = $aivideo_shot$
# AI短剧分镜提取默认模板

请从短剧剧本中提取【角色、场景、分镜】，必须只输出 JSON 对象，不要输出解释、Markdown 围栏或额外说明。
JSON key 必须保持英文，所有字段值必须使用中文。

## 角色构建规则
1. 先解析角色画像：代号、年龄/生命阶段、性别或物种、身份、人格标签、故事功能。
2. 人类角色写清年龄、发色、发型、眼神、服装材质、主色辅色、鞋履配饰。
3. 动物、宠物、怪物、机器人、器物精灵等非人类角色必须保留物种本体，写清品种/体型/毛色/眼睛/标志性特征，禁止改成人类演员。
4. 多角色必须在色彩、轮廓、材质或身体特征上显著区别，严禁视觉雷同。
5. promptText 要可直接用于角色图生成，包含单一角色、纯白极简背景、头部/面部特写、全身正侧背三视图、固定自然站姿或动物自然姿态等关键信息。

## 电影级纯净场景规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。
3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。

## 分镜动作预算
1. durationSec 只能输出 5、6、8，不再固定使用项目默认秒数；项目默认镜头秒数仅作为初始参考：{{defaultShotDuration}}。
2. 5 秒镜头：只允许 1 个主动作 + 1 个反应/表情 + 1 个结尾状态。
3. 6 秒镜头：允许 2 个连续动作 + 1 个结尾状态。
4. 8 秒镜头：允许 3 个连续动作 + 1 个明确结尾状态。
5. 超过 3 个动作 beat 必须自动拆成多个 shots，不允许硬塞。
6. 强动作要额外占预算：倒地起身、悬浮、变身、俯冲、落水、打斗、救援、掰弯铁栏等，优先单独作为一个镜头核心。
7. actionDesc 必须写成视频模型能执行的动作节拍，包含起始状态、主动作、反应/表情和结尾状态。
8. promptText 必须补充构图、目标部位可见和部位发光限制。
9. 出现爪子、手、脚、翅膀、尾巴等部位时，必须要求半身/全身构图并露出目标部位；出现发光时必须写清具体发光部位，禁止用眼睛发光替代目标部位发光。
10. dialogue 只放角色直接说的话；voiceOver 只放旁白、心理活动和环境氛围，不能把旁白改成对白。

## 输出 JSON 结构
{
  "characters": [{"characterName":"","gender":"","ageDesc":"","identityDesc":"","personalityTags":[""],"storyRole":"","relationshipDesc":"","appearance":"","hairStyle":"","costume":"","colorStyle":"","negativeTraits":"","promptText":"","completeness":"","missingFields":[""]}],
  "scenes": [{"sceneName":"","sceneType":"","episodeNo":1,"timeDesc":"","weather":"","atmosphere":"","visualFeatures":"","colorTone":"","props":"","negativeElements":"","promptText":"","completeness":"","missingFields":[""]}],
  "shots": [{"episodeNo":1,"shotNo":1,"durationSec":5,"sceneName":"","characterNames":[""],"shotType":"","cameraPosition":"","cameraMovement":"","actionDesc":"","dialogue":"","voiceOver":"","emotion":"","promptText":""}]
}

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
剧本：
{{scriptText}}
$aivideo_shot$,
    variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]',
    description = 'AI短剧分镜提取默认模板，增加动作预算、动态时长和部位锁定',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

UPDATE ai_prompt_template
SET category = 'aivideo_video',
    content = $aivideo_shot_video$
# 单分镜视频模型执行版 Prompt

基于参考图生成 1 个连续短剧镜头，不要生成多镜头拼接。
参考图类型：{{referenceFrameType}}。参考图地址：{{referenceImageUrl}}。
输出规格：{{ratio}}，{{resolution}}，约 {{durationSec}} 秒。

## 第一帧和连续性
- 第一帧必须贴合参考图：主体位置、姿态、朝向、体型、毛色/服饰、光影、天气和背景空间保持一致。
- 上一镜头：{{previousShotNo}}，{{previousShotSummary}}。
- 上一镜头结束状态：{{previousEndState}}
- 本镜头起始状态：{{currentStartState}}
- 本镜头结尾状态：{{currentEndState}}

## 主体、场景、构图
- 项目/风格：{{projectName}} / {{style}}
- 场景：{{sceneName}}，{{sceneTime}}，{{weather}}，{{atmosphere}}，视觉特征：{{visualFeatures}}
- 出场主体：{{characterNames}}
- 角色一致性：{{characterContinuity}}
- 场景一致性：{{sceneContinuity}}
- 构图要求：{{compositionRequirement}}
- 部位可见要求：{{bodyPartRequirement}}
- 发光部位要求：{{glowRequirement}}

## 动作节拍
{{actionBeats}}

## 执行顺序
{{timingPlan}}

## 镜头语言
- 景别/机位/运镜：{{shotType}} / {{cameraPosition}} / {{cameraMovement}}
- 同一镜头内只保留 1 种主要运镜，运动稳定、低幅度、可剪辑。
- 动作边界：{{motionBoundary}}

## 音画规则
{{audioVisualProtocol}}
视频生成阶段只负责画面，不生成、不替换、不改变配音、旁白声线、BGM 或音效；对白为空时主体不张嘴。

## 负面约束
{{continuityNegativePrompt}}
禁止字幕、水印、logo、花字、无关文字；禁止换角色、换物种、换毛色、换体型、换背景；禁止用眼睛发光替代指定部位发光。
$aivideo_shot_video$,
    variables = '["projectName","targetPlatform","style","ratio","resolution","durationSec","episodeNo","shotNo","sceneName","sceneType","sceneTime","weather","atmosphere","visualFeatures","characterNames","characterContinuity","sceneContinuity","audioVisualProtocol","shotType","cameraPosition","cameraMovement","actionDesc","dialogue","voiceOver","emotion","shotPromptText","referenceImageUrl","referenceFrameType","previousShotNo","previousShotSummary","previousEndState","previousTailFrameUrl","currentStartState","currentEndState","motionBoundary","continuityNegativePrompt","actionBeats","timingPlan","compositionRequirement","bodyPartRequirement","glowRequirement","candidateCount"]',
    description = 'AI短剧分镜视频生成执行模板，增加动作节拍、构图部位锁定和禁用自动配音',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成';

UPDATE ai_video_project_setting
SET default_shot_duration = CASE
        WHEN default_shot_duration IS NULL OR default_shot_duration <= 5 THEN 5
        WHEN default_shot_duration <= 6 THEN 6
        ELSE 8
    END,
    update_time = CURRENT_TIMESTAMP
WHERE default_shot_duration IS NULL OR default_shot_duration NOT IN (5, 6, 8);

UPDATE ai_video_project
SET default_shot_duration = CASE
        WHEN default_shot_duration IS NULL OR default_shot_duration <= 5 THEN 5
        WHEN default_shot_duration <= 6 THEN 6
        ELSE 8
    END,
    update_time = CURRENT_TIMESTAMP
WHERE default_shot_duration IS NULL OR default_shot_duration NOT IN (5, 6, 8);

UPDATE ai_video_shot
SET duration_sec = CASE
        WHEN duration_sec IS NULL OR duration_sec <= 5 THEN 5
        WHEN duration_sec <= 6 THEN 6
        ELSE 8
    END,
    update_time = CURRENT_TIMESTAMP
WHERE duration_sec IS NULL OR duration_sec NOT IN (5, 6, 8);

COMMIT;
