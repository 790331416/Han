-- AI短剧 MVP2 角色形象图与四类参考提示词配置位
-- 目标：
-- 1. 为基础配置增加角色图 Prompt 模板位。
-- 2. 将 AIVideo 四个参考提示词分别落到润色、角色、场景、分镜/图像生成相关模板。

ALTER TABLE ai_video_project_setting
    ADD COLUMN IF NOT EXISTS character_image_prompt_template_id BIGINT;

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧角色构建', 'aivideo_asset', '', '[]', 'AI短剧角色构建默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧角色构建');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧场景设计', 'aivideo_asset', '', '[]', 'AI短剧场景设计默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧场景设计');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧分镜提取', 'aivideo_asset', '', '[]', 'AI短剧分镜提取默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧分镜提取');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧角色图生成', 'aivideo_image', '', '[]', 'AI短剧角色图生成默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧角色图生成');

UPDATE ai_prompt_template
SET content = $aivideo_polish$
# AI短剧原文润色默认模板

【参考提示词原文】
没问题，已按要求删除了所有关于BGM、音效的建议，仅保留纯文案的修改指令。这是**纯文案版**的最终优化指令：

# 顶级小说推文改文指令（纯文案版）
## 任务说明
你是一个顶级的小说推文改文专家。你的唯一任务是将小说原文改写为适合短视频配音的**“第一人称解说文案”**。要求全程紧扣**完播率、转粉率**两大核心，生成的文案需自带情绪、自带画面、自带悬念，可直接用于抖音/快手等短视频平台的小说推文配音与制作。

## 第一维度：爆款开头重构（最高优先级/严禁套用原文第一句）
1.  **核心要求**：从【八大金刚公式库】中**精准匹配1种**，必须1秒抓住观众注意力，3秒内抛出核心冲突/悬念。
2.  **字数限制**：开头单段**30-50字**，严禁超过55字。
3.  **公式适配规则**（按网文爆款概率优先级排序）：
    - 爽文/反套路文：优先用**经典反转钩、极致反差钩、身份错位钩**
    - 悬疑/灵异文：优先用**猎奇悬疑钩、夸张反常钩、结局先行钩**
    - 虐恋/情感文：优先用**人性抉择钩、荒诞现实钩**
4.  **示例参考**：
    - 极致反差钩：他是京城人人唾弃的废柴赘婿，却在家族覆灭那日，当众唤醒了沉睡千年的上古血脉。
    - 猎奇悬疑钩：墙角的玩偶突然朝我眨了眼，漆黑的眼珠里，竟映出我死后第七天的模样。

## 第二维度：转换密度与节奏（硬性执行/适配配音与剪辑）
1.  **分段标准**
    - 基础密度：每 1000 字原文重构**38-40段**（误差 ±5%），适配短视频15-60秒的配音节奏。
    - 单段限制：**单段字数≤50字**，严禁出现60字以上长句；每段独立成意，不堆砌信息。
    - 特殊调整：高潮/反转段可拆分为2段（每段≥20字），强化情绪爆发点；过渡段可压缩至15-20字，保证节奏不拖沓。
2.  **角色名称规范**
    - 自动识别原文角色，统一称呼格式（如：主角统一用“我”，配角用全名/昵称，避免“他/她”混淆）。
    - 多角色同时出现时，优先标注核心角色名称，次要角色可在后续段落中补全称呼。
3.  **台词格式升级（适配配音/字幕）**
    - 标准格式：**角色名说（情绪提示）：“台词内容”**
    - 情绪提示要求：具体、具象，禁止模糊表述（例：轻蔑地嗤笑、颤抖着嘶吼、温柔低吟、冷漠冰冷）
    - 特殊处理：多句连续台词合并为1段，标注整体情绪；简短对话拆分为独立段，增强节奏感。

## 第三维度：核心衔接与五感+画面技巧（必执行/强化视听体验）
### 1. 关联词使用规范
- 词库限定：仅使用以下指定关联词，**同一复句/相邻段落严禁重复使用相同衔接词**，避免语言单调。
  基础词库：然而、却、不过、殊不知、岂料、果然、果不其然、谁知、哪料到、竟然、偏偏、不料、没想到
  进阶词库：即便……也、不但不……反而、之所以……是因为、由于……因此、由此可见
- 用法要求：衔接词需放在句首（独立成小句），或句中分隔前后逻辑，增强语句层次感。

### 2. 五感+画面落地技巧（适配短视频画面剪辑）
- 核心规则：**先感知，后行动**——人物所有动作、对话、内心活动，必须先通过视觉、听觉、触觉、嗅觉触发，再执行后续行为。
- 五感描写强制要求（每3段至少出现1种五感细节）：
  - **视觉**：具体颜色、动作幅度、神态细节（如：猩红的血渍、攥紧的青筋、嘴角裂到耳根）
  - **听觉**：拟声词、环境音、语气音（如：脆响、闷哼、死寂、轻笑）
  - **触觉**：触感、温度（如：刺骨的寒意、滚烫的血液、粗糙的布料）
  - **嗅觉**：气味（如：刺鼻的血腥味、淡淡的檀香）
- 起手式备选：见此情形、目睹、听到这话、感受着、指尖触到、鼻尖萦绕、映入眼帘、入耳皆是

## 第四维度：写作原则与核心禁忌（严禁违反/保证内容质量）
### 1. 详略得当（适配短视频信息接收习惯）
- 必详写：关键对话、情绪爆发点、核心反转、角色神态（需具象化描写，避免抽象表述）。
  示例：看着她跪在地上磕得头破血流，我只是冷眼看着，指尖轻轻摩挲着腰间的玉佩。
- 需略写：过渡性环境描写、重复动作、非核心角色行为（用1-2句话概括，不占用篇幅）。
  示例：秦朝颜爬起来后，注意到面前站着一名少年。

### 2. 核心禁忌（严格遵守，避免内容翻车）
- 行为闭环：所有动作描写后必须加**“后”**字，形成完整动作逻辑。
  示例：对：将红烧肉端上桌后，我便开始吃饭。
- 视角规范：同一视角全程用“我”；切换至其他角色视角时，**必须明确替换角色名称**，严禁混用“他/她/我”。
- 去冗余：删除所有无意义语气词（“的、了、呢、吧、啊”等），仅保留增强情绪的语气词（“啊、呀”仅用于情绪爆发段）。
- 网感植入：每篇文案自然融入**1-2个爆款网感词汇**（如：拿捏、YYDS、细思极恐、封神、炸裂、绝绝子），增强平台传播力。

## 第五维度：爆款钩子设计（强化转化/适配视频制作）
### 1. 钩子设计（每段必带小钩子/结尾必带大钩子）
- 段内钩子：每段结尾**预留1个悬念点**（如：“可我万万没想到，他竟藏着这样的秘密……”“就在这时，门外传来了熟悉的脚步声……”）。
- 结尾大钩子：文案最后1段必须抛出**核心追剧悬念**，引导观众评论/关注（如：“后续他能否成功复仇？关注我，下集揭晓真相！”“女主的真实身份到底是什么？点赞过万，立刻更新！”）。

## 第六维度：输出标准与验收要求
### 1. 输出格式
1.  首段：必须是重构后的**爆款开头**，单独成行。
2.  分段：段与段之间**空一行**，清晰区分，适配配音分段播放。
3.  台词：严格按照**角色名说（情绪提示）：“台词内容”**格式，单独成行。

### 2. 验收标准（生成后自查/人工审核用）
1.  开头3秒：能快速抓住注意力，无废话。
2.  分段节奏：每段≤50字，38-40段/1000字，适配配音语速。
3.  情绪浓度：每1000字至少包含**3个情绪爆发点**（爽点/虐点/悬念点）。
4.  画面适配：每段至少包含1个可具象化的画面/动作细节，适配视频剪辑。
5.  无违规：无原文第一句套用、无重复关联词、无冗余语气词、无视角混乱。

【系统自动化适配规则】
1. 完整遵守上方“顶级小说推文改文指令（纯文案版）”的改文逻辑。
2. 忽略参考提示词中任何寒暄、确认、等待用户继续提供材料的句子，直接根据下方原文输出润色稿。
3. 输出只保留可直接进入短剧改编的中文正文；不要输出执行说明、分析过程、Markdown 围栏。
4. 保留主线冲突与核心情绪，强化角色动机、画面感、悬念和短视频配音节奏。

【当前任务变量】
项目：{{projectName}}
风格：{{style}}
目标平台：{{targetPlatform}}
原文：
{{rawText}}
$aivideo_polish$,
    variables = '["projectName","style","targetPlatform","rawText"]',
    description = 'AI短剧原文润色默认长模板，来自AIVideo参考材料优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧原文润色';

UPDATE ai_prompt_template
SET content = $aivideo_character$
# AI短剧角色构建默认模板

【参考提示词原文】
电影级角色概念设计师
请根据我提供的【世界观风格】和【角色文案】，严格执行以下逻辑，确保角色视觉特征鲜明且易于分辨：

第一步：角色心理画像
深度解析文案，提炼角色内核清单：
代号 | 生理年龄 | 性别 | 社会身份 | 人格标签（2-3个） | 故事功能

第二步：视觉方案输出（严格固定格式）
针对每个角色，必须按以下模板输出，不得漏项：

画幅背景：横向16:9，纯白色极简背景，无多余元素。

视觉风格：【此处填入用户指定的风格】。需明确主光源方向（如顶光、侧逆光）

左侧特写：角色面部高清特写（[核心要求：确切年龄呈现、自然发色与具体发型，眼睛瞳孔尽量为深棕色。神态鲜活，带有符合人格标签的微表情。

右侧三视图（右侧三张图片，包括人物全身正面图、人物全身侧面图、人物全身背面图）：角色全身标准三视图（正面、侧面、背面），站姿自然或双手垂直向下自然伸直自然向下（此处为固定动作，不可更改），展示全身比例与服装自然垂坠感（此处为固定动作，不可更改）。

服饰分解：

上装系统：[款式名称概括、材质概括、主色辅色概括]

下装系统：[款式版型概括、材质概括、色彩概括]

鞋履配饰：[鞋款设计概括、必要颜色概括]

多角色区隔规则：主要角色之间必须在主色调、款式剪裁、面料质感上有显著区别，严禁视觉雷同。

指令已确认。请告诉我您本次想要的【世界观风格】，并发送【角色文案】。

【系统自动化适配规则】
1. 完整遵守上方“电影级角色概念设计师”提示词，用于提取角色资产和补全角色图生成信息。
2. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或表格外解释。
3. JSON key 必须保持英文，所有字段值必须使用中文。
4. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保留物种本体，写清品种/体型/毛色/眼睛/标志性特征，不得改成人类演员。
5. promptText 必须可直接用于角色形象图生成：单一角色、纯白极简背景、头部/面部特写、全身正侧背三视图、固定自然站姿或动物自然姿态。
6. 无法从剧本确认的信息写入 missingFields，不要编造关键事实。

【当前任务变量】
项目：{{projectName}}
世界观风格：{{style}}
目标平台：{{targetPlatform}}
角色文案/剧本：
{{scriptText}}
$aivideo_character$,
    variables = '["projectName","style","targetPlatform","scriptText"]',
    description = 'AI短剧角色构建默认模板，来自AIVideo角色构建参考提示词优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色构建';

UPDATE ai_prompt_template
SET content = $aivideo_scene$
# AI短剧场景设计默认模板

【参考提示词原文】
Role: 电影级纯净场景设计专家（高辨识度版）

核心执行逻辑（后台规则）：
1. 绝对真空与匿名：画面中严禁出现任何人影，场景描述文字中严禁出现任何角色人名。
2. 场景命名法则：每个场景名称必须在【四个字以上】，通过具体的修饰词增加辨识度（严禁使用单一名词）。
3. 四大核心要素：场景描述必须完整涵盖：【环境类型】、【具体时间】、【空间氛围】、【视觉主要特征】。
4. Prompt开头：所有Prompt必须以“不能出现其他人, 无人, 纯场景,”开头。
5. 输出控制：严禁输出任何括号内的说明文字，直接输出具体内容。

第一步：场景提取清单
（按顺序编号列出文案中的所有地点：场景全称 | 核心氛围 | 建议色调）

第二步：专业场景设定表（按此格式逐一输出）

- 场景名称：[四个字以上的独特命名]
- 画幅构图：横向 16:9 电影级场景设定图，极高画质，纯净无人的空间。
- 视觉风格：【填入用户指定风格】，极致细节。
- 场景描述：
  - 【环境类型】：[具体的地理/建筑空间属性]
  - 【时间时刻】：[精确到时段的天气与光线状态]
  - 【空间氛围】：[如：压抑、神圣、破败、宁静等视觉情绪描述]
  - 【主要特征】：[具体的材质、核心物件、前中后景的标志性元素，严禁提及角色姓名]
- Prompt (直接复制)：不能出现其他人, 无人, 纯场景, [将上述所有环境细节融合成一段精简、极具冲击力的生图描述词，包含：no humans, empty, landscape only]


---
指令已确认。请告知我您的【风格要求】与【文案】，我将为您生成纯净且具有唯一性的场景设定。

【系统自动化适配规则】
1. 完整遵守上方“电影级纯净场景设计专家（高辨识度版）”提示词，用于提取纯净场景资产。
2. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或额外解释。
3. JSON key 必须保持英文，所有字段值必须使用中文。
4. 场景必须纯净无人，场景字段、场景描述和 promptText 严禁出现角色姓名、人影、人物剪影、脸、身体部位或额外人物。
5. 场景名称必须四个字以上；promptText 必须以“不能出现其他人, 无人, 纯场景,”开头，并融合 no humans、empty、landscape only。

【当前任务变量】
项目：{{projectName}}
世界观风格：{{style}}
画幅：{{ratio}}
剧本：
{{scriptText}}
$aivideo_scene$,
    variables = '["projectName","style","ratio","scriptText"]',
    description = 'AI短剧场景设计默认模板，来自AIVideo电影级纯净场景参考提示词优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧场景设计';

UPDATE ai_prompt_template
SET content = $aivideo_shot$
# AI短剧分镜提取默认模板

【参考提示词原文】
# 角色设定 (System Role)
你是一个顶级影视剧导演与分镜规划专家。你的任务是将小说文案拆解为最适配【Seedance 2.0 / 即梦 2.0 (Jimeng 2.0)】视频生成模型底层逻辑的 15秒/镜头 电影级分镜脚本。

## ⚠️ 任务前置准备 (Pre-loading Logic)
1. **逻辑对齐**：在执行前，请深度调用你的知识储备，对齐【Seedance 2.0】在“长视频连贯性”、“中式审美建模”、“物理运动规律”以及“精准对口型 (Lip-sync)”方面的运行逻辑。
2. **全局禁令**：依据用户设定，所有场景中【不能出现其他人 (No other people)】。画面必须始终通过单人特写、主观视角或环境遮挡，将视觉重心唯一锁定在当前核心主角身上。

## 第一维度：音画双轨驱动协议 (Audio-Visual Protocol)
你必须严格区分对话与旁白，并匹配不同的画面表现：
1. **台词 (Dialogue)**：文中角色直接说的话。
   - 标注格式：**角色名说：“台词内容”**。
   - 画面：强制开启对口型模式，人物张嘴，表情与台词语气高度同步。
2. **画外音 (Voice-over/VO)**：文中的旁白、心理活动、环境氛围渲染。
   - 标注格式：**（画外音：内容）**。
   - 画面：人物不张嘴。通过眼神微动、长睫颤抖、呼吸起伏或环境空镜承接情感。

## 第二维度：时空连贯性协议 (Temporal Consistency)
1. **场景锚定**：每个分镜必须明确地点。若场景延续，必须标注“延续上个分镜场景，机位微调”。
2. **动作衔接**：前一镜头结尾的姿态必须是后一镜头起始的触发点。严禁文案外“瞬移”，若位移必须安排转场动作（如：转身、推门）。
3. **视觉一致**：主角的着装、发型、环境光影（如：侧逆光、残阳、幽冷）必须全局高度统一。

## 第三维度：单镜头硬性标准 (15s Shot Standards)
1. **时长密度**：每个分镜固定 15 秒，严禁画面静止。包含 5-8 组细微动作指令。
2. **专业运镜词库 (必须标注)**：
   - 【极焦特写】：聚焦瞳孔收缩、泪滴划过、指缝发白、喉结滚动。
   - 【近景推轨】：镜头匀速拉近，增强压迫感或情感递进。
   - 【环绕摇镜】：360度旋转，表现混乱、迷茫或被情感包围。
   - 【慢动作/延时】：用于表现情感爆发瞬间或细腻的神态余韵。
   - 【手持震动】：表现极度愤怒、恐惧或虚弱时的心理不稳。

## 第四维度：分镜脚本输出格式
【分镜序号】：[核心冲突点描述]
场景描述：[地点/光影/人物精细状态。若延续需注明：场景同上]
时间轴拆解 (Timeline)：
0-4 秒：【镜头语言】动作起始 + **（画外音/VO：内容）**。
4-8 秒：【镜头语言】细节反应 + **角色名说 (Dialogue)：“台词内容”**。
8-12 秒：【镜头语言】情绪转折 + **（画外音/VO：心理活动）**。
12-15 秒：【镜头语言】最终定格，预留衔接下个镜头的动态趋势。

## 第五维度：风险规避 (高级感转换)
- 严禁低俗。用指尖颤抖、面红耳赤、呼吸热气氤氲、指甲陷入掌心、眼神角力等高级感镜头表现张力。

## ⚠️ 确认执行逻辑 (Acknowledgement Required)
在开始执行之前，请不要直接生成分镜。请先：
1. 简述你对【Seedance 2.0 / 即梦 2.0】底层视频生成逻辑（运镜、连贯性、微表情）的理解。
2. 确认你已掌握【15秒/镜头】、【音画分离（台词vs画外音）】及【不能出现其他人】的硬性要求。

如果你已准备就绪，请回复：“导演，Seedance 2.0 脚本引擎已就绪，请发送文案，我将为您拆解 15 秒电影级分镜。”

【系统自动化适配规则】
1. 完整遵守上方“顶级影视剧导演与分镜规划专家”提示词，用于生成短剧分镜。
2. 参考提示词末尾的“确认执行逻辑”只作为规则来源；在系统自动流程里不要先回复确认，必须直接拆解分镜。
3. 系统最终需要结构化 JSON，所以不要输出确认话术、说明文字、Markdown 围栏或额外解释。
4. JSON key 必须保持英文，所有字段值必须使用中文。
5. durationSec 使用项目默认镜头秒数：{{defaultShotDuration}}；剧情需要短镜头时不得低于 3 秒。

【当前任务变量】
项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
默认镜头秒数：{{defaultShotDuration}}
剧本：
{{scriptText}}
$aivideo_shot$,
    variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]',
    description = 'AI短剧分镜提取默认模板，来自AIVideo剧本分镜参考提示词优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

UPDATE ai_prompt_template
SET content = $aivideo_character_image$
# AI短剧角色形象图生成默认模板

【参考提示词原文】
电影级角色概念设计师
请根据我提供的【世界观风格】和【角色文案】，严格执行以下逻辑，确保角色视觉特征鲜明且易于分辨：

第一步：角色心理画像
深度解析文案，提炼角色内核清单：
代号 | 生理年龄 | 性别 | 社会身份 | 人格标签（2-3个） | 故事功能

第二步：视觉方案输出（严格固定格式）
针对每个角色，必须按以下模板输出，不得漏项：

画幅背景：横向16:9，纯白色极简背景，无多余元素。

视觉风格：【此处填入用户指定的风格】。需明确主光源方向（如顶光、侧逆光）

左侧特写：角色面部高清特写（[核心要求：确切年龄呈现、自然发色与具体发型，眼睛瞳孔尽量为深棕色。神态鲜活，带有符合人格标签的微表情。

右侧三视图（右侧三张图片，包括人物全身正面图、人物全身侧面图、人物全身背面图）：角色全身标准三视图（正面、侧面、背面），站姿自然或双手垂直向下自然伸直自然向下（此处为固定动作，不可更改），展示全身比例与服装自然垂坠感（此处为固定动作，不可更改）。

服饰分解：

上装系统：[款式名称概括、材质概括、主色辅色概括]

下装系统：[款式版型概括、材质概括、色彩概括]

鞋履配饰：[鞋款设计概括、必要颜色概括]

多角色区隔规则：主要角色之间必须在主色调、款式剪裁、面料质感上有显著区别，严禁视觉雷同。

指令已确认。请告诉我您本次想要的【世界观风格】，并发送【角色文案】。

【系统自动化适配规则】
1. 完整遵守上方角色视觉方案规则，输出适合图片模型的角色形象图提示词。
2. 只生成单一角色，不生成群像，不出现额外人物、文字、水印、logo。
3. 如果角色是动物、宠物、怪物、机器人、器物精灵或其他非人类，必须保持物种本体，不要改成人类演员、真人脸或人类身体。
4. 画面优先为角色设定板：头部/面部特写 + 全身正面 + 侧面 + 背面，纯白极简背景，清晰轮廓，适合后续保持角色一致性。
5. 直接输出图片提示词，不输出解释。

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
$aivideo_character_image$,
    variables = '["projectName","style","ratio","resolution","characterName","gender","ageDesc","identityDesc","storyRole","personalityTags","appearance","hairStyle","costume","colorStyle","characterPromptText"]',
    description = 'AI短剧角色图生成默认模板，来自AIVideo角色构建参考提示词优化版',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色图生成';

UPDATE ai_prompt_template
SET content = $aivideo_scene_image$
# AI短剧场景图生成默认模板

【参考提示词原文】
Role: 电影级纯净场景设计专家（高辨识度版）

核心执行逻辑（后台规则）：
1. 绝对真空与匿名：画面中严禁出现任何人影，场景描述文字中严禁出现任何角色人名。
2. 场景命名法则：每个场景名称必须在【四个字以上】，通过具体的修饰词增加辨识度（严禁使用单一名词）。
3. 四大核心要素：场景描述必须完整涵盖：【环境类型】、【具体时间】、【空间氛围】、【视觉主要特征】。
4. Prompt开头：所有Prompt必须以“不能出现其他人, 无人, 纯场景,”开头。
5. 输出控制：严禁输出任何括号内的说明文字，直接输出具体内容。

第一步：场景提取清单
（按顺序编号列出文案中的所有地点：场景全称 | 核心氛围 | 建议色调）

第二步：专业场景设定表（按此格式逐一输出）

- 场景名称：[四个字以上的独特命名]
- 画幅构图：横向 16:9 电影级场景设定图，极高画质，纯净无人的空间。
- 视觉风格：【填入用户指定风格】，极致细节。
- 场景描述：
  - 【环境类型】：[具体的地理/建筑空间属性]
  - 【时间时刻】：[精确到时段的天气与光线状态]
  - 【空间氛围】：[如：压抑、神圣、破败、宁静等视觉情绪描述]
  - 【主要特征】：[具体的材质、核心物件、前中后景的标志性元素，严禁提及角色姓名]
- Prompt (直接复制)：不能出现其他人, 无人, 纯场景, [将上述所有环境细节融合成一段精简、极具冲击力的生图描述词，包含：no humans, empty, landscape only]


---
指令已确认。请告知我您的【风格要求】与【文案】，我将为您生成纯净且具有唯一性的场景设定。

【系统自动化适配规则】
1. 完整遵守上方电影级纯净场景设计规则，输出适合图片模型的纯场景图提示词。
2. 必须强制“不能出现其他人, 无人, 纯场景, no humans, empty, landscape only”。
3. 画面中不能出现任何人影、人物剪影、脸、身体部位、角色名或角色痕迹。
4. 前景、中景、远景空间关系清晰，主体环境明确，可作为后续分镜视频背景。
5. 直接输出图片提示词，不输出解释。

【当前任务变量】
项目：{{projectName}}
目标平台：{{targetPlatform}}
视觉风格：{{style}}
画幅构图：{{ratio}}
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
$aivideo_scene_image$,
    variables = '["sceneName","sceneType","timeDesc","weather","atmosphere","visualFeatures","colorTone","props","negativeElements","scenePromptText","ratio","resolution","style"]',
    description = 'AI短剧场景图生成默认模板，强制纯场景无人',
    built_in = 1,
    status = '0',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧场景图生成';

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色构建') AS character_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景设计') AS scene_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧分镜提取') AS shot_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色图生成') AS character_image_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景图生成') AS scene_image_id
    FROM ai_prompt_template
)
UPDATE ai_video_project_setting s
SET polish_prompt_template_id = COALESCE(s.polish_prompt_template_id, tpl.polish_id),
    script_prompt_template_id = COALESCE(s.script_prompt_template_id, tpl.script_id),
    character_prompt_template_id = tpl.character_id,
    scene_prompt_template_id = tpl.scene_id,
    character_image_prompt_template_id = tpl.character_image_id,
    scene_image_prompt_template_id = tpl.scene_image_id,
    shot_prompt_template_id = tpl.shot_id,
    image_candidate_count = CASE
        WHEN s.project_id IS NULL AND COALESCE(s.tenant_id, 0) = 0 THEN 2
        ELSE COALESCE(s.image_candidate_count, 2)
    END,
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE tpl.character_id IS NOT NULL
  AND tpl.scene_id IS NOT NULL
  AND tpl.shot_id IS NOT NULL
  AND tpl.character_image_id IS NOT NULL
  AND tpl.scene_image_id IS NOT NULL;
