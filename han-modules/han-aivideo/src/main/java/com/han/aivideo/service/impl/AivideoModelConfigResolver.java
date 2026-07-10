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
 * Resolves AIVideo VOD edit integration config from AI model management first, then legacy environment variables.
 * TTS credential resolution has been reback to han-ai ({@code AiSpeechCredentialResolver}); business module no longer
 * resolves generic speech credentials by itself.
 */
@Component
@RequiredArgsConstructor
public class AivideoModelConfigResolver {

    static final String MODEL_TYPE_VIDEO_EDIT = "VIDEO_EDIT";

    private static final String DEFAULT_VOD_REGION = "cn-north-1";
    private static final String DEFAULT_VOD_APPLICATION = "VideoTrackHighlight";

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

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
