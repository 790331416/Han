package com.han.aivideo.service.impl;

import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves AIVideo integration configs from AI model management first, then legacy environment variables.
 */
@Component
@RequiredArgsConstructor
public class AivideoModelConfigResolver {

    static final String MODEL_TYPE_TTS = "TTS";
    static final String MODEL_TYPE_VIDEO_EDIT = "VIDEO_EDIT";

    private static final String DEFAULT_TTS_ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts";
    private static final String DEFAULT_TTS_CLUSTER = "volcano_tts";
    private static final String DEFAULT_TTS_VOICE_TYPE = "BV001_24k_streaming";
    private static final String DEFAULT_VOD_REGION = "cn-north-1";
    private static final String DEFAULT_VOD_APPLICATION = "VideoTrackHighlight";

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public TtsConfig resolveTtsConfig() {
        Map<String, Object> model = findEnabledModel(MODEL_TYPE_TTS);
        Map<String, Object> credential = parseCredential(model);
        String apiKey = text(model.get("api_key"));
        return new TtsConfig(
                firstText(text(model.get("base_url")),
                        credentialText(credential, "endpoint", "baseUrl"),
                        property("han.aivideo.tts.volc.endpoint"),
                        property("AIVIDEO_TTS_VOLC_ENDPOINT"),
                        DEFAULT_TTS_ENDPOINT),
                firstText(credentialText(credential, "appId", "appid"),
                        property("han.aivideo.tts.volc.app-id"),
                        property("AIVIDEO_TTS_VOLC_APP_ID")),
                firstText(credentialText(credential, "accessToken", "token"),
                        looksLikeJson(apiKey) ? null : apiKey,
                        property("han.aivideo.tts.volc.access-token"),
                        property("AIVIDEO_TTS_VOLC_ACCESS_TOKEN")),
                firstText(credentialText(credential, "cluster"),
                        property("han.aivideo.tts.volc.cluster"),
                        property("AIVIDEO_TTS_VOLC_CLUSTER"),
                        DEFAULT_TTS_CLUSTER),
                firstText(credentialText(credential, "defaultVoiceType", "voiceType"),
                        property("han.aivideo.tts.volc.default-voice-type"),
                        property("AIVIDEO_TTS_VOLC_DEFAULT_VOICE_TYPE"),
                        DEFAULT_TTS_VOICE_TYPE)
        );
    }

    public VodEditConfig resolveVodEditConfig() {
        Map<String, Object> model = findEnabledModel(MODEL_TYPE_VIDEO_EDIT);
        Map<String, Object> credential = parseCredential(model);
        String apiKey = text(model.get("api_key"));
        return new VodEditConfig(
                firstText(credentialText(credential, "accessKey", "accessKeyId", "ak"),
                        looksLikeJson(apiKey) ? null : apiKey,
                        property("volcengine.vod.access-key"),
                        property("VOLCENGINE_VOD_ACCESS_KEY_ID")),
                firstText(credentialText(credential, "secretKey", "secretAccessKey", "sk"),
                        property("volcengine.vod.secret-key"),
                        property("VOLCENGINE_VOD_SECRET_ACCESS_KEY")),
                firstText(credentialText(credential, "space", "uploader"),
                        property("volcengine.vod.space"),
                        property("AIVIDEO_VOD_SPACE")),
                firstText(credentialText(credential, "application"),
                        property("volcengine.vod.application"),
                        property("AIVIDEO_VOD_APPLICATION"),
                        DEFAULT_VOD_APPLICATION),
                firstText(credentialText(credential, "region"),
                        property("volcengine.vod.region"),
                        property("AIVIDEO_VOD_REGION"),
                        DEFAULT_VOD_REGION)
        );
    }

    private Map<String, Object> findEnabledModel(String modelType) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT model_name, model_type, provider, model_code, base_url, api_key, remark
                    FROM ai_model
                    WHERE status = '0' AND model_type = ?
                    ORDER BY update_time DESC, model_id DESC
                    LIMIT 1
                    """, new Object[]{modelType});
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            return rows.get(0) == null ? Map.of() : rows.get(0);
        } catch (DataAccessException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> parseCredential(Map<String, Object> model) {
        String apiKey = text(model.get("api_key"));
        if (!looksLikeJson(apiKey)) {
            return Map.of();
        }
        try {
            Map<?, ?> parsed = XuJsonUtil.parseObject(apiKey, Map.class);
            if (parsed == null || parsed.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            parsed.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private boolean looksLikeJson(String value) {
        String normalized = firstText(value);
        return normalized.startsWith("{") && normalized.endsWith("}");
    }

    private String credentialText(Map<String, Object> credential, String... keys) {
        if (credential == null || credential.isEmpty() || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = text(credential.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String property(String key) {
        return text(environment.getProperty(key));
    }

    private String text(Object value) {
        return value == null ? "" : firstText(String.valueOf(value));
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public record TtsConfig(String endpoint,
                            String appId,
                            String accessToken,
                            String cluster,
                            String defaultVoiceType) {

        public boolean configured() {
            return StringUtils.hasText(appId) && StringUtils.hasText(accessToken);
        }
    }

    public record VodEditConfig(String accessKey,
                                String secretKey,
                                String space,
                                String application,
                                String region) {

        public boolean configured() {
            return StringUtils.hasText(accessKey)
                    && StringUtils.hasText(secretKey)
                    && StringUtils.hasText(space);
        }
    }
}
