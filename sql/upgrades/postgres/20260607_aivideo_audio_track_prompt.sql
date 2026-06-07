-- AI short video audio-track prompt upgrade.
-- Separates spoken dialogue, audible voice-over, and silent internal thoughts.

BEGIN;

UPDATE ai_prompt_template
SET content = replace(
        replace(
        replace(
        replace(
        replace(
        replace(
        replace(
        replace(
        replace(content,
            '按场次组织，包含人物、场景、动作、对白、旁白和情绪提示。',
            '按场次组织，包含人物、场景、动作、对白、旁白/画外音、心声/心理活动和情绪提示。'),
            '按场次组织，每场包含人物、地点、光影、动作、对白、旁白和情绪提示。',
            '按场次组织，每场包含人物、地点、光影、动作、对白、旁白/画外音、心声/心理活动和情绪提示。'),
            '对白与旁白必须分开：角色直接说的话标注为“角色名说：“台词内容””；旁白、心理活动和氛围渲染标注为“（画外音：内容）”。',
            '对白、旁白/画外音、心声/心理活动必须三轨分清：角色直接说出口的话标注为“角色名说：“台词内容””；可发声但角色不张嘴的旁白/画外音标注为“旁白：内容”或“角色名（画外音）：内容”；心声/心理活动默认不朗读，写成眼神、动作、环境空镜或画面隐喻。'),
            '严格区分 dialogue 和 voiceOver；角色直接说的话写入 dialogue，旁白、心理活动、环境氛围写入 voiceOver。',
            '严格区分 dialogue、voiceOver 和心理画面：dialogue 只写角色说出口并可口型同步的话；voiceOver 只写可发声但角色不张嘴的旁白/画外音；心理活动默认不写入 voiceOver，优先写入 actionDesc/promptText/emotion 用画面表现。低声报数、低声说、耳语、小声说、念出、读出都属于说出口的对白，必须写入 dialogue。'),
            'dialogue 只放角色直接说的话；voiceOver 只放旁白、心理活动和环境氛围，不能把旁白改成对白。',
            'dialogue 只放角色直接说出口的话；voiceOver 只放可发声但角色不张嘴的旁白/画外音，不能把旁白改成对白。心理活动默认不写入 voiceOver；脑海里闪过、想到、意识到、想象、回忆、触感、心里一动等心理内容写入 actionDesc/promptText/emotion 用画面表现。低声报数、低声说、耳语、小声说、念出、读出都属于对白，必须写入 dialogue。'),
            '旁白、心理活动和环境描述必须作为画外音处理，角色不张嘴、不做口型，用眼神、呼吸、姿态和环境变化承接情绪。',
            '旁白/画外音可发声但角色不张嘴、不做口型；心声/心理活动默认不发声、不口型，必须用眼神、呼吸、姿态和环境变化承接情绪。'),
            '严格执行音画双轨协议：视频阶段只负责画面，不新增、不改写、不替换配音、旁白声线、BGM 或音效；对白才允许口型同步，旁白和心理活动必须作为画外音处理，角色不张嘴。',
            '严格执行音画三轨协议：视频阶段只负责画面，不新增、不改写、不替换配音、旁白声线、BGM 或音效；对白才允许口型同步，旁白/画外音可发声但角色不张嘴；心声/心理活动默认不朗读、不口型，只通过眼神、动作和画面隐喻表现。'),
            '视频生成阶段只负责画面，不生成、不替换、不改变配音、旁白声线、BGM 或音效；对白为空时主体不张嘴。',
            '视频生成阶段只负责画面，不生成、不替换、不改变配音、旁白声线、BGM 或音效；对白为空时主体不张嘴；心声/心理活动默认不朗读、不口型。'),
            '音画双轨',
            '音画三轨'),
    description = replace(description, '音画双轨', '音画三轨'),
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name IN ('AI短剧剧本生成', 'AI短剧分镜提取', 'AI短剧分镜视频生成');

COMMIT;
