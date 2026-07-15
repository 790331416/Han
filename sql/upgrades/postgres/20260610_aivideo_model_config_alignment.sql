-- AIVideo model-management alignment for post-production TTS and video editing.
-- Keep rows disabled by default; fill API Key JSON in AI模型管理 and enable manually.

DO $$
BEGIN
    IF to_regclass('public.ai_model') IS NOT NULL THEN
        INSERT INTO ai_model (model_name, model_type, provider, model_code, base_url, api_key, max_tokens, temperature, status, remark)
        SELECT '火山语音合成', 'TTS', 'volcengine', 'volc-tts',
               'https://openspeech.bytedance.com/api/v1/tts', '', 256, 0.70, '1',
               'API Key填JSON: appId/accessToken/cluster/defaultVoiceType'
        WHERE NOT EXISTS (
            SELECT 1 FROM ai_model
            WHERE model_type = 'TTS'
              AND provider = 'volcengine'
              AND model_code = 'volc-tts'
        );

        INSERT INTO ai_model (model_name, model_type, provider, model_code, base_url, api_key, max_tokens, temperature, status, remark)
        SELECT '火山 VOD 视频剪辑合成', 'VIDEO_EDIT', 'volcengine', 'vod-direct-edit',
               'https://vod.volcengineapi.com', '', 256, 0.70, '1',
               'API Key填JSON: accessKey/secretKey/space/application/region'
        WHERE NOT EXISTS (
            SELECT 1 FROM ai_model
            WHERE model_type = 'VIDEO_EDIT'
              AND provider = 'volcengine'
              AND model_code = 'vod-direct-edit'
        );
    ELSE
        RAISE NOTICE 'relation public.ai_model does not exist, skipping AIVideo model seed alignment';
    END IF;
END $$;
