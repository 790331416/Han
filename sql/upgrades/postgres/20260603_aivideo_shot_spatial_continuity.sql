-- AI short video shot spatial continuity prompt upgrade.
-- This script tightens storyboard extraction so adjacent shots cannot jump to an unintroduced location.

BEGIN;

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
5. promptText 要可直接用于 Seedance 视频角色锚定图生成，必须写成单一主体、纯白/浅灰极简背景、3/4 正面或轻微侧正面自然站姿、全身完整可见、主体占画面高度 60%-75%。
6. promptText 禁止写头部特写、面部特写、半身像、三视图、四方向、正侧背、多视图、分栏、拼图或同款分身；动物保持自然四足站立，不拟人化。

## Seedance 视频场景锚点规则
1. 场景必须纯净无人，场景描述和 promptText 严禁出现角色姓名、人影或额外人物。
2. 场景名称必须四个字以上，不能只写单一名词，要通过修饰词增加辨识度。
3. 场景必须覆盖环境类型、具体时间、空间氛围、视觉主要特征、建议色调和道具元素。
4. 场景 promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty scene、single shot reference。
5. 场景 promptText 必须写成单镜头视频首帧/环境锚点：前景、中景、远景和地面可行动区域清楚，禁止拼图、分栏、设定板、漫画格、文字标签。

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
11. 剧情空间连续性是硬约束：后一分镜必须承接前一分镜的主体位置、危险目标、空间关系和结尾状态，不能只因情绪需要突然换地点。
12. 如果上一分镜建立了屋顶、广告牌、铁架、高处、水中、火场、车道等危险目标，下一分镜必须继续该目标、让主角观察/靠近/救援该目标，或在 actionDesc 开头写明过渡动作。
13. 未经剧本铺垫，禁止突然切到狗窝、室内、家里、床下、窝口等新地点；必须先用过渡镜头建立空间关系，或改写为“延续上一镜，镜头回到街边/同一条街道”。
14. 错误示例：上一镜“广告牌铁架上有小身影”，下一镜“狗狗蜷缩在窝的角落”。正确示例：下一镜“延续上一镜，狗狗在街边抬头望向广告牌铁架，身体绷紧准备冲向商铺雨棚”。

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
    description = 'AI短剧分镜提取默认模板，增加动作预算、动态时长、部位锁定和剧情空间连续性硬约束',
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

COMMIT;
