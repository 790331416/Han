-- AI short-drama character prompt hardening.
-- Keep this separate from the turnaround template upgrade because some running
-- databases already executed the previous script before prompt text sanitizing
-- was added in the backend.

BEGIN;

UPDATE ai_prompt_template
SET content = replace(
        replace(
            content,
            '7. 旧词屏蔽规则：如果原始角色提示词或补充要求里出现“头部特写、面部特写、三视图、正侧背”等旧版版式，只提取身份和外观特征，不执行旧版构图；最终仍以四方向全身转面表为最高优先级。',
            '7. 历史版式屏蔽规则：历史输入里的旧版头像、半身、三视图或正侧背版式只用于识别无效构图，不进入最终构图；最终只允许四方向全身转面表。'
        ),
        '原始角色提示词：{{characterPromptText}}',
        '净化后的角色外观提示词：{{characterPromptText}}'
    ),
    description = 'AI短剧角色图生成默认模板，强制四方向全身转面表，并由后端净化历史版式词',
    update_by = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE template_name = 'AI短剧角色图生成'
  AND category = 'aivideo_image';

COMMIT;
