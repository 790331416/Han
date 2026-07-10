-- AI short-drama MVP 1 text workflow prompt seed.
-- This script is idempotent and does not store API keys.

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧原文润色', 'aivideo_text',
$$请将以下原文润色为适合 AI 短剧改编的文本。
要求：
1. 保留主线、人物关系和核心冲突。
2. 强化人物动机、情绪转折、戏剧张力和画面感。
3. 语言清晰可拍，避免过度文学化。
4. 输出完整润色稿，不要输出解释。

项目：{{projectName}}
风格：{{style}}
原文：
{{rawText}}$$,
'["projectName","style","rawText"]', 'AI短剧原文润色默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧原文润色');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧剧本生成', 'aivideo_text',
$$请将以下润色文本改写为短剧剧本。
要求：
1. 按场次组织，包含人物、场景、动作、对白、旁白和情绪提示。
2. 每个场次都要具备可拍摄的空间、行为和冲突推进。
3. 镜头描述要能继续拆分为分镜，避免空泛形容。
4. 输出短剧剧本正文，不要输出解释。

项目：{{projectName}}
目标平台：{{targetPlatform}}
画幅：{{ratio}}
润色文本：
{{polishedText}}$$,
'["projectName","targetPlatform","ratio","polishedText"]', 'AI短剧剧本生成默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧剧本生成');

INSERT INTO ai_prompt_template (tenant_id, template_name, category, content, variables, description, built_in, status)
SELECT NULL, 'AI短剧资产提取', 'aivideo_text',
$$请从短剧剧本中提取人物、场景、分镜，必须只输出 JSON 对象，不要输出解释。
JSON 结构：
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
      "durationSec": 5,
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

项目：{{projectName}}
剧本：
{{scriptText}}$$,
'["projectName","scriptText"]', 'AI短剧人物场景分镜提取默认模板', 1, '0'
WHERE NOT EXISTS (SELECT 1 FROM ai_prompt_template WHERE template_name = 'AI短剧资产提取');
