package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.ai.domain.po.AiModelPo;
import com.han.ai.mapper.AiModelMapper;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 火山引擎语音（TTS）的结构化凭据优先取自 AI 模型管理，其次才回落到环境变量。
 * 这样通用语音客户端的凭据留在 han-ai 内部，不外泄到各业务模块。
 */
@Component
@RequiredArgsConstructor
class AiSpeechCredentialResolver {

    static final String MODEL_TYPE_TTS = "TTS";
    static final String STATUS_ENABLED = "0";

    private static final String DEFAULT_TTS_ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts";
    private static final String DEFAULT_TTS_CLUSTER = "volcano_tts";
    private static final String DEFAULT_TTS_VOICE_TYPE = "BV001_24k_streaming";

    private final AiModelMapper aiModelMapper;
    private final Environment environment;

    AiVolcSpeechClient.SpeechCredential resolveTtsCredential(Long modelId, Long tenantId) {
        AiModelPo model = findModel(modelId, tenantId);
        Map<String, Object> credential = parseCredential(model);
        String apiKey = model == null ? null : model.getApiKey();
        String endpoint = firstText(
                model == null ? null : model.getBaseUrl(),
                credentialText(credential, "endpoint", "baseUrl"),
                property("han.aivideo.tts.volc.endpoint"),
                property("AIVIDEO_TTS_VOLC_ENDPOINT"),
                DEFAULT_TTS_ENDPOINT);
        String appId = firstText(
                credentialText(credential, "appId", "appid"),
                property("han.aivideo.tts.volc.app-id"),
                property("AIVIDEO_TTS_VOLC_APP_ID"));
        String accessToken = firstText(
                credentialText(credential, "accessToken", "token"),
                looksLikeJson(apiKey) ? null : trimToNull(apiKey),
                property("han.aivideo.tts.volc.access-token"),
                property("AIVIDEO_TTS_VOLC_ACCESS_TOKEN"));
        String cluster = firstText(
                credentialText(credential, "cluster"),
                property("han.aivideo.tts.volc.cluster"),
                property("AIVIDEO_TTS_VOLC_CLUSTER"),
                DEFAULT_TTS_CLUSTER);
        String defaultVoiceType = firstText(
                credentialText(credential, "defaultVoiceType", "voiceType"),
                property("han.aivideo.tts.volc.default-voice-type"),
                property("AIVIDEO_TTS_VOLC_DEFAULT_VOICE_TYPE"),
                DEFAULT_TTS_VOICE_TYPE);
        return new AiVolcSpeechClient.SpeechCredential(endpoint, appId, accessToken, cluster, defaultVoiceType);
    }

    private AiModelPo findModel(Long modelId, Long tenantId) {
        if (modelId != null) {
            AiModelPo model = aiModelMapper.selectById(modelId);
            if (model != null && MODEL_TYPE_TTS.equalsIgnoreCase(model.getModelType())) {
                return model;
            }
        }
        LambdaQueryWrapper<AiModelPo> wrapper = new LambdaQueryWrapper<AiModelPo>()
                .eq(AiModelPo::getModelType, MODEL_TYPE_TTS)
                .eq(AiModelPo::getStatus, STATUS_ENABLED)
                .orderByDesc(AiModelPo::getUpdateTime)
                .orderByDesc(AiModelPo::getModelId)
                .last("LIMIT 1");
        if (tenantId != null && tenantId > 0) {
            wrapper.and(q -> q.eq(AiModelPo::getTenantId, tenantId)
                    .or().eq(AiModelPo::getTenantId, 0L)
                    .or().isNull(AiModelPo::getTenantId));
        }
        return aiModelMapper.selectList(wrapper).stream().findFirst().orElse(null);
    }

    private Map<String, Object> parseCredential(AiModelPo model) {
        String apiKey = model == null ? null : model.getApiKey();
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
        String normalized = trimToEmpty(value);
        return normalized.startsWith("{") && normalized.endsWith("}");
    }

    private String credentialText(Map<String, Object> credential, String... keys) {
        if (credential == null || credential.isEmpty() || keys == null) {
            return "";
        }
        for (String key : keys) {
            Object value = credential.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String property(String key) {
        return trimToEmpty(environment.getProperty(key));
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
