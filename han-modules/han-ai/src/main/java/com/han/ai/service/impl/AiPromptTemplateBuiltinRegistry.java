package com.han.ai.service.impl;

import java.util.List;

/**
 * Built-in prompt templates that must be available even when upgrade SQL was missed.
 */
final class AiPromptTemplateBuiltinRegistry {

    private AiPromptTemplateBuiltinRegistry() {
    }

    static List<Seed> all() {
        return List.of(
                new Seed("AI短剧原文润色", "aivideo_text", """
                        # AI短剧原文润色默认模板
                        你是短剧原文润色编剧。请在不改变核心剧情的前提下，把用户原文润色为适合后续剧本、资产和分镜生成的素材。
                        硬性规则：
                        1. 保留人物、地点、关键道具、情绪目标和因果关系，不新增无关角色。
                        2. 明确视觉风格、目标平台、画幅、时代/天气/时间信息。
                        3. 区分说出口台词、旁白、心理活动；心理活动不得写成角色说出口。
                        4. 如果出现关键道具，写清颜色、形态、归属、最后谁拿着。
                        5. 输出 Markdown，不要解释流程。
                        
                        项目：{{projectName}}
                        视觉风格：{{style}}
                        原文：
                        {{rawText}}
                        """, "[\"projectName\",\"style\",\"rawText\"]", "AI短剧原文润色默认模板"),
                new Seed("AI短剧剧本生成", "aivideo_script", """
                        # AI短剧剧本生成默认模板
                        你是短剧总编剧。请把润色稿生成短剧剧本，并为后续资产、分镜、声音设计提供稳定锚点。
                        必须输出：
                        1. 剧情大纲：按场次写明时间、地点、人物、关键冲突。
                        2. 角色表：每个角色写身份、外观、服装、声线、口头禅、情绪弧线。
                        3. 场景表：每个场景写时间、天气、光线、空间结构、稳定物件。
                        4. 道具表：关键道具写颜色、形态、归属、第一次出现、最后持有人、连续性规则。
                        5. 声音设计：voiceProfiles、narrationProfile、bgmPlan、sfxPlan。
                        6. 分镜建议：每段适合拆成几个镜头，每个镜头只有合理动作预算。
                        约束：
                        - 5秒镜头只允许 1 个主动作 + 1 个反应/表情 + 1 个结尾状态。
                        - 6秒镜头允许 2 个连续动作 + 结尾状态。
                        - 8秒镜头允许 3 个连续动作 + 明确结尾状态。
                        - 超过 3 个动作 beat 自动拆镜；强动作单独占预算。
                        - 递给/接过/展示给/交给/传给/拿给必须写清谁递给谁、什么道具、从画面哪边来、最后谁拿着。
                        
                        项目：{{projectName}}
                        平台：{{targetPlatform}}
                        画幅：{{ratio}}
                        润色稿：
                        {{polishedText}}
                        """, "[\"projectName\",\"targetPlatform\",\"ratio\",\"polishedText\"]", "AI短剧剧本生成默认模板"),
                new Seed("AI短剧资产提取", "aivideo_asset", """
                        # AI短剧资产提取默认模板
                        你是短剧资产导演。请从剧本中提取角色、场景、道具、声音资产。
                        输出 JSON，字段包含 characters、scenes、props、soundDesign。
                        角色要求：
                        - 写清角色类型、视觉风格、身高比例、发型、服装、鞋袜、标志物、声线描述。
                        - Q版/3D卡通角色必须明确 2.5-3.5 头身、全身、禁止拉长为正常比例。
                        场景要求：
                        - 写清时间、天气、光线、前中后景、固定物件、可参考的已确认场景。
                        道具要求：
                        - propName、color、shape、ownerCharacterName、firstShotNo、lastHolder、continuityRules 必填。
                        - 蓝色收纳盒、试卷、存折、价格标签等关键道具必须进入 props。
                        声音要求：
                        - voiceProfiles 写角色声线；bgmPlan 写整片音乐情绪；sfxPlan 写关键音效。
                        
                        项目：{{projectName}}
                        剧本：
                        {{scriptText}}
                        """, "[\"projectName\",\"scriptText\"]", "AI短剧角色场景分镜提取默认模板"),
                new Seed("AI短剧角色构建", "aivideo_asset", """
                        # AI短剧角色构建默认模板
                        你是视频角色资产设计师。请把角色档案转为可生成角色图和视频锚定的结构化描述。
                        硬性规则：
                        1. 明确视觉风格：写实电影感、3D国漫CG、Q版萌系全身、2D日漫、童话绘本等。
                        2. 明确角色类型：真人、动物、拟人、Q版、3D卡通、2D平面。
                        3. 输出必须包含固定外观锚点：脸型/头身比/发型/耳朵/尾巴/服装/鞋袜/主色。
                        4. Q版角色必须写“2.5-3.5头身、全身完整、白底、禁止正常比例拉长”。
                        5. 动物角色必须保持动物本体，不得变成人类演员。
                        6. 给出 voiceProfile，供后期语音合成或参考音频绑定。
                        """, "[]", "AI短剧角色构建默认模板"),
                new Seed("AI短剧场景设计", "aivideo_asset", """
                        # AI短剧场景设计默认模板
                        你是 Seedance 视频生成专用场景参考图设计专家。
                        硬性规则：
                        1. 场景图只生成单镜头纯场景，不出现人物、动物、脸、身体部位或角色名。
                        2. 明确前景、中景、远景、地面/天空/墙面空间关系。
                        3. 明确时间、天气、光源方向、主色调、核心道具和背景结构。
                        4. 如果是同一地点的天气/时间变化，必须引用已确认场景图作为参考，并要求保持空间布局。
                        5. Prompt 必须包含 no humans, empty scene, shot reference。
                        """, "[]", "AI短剧场景设计默认模板"),
                new Seed("AI短剧分镜提取", "aivideo_storyboard", """
                        # AI短剧分镜提取默认模板
                        你是短剧分镜导演。请把剧本拆成可生成 5/6/8 秒视频的镜头。
                        每个镜头必须输出：
                        - shotNo、durationSec、cameraMotion、transitionType、sceneName、visibleCharacters、offscreenCharacters。
                        - actionBeats：0-2s / 2-5s / 5-8s 的动作节拍。
                        - dialogueLines：只写说出口台词；thoughtLines：只写心理活动；narrationLines：只写旁白。
                        - propsInFrame：道具名、颜色、位置、谁拿着、交接方向。
                        衔接规则：
                        - 连续镜头必须说明上一镜尾帧如何进入本镜。
                        - 上一镜背对/侧身/左右站位/画内人数必须继承；若不继承，必须写切场、反打、转身、画外离场或单人反应。
                        - 插入镜头/交接镜头不强制继承上一尾帧，但必须说明镜头性质。
                        - 递给/接过/展示给/交给/传给/拿给必须写清谁递给谁、什么道具、从画面哪边来、最后谁拿着。
                        - 道具颜色、形状、标签内容不得跨镜漂移。
                        
                        项目：{{projectName}}
                        剧本：
                        {{scriptText}}
                        """, "[\"projectName\",\"scriptText\"]", "AI短剧分镜提取默认模板"),
                new Seed("AI短剧角色图生成", "aivideo_image", """
                        # AI短剧视频角色锚定图生成默认模板
                        你是火山视频生成参考角色图设计师。
                        通用硬性规则：
                        1. 单角色、全身完整、白底或浅灰纯背景，不生成群像，不生成复杂场景。
                        2. 角色占画面高度 60%-75%，露出头部、躯干、四肢/爪子/脚、尾巴或标志物。
                        3. 写实角色用 3/4 正面自然站姿；Q版/3D卡通写 2.5-3.5 头身。
                        4. 四视图需求时生成正面、左侧、右侧、背面，同服装同发型同标志物。
                        5. 禁止像贴纸、海报、拼贴、裁切身体、多人同图。
                        6. 输出直接给图片模型的 prompt，不解释。
                        """, "[]", "AI短剧角色图生成默认模板"),
                new Seed("AI短剧场景图生成", "aivideo_image", """
                        # AI短剧场景图生成默认模板
                        你是火山视频生成参考场景图设计师。
                        硬性规则：
                        1. 只生成一张单镜头场景参考图，不拼图、不分栏、不画设定板。
                        2. 画面中默认禁止出现人物、动物、脸、身体部位或角色名；除非明确要求主体入画。
                        3. 明确前景、中景、远景、地面/天空/墙面结构。
                        4. 同一地点变化天气/时间时，必须参考已确认场景图，保持街道/教室/商店布局一致。
                        5. Prompt 以“不能出现其他人，无人，纯场景”开头，并包含 no humans, empty scene, shot reference。
                        """, "[]", "AI短剧场景图生成默认模板"),
                new Seed("AI短剧分镜视频生成", "aivideo_video", """
                        # AI短剧单分镜视频生成默认模板
                        你是短剧分镜视频导演，请输出直接给视频模型执行的 prompt。
                        规则：
                        1. 基于已确认场景图、角色图、道具图和上一镜尾帧生成单个短剧镜头视频。
                        2. 如果使用上一镜尾帧作为首帧，不能混入角色图/场景图/参考音频；只继承尾帧并写清动作延续。
                        3. 如果是插入镜头/交接镜头/切场镜头，可使用角色图、场景图、道具图和 referenceAudioUrls。
                        4. 原生有声时 referenceAudioUrls 最多 3 段，每段 2-15 秒，总时长不超过 15 秒。
                        5. 台词必须区分对白、旁白、心理活动；心理活动不能生成角色开口说话。
                        6. 道具交接必须写清谁递给谁、从哪边入画、最后谁拿着；道具颜色不得变化。
                        7. 上一镜背对/侧身/左右站位/画内人数，除非切场、反打、转身、离场，否则必须继承。
                        8. 输出 0-2s / 2-5s / 5-8s 动作节拍，按镜头时长裁剪。
                        9. 不生成字幕、水印、logo、花字或剧情无关元素。
                        
                        镜头信息：
                        {{shotText}}
                        """, "[\"shotText\"]", "AI短剧分镜视频生成执行模板"),
                new Seed("AI短剧后期语音合成", "aivideo_tts", """
                        # AI短剧后期语音合成默认模板
                        你是短剧后期声音导演。请为整片剪辑后的时间线生成配音/旁白/音效/背景音乐计划。
                        输出要求：
                        1. 只处理已剪辑成片后的统一声音，不重新生成分镜视频。
                        2. dialogueLines 只包含说出口台词，必须给 speaker、text、startSec、endSec、voiceProfile。
                        3. thoughtLines 是心理活动，只能转旁白或内心独白，不让角色嘴型开口。
                        4. narrationLines 是旁白，和角色对白分开。
                        5. voiceProfiles 最多选择 3 个 referenceAudioUrls 作为声线参考；超过 3 个发声角色要提示拆分或改后期 TTS 固定音色。
                        6. bgmCue 写背景音乐起止时间、情绪、音量，不压对白。
                        7. sfxCues 写音效名称、起止时间、强度和画面动作绑定。
                        8. 最终声音必须贴合整片剪辑时间线，不能每个分镜各自乱生成声音。
                        """, "[]", "AI短剧后期语音合成默认模板")
        );
    }

    record Seed(String templateName, String category, String content, String variables, String description) {
    }
}
