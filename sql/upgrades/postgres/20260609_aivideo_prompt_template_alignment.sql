-- AIVideo Prompt template alignment for storyboard continuity, props and audio strategy.
-- Keep this upgrade aligned with sql/tiers/full/full-init.sql.

BEGIN;

ALTER TABLE ai_prompt_template
    ADD COLUMN IF NOT EXISTS create_by VARCHAR(64) DEFAULT '',
    ADD COLUMN IF NOT EXISTS update_by VARCHAR(64) DEFAULT '';

ALTER TABLE ai_prompt_template
    ALTER COLUMN variables TYPE TEXT;

INSERT INTO ai_prompt_template
    (tenant_id, template_name, category, content, variables, description, built_in, status, create_by, create_time, update_by, update_time)
SELECT
    0,
    item.template_name,
    item.category,
    item.content,
    item.variables,
    item.description,
    1,
    '0',
    'system',
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP
FROM (
    VALUES
    ('AI短剧原文润色', 'aivideo_text', '请在不改变核心剧情的前提下润色短剧原文，保留人物、地点、事件因果和情绪线。', '["originalText","targetPlatform","style"]', 'AI短剧原文润色默认模板'),
    ('AI短剧剧本生成', 'aivideo_script', '你是短剧编剧。请输出可拆分为镜头的短剧本，明确场次、角色、动作、对白、旁白、心理活动和情绪提示。', '["projectName","polishedText","targetPlatform","style","durationSec"]', 'AI短剧剧本生成默认模板'),
    ('AI短剧资产提取', 'aivideo_asset', '你是短剧资产统筹。请从剧本中提取角色、场景、关键道具和分镜资产，输出结构化 JSON。', '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText"]', 'AI短剧资产提取默认模板'),
    ('AI短剧角色构建', 'aivideo_asset', '你是角色设定师。请为每个角色建立稳定角色档案，包含身份、年龄、外貌、服装、标志物、声线和负面约束。', '["projectName","style","characterList","scriptText"]', 'AI短剧角色构建默认模板'),
    ('AI短剧场景设计', 'aivideo_asset', '你是场景设定师。请为每个场景建立稳定场景档案，包含时间、天气、空间结构、核心道具和参考关系。', '["projectName","style","sceneList","scriptText"]', 'AI短剧场景设计默认模板'),
    ('AI短剧分镜提取', 'aivideo_storyboard', '你是分镜导演。请把剧本拆为可执行镜头，输出镜头秒数、运镜、衔接/转场、动作、对白、旁白、心理活动、画内角色、场景、道具和尾帧状态。', '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]', 'AI短剧分镜提取默认模板'),
    ('AI短剧角色图生成', 'aivideo_image', '你是视频生成参考角色图设计师。请生成单角色主体完整、身份稳定、便于视频模型继承的角色参考图提示词。', '["projectName","style","ratio","characterName","characterProfile","referenceImages"]', 'AI短剧角色图生成默认模板'),
    ('AI短剧场景图生成', 'aivideo_image', '你是视频生成参考场景图设计师。请生成单镜头场景锚点图提示词，禁止人物、动物、身体部位和文字水印。', '["projectName","style","ratio","sceneName","sceneProfile","referenceImages"]', 'AI短剧场景图生成默认模板'),
    ('AI短剧分镜视频生成', 'aivideo_video', '你是视频分镜导演。请输出给视频模型直接执行的提示词，锁定角色、场景、道具、动作节拍、衔接策略和声音策略。', '["projectName","style","ratio","resolution","durationSec","shotNo","cameraMove","actionDesc","dialogue","voiceOver","innerThought","emotion","characterAnchors","sceneAnchor","propAnchors","continuityRequirement","referenceAudioUrls","referenceVideoUrl"]', 'AI短剧分镜视频生成默认模板'),
    ('AI短剧后期语音合成', 'aivideo_tts', '你是短剧后期配音导演。请只提取需要真正朗读的对白和旁白，排除心理活动、画面说明、动作描述和脑海闪回；每句保留说话角色、情绪、语速和停顿建议。', '["projectName","shotNo","characterName","dialogue","voiceOver","emotion","voiceType","durationSec"]', 'AI短剧后期语音合成默认模板')
) AS item(template_name, category, content, variables, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_prompt_template t
    WHERE t.template_name = item.template_name
);

UPDATE ai_prompt_template
SET category = CASE template_name
        WHEN 'AI短剧原文润色' THEN 'aivideo_text'
        WHEN 'AI短剧剧本生成' THEN 'aivideo_script'
        WHEN 'AI短剧资产提取' THEN 'aivideo_asset'
        WHEN 'AI短剧角色构建' THEN 'aivideo_asset'
        WHEN 'AI短剧场景设计' THEN 'aivideo_asset'
        WHEN 'AI短剧分镜提取' THEN 'aivideo_storyboard'
        WHEN 'AI短剧角色图生成' THEN 'aivideo_image'
        WHEN 'AI短剧场景图生成' THEN 'aivideo_image'
        WHEN 'AI短剧分镜视频生成' THEN 'aivideo_video'
        WHEN 'AI短剧后期语音合成' THEN 'aivideo_tts'
        ELSE category
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name IN (
    'AI短剧原文润色',
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧角色构建',
    'AI短剧场景设计',
    'AI短剧分镜提取',
    'AI短剧角色图生成',
    'AI短剧场景图生成',
    'AI短剧分镜视频生成',
    'AI短剧后期语音合成'
);

WITH hard_rules AS (
    SELECT $rules$

【20260609模板对齐硬规则】
1. 道具交接硬锁：出现“递给/接过/展示给/交给/传给/拿给/递来/滚入/飘来/飞来/滑来”时，必须写清谁递给谁、什么道具、从画面哪边来、最后谁拿着；禁止“试卷飘过来”“接过某物”“展示给画外”这类无来源动作。
2. 道具连续硬锁：关键道具必须写清道具颜色、材质、形状、尺寸、归属角色和镜头结束位置；下一镜继承同一颜色和归属。
3. 方位/站位硬锁：上一镜背对、侧身、左/右站位、画内人数和视线方向必须继承；若下一镜要正面对话，必须写明转身、反打、换轴、重新建立站位或切场，否则视为不合格。
4. 在场角色硬锁：同一场景/同一 stitchGroupNo 下，上一镜在画内的角色默认仍在当前镜，除非明确离场、画外、裁切、单人反应或插入镜头；不得无说明消失。
5. 镜头衔接硬锁：CONTINUE 强制继承上一尾帧；SCENE_CUT/TIME_JUMP/MONTAGE 可新建场景；INSERT 是插入镜头/交接镜头，不强制继承上一尾帧，但必须交代和上一镜的动作关系。
6. 素材策略硬锁：连续镜头如果使用上一尾帧，就不要混入角色图/场景图/参考音频；插入镜头/交接镜头可使用上一段视频、角色图、场景图、道具图和角色参考音频。
7. 多角色声音硬锁：长期角色声线应使用 referenceAudioUrls，最多 3 段，单段 2-15 秒，总时长不超过 15 秒；超过 3 个发声角色时必须拆镜或改后期 TTS。
8. 三轨声音硬锁：说出口的写 dialogue；旁白/画外音写 voiceOver；“脑海里闪过、想到、意识到、心里一动”等心理活动默认不朗读，只写 actionDesc/promptText/emotion。
$rules$ AS block
)
UPDATE ai_prompt_template t
SET content = CASE
        WHEN t.content IS NULL OR btrim(t.content) = '' THEN hard_rules.block
        ELSE rtrim(t.content) || hard_rules.block
    END,
    built_in = 1,
    status = '0',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
FROM hard_rules
WHERE t.template_name IN (
    'AI短剧剧本生成',
    'AI短剧资产提取',
    'AI短剧角色构建',
    'AI短剧场景设计',
    'AI短剧分镜提取',
    'AI短剧角色图生成',
    'AI短剧场景图生成',
    'AI短剧分镜视频生成',
    'AI短剧后期语音合成'
)
AND COALESCE(t.content, '') NOT LIKE '%【20260609模板对齐硬规则】%';

UPDATE ai_prompt_template
SET variables = '["projectName","style","ratio","resolution","durationSec","shotNo","cameraMove","actionDesc","dialogue","voiceOver","innerThought","emotion","characterAnchors","sceneAnchor","propAnchors","continuityRequirement","referenceAudioUrls","referenceVideoUrl"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜视频生成';

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","defaultShotDuration","scriptText"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧分镜提取';

UPDATE ai_prompt_template
SET variables = '["projectName","targetPlatform","ratio","style","defaultShotDuration","scriptText"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧资产提取';

UPDATE ai_prompt_template
SET variables = '["projectName","shotNo","characterName","dialogue","voiceOver","emotion","voiceType","durationSec"]',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧后期语音合成';

WITH tpl AS (
    SELECT
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧原文润色') AS polish_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧剧本生成') AS script_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色构建') AS character_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景设计') AS scene_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧分镜提取') AS shot_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧角色图生成') AS character_image_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧场景图生成') AS scene_image_id,
        MAX(template_id) FILTER (WHERE template_name = 'AI短剧分镜视频生成') AS video_prompt_id
    FROM ai_prompt_template
)
UPDATE ai_video_project_setting s
SET polish_prompt_template_id = COALESCE(s.polish_prompt_template_id, tpl.polish_id),
    script_prompt_template_id = COALESCE(s.script_prompt_template_id, tpl.script_id),
    character_prompt_template_id = COALESCE(s.character_prompt_template_id, tpl.character_id),
    scene_prompt_template_id = COALESCE(s.scene_prompt_template_id, tpl.scene_id),
    shot_prompt_template_id = COALESCE(s.shot_prompt_template_id, tpl.shot_id),
    video_prompt_template_id = COALESCE(s.video_prompt_template_id, tpl.video_prompt_id),
    character_image_prompt_template_id = COALESCE(s.character_image_prompt_template_id, tpl.character_image_id),
    scene_image_prompt_template_id = COALESCE(s.scene_image_prompt_template_id, tpl.scene_image_id),
    update_time = CURRENT_TIMESTAMP
FROM tpl
WHERE TRUE;

DO $$
DECLARE
    v_ai_root_id BIGINT;
    v_prompt_menu_id BIGINT;
    v_next_id BIGINT;
    v_action RECORD;
BEGIN
    SELECT id
    INTO v_ai_root_id
    FROM sys_menu
    WHERE (path = 'ai' AND menu_type = 'M') OR menu_name = 'AI智能'
    ORDER BY CASE WHEN id = 500 THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_ai_root_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 500) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_ai_root_id FROM sys_menu;
        ELSE
            v_ai_root_id := 500;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
            query, menu_type, visible, status, perms, icon, is_frame, is_cache
        )
        VALUES (
            v_ai_root_id, NULL, 'AI智能', 0, '0', 5, 'ai', NULL,
            NULL, 'M', 0, 0, NULL, 'magic-stick', 1, 0
        );
    END IF;

    SELECT id
    INTO v_prompt_menu_id
    FROM sys_menu
    WHERE perms = 'ai:prompt:list'
       OR (path = 'prompt' AND component = 'ai/prompt/index')
    ORDER BY CASE WHEN id = 515 THEN 0 ELSE 1 END, id
    LIMIT 1;

    IF v_prompt_menu_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM sys_menu WHERE id = 515) THEN
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_prompt_menu_id FROM sys_menu;
        ELSE
            v_prompt_menu_id := 515;
        END IF;

        INSERT INTO sys_menu (
            id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
            query, menu_type, visible, status, perms, icon, is_frame, is_cache
        )
        VALUES (
            v_prompt_menu_id, NULL, 'Prompt模板', v_ai_root_id, '0,' || v_ai_root_id,
            6, 'prompt', 'ai/prompt/index', NULL, 'C', 0, 0,
            'ai:prompt:list', 'document', 1, 0
        );
    ELSE
        UPDATE sys_menu
        SET menu_name = 'Prompt模板',
            parent_id = v_ai_root_id,
            ancestors = '0,' || v_ai_root_id,
            sort = 6,
            path = 'prompt',
            component = 'ai/prompt/index',
            menu_type = 'C',
            visible = 0,
            status = 0,
            perms = 'ai:prompt:list',
            icon = 'document',
            is_frame = 1,
            is_cache = 0
        WHERE id = v_prompt_menu_id;
    END IF;

    FOR v_action IN
        SELECT * FROM (
            VALUES
                ('Prompt模板查询', 'ai:prompt:query', 1),
                ('Prompt模板新增', 'ai:prompt:add', 2),
                ('Prompt模板编辑', 'ai:prompt:edit', 3),
                ('Prompt模板删除', 'ai:prompt:remove', 4)
        ) AS action(menu_name, perms, sort_no)
    LOOP
        IF EXISTS (SELECT 1 FROM sys_menu WHERE perms = v_action.perms) THEN
            UPDATE sys_menu
            SET menu_name = v_action.menu_name,
                parent_id = v_prompt_menu_id,
                ancestors = '0,' || v_ai_root_id || ',' || v_prompt_menu_id,
                sort = v_action.sort_no,
                path = '',
                component = NULL,
                query = NULL,
                menu_type = 'F',
                visible = 0,
                status = 0,
                icon = '#',
                is_frame = 1,
                is_cache = 0
            WHERE perms = v_action.perms;
        ELSE
            SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM sys_menu;

            INSERT INTO sys_menu (
                id, tenant_id, menu_name, parent_id, ancestors, sort, path, component,
                query, menu_type, visible, status, perms, icon, is_frame, is_cache
            )
            VALUES (
                v_next_id, NULL, v_action.menu_name, v_prompt_menu_id,
                '0,' || v_ai_root_id || ',' || v_prompt_menu_id,
                v_action.sort_no, '', NULL, NULL, 'F', 0, 0,
                v_action.perms, '#', 1, 0
            );
        END IF;
    END LOOP;

    INSERT INTO sys_role_menu (role_id, menu_id)
    SELECT role.id, menu.id
    FROM sys_role role
    CROSS JOIN sys_menu menu
    WHERE (role.id = 1 OR role.role_key IN ('admin', 'super_admin'))
      AND (
          menu.id IN (v_ai_root_id, v_prompt_menu_id)
          OR menu.perms IN ('ai:prompt:list', 'ai:prompt:query', 'ai:prompt:add', 'ai:prompt:edit', 'ai:prompt:remove')
      )
    ON CONFLICT DO NOTHING;
END $$;

COMMIT;
