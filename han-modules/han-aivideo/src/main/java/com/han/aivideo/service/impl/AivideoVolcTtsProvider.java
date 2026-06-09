package com.han.aivideo.service.impl;

import com.han.aivideo.service.AivideoTtsProvider;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Volcengine Doubao Speech TTS provider.
 */
@Service
public class AivideoVolcTtsProvider implements AivideoTtsProvider {

    private static final String DEFAULT_ENDPOINT = "https://openspeech.bytedance.com/api/v1/tts";
    private static final String DEFAULT_CLUSTER = "volcano_tts";
    private static final String DEFAULT_VOICE_TYPE = "BV001_24k_streaming";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final String endpoint;
    private final String appId;
    private final String accessToken;
    private final String cluster;
    private final String defaultVoiceType;

    public AivideoVolcTtsProvider(
            @Value("${han.aivideo.tts.volc.endpoint:${AIVIDEO_TTS_VOLC_ENDPOINT:https://openspeech.bytedance.com/api/v1/tts}}") String endpoint,
            @Value("${han.aivideo.tts.volc.app-id:${AIVIDEO_TTS_VOLC_APP_ID:}}") String appId,
            @Value("${han.aivideo.tts.volc.access-token:${AIVIDEO_TTS_VOLC_ACCESS_TOKEN:}}") String accessToken,
            @Value("${han.aivideo.tts.volc.cluster:${AIVIDEO_TTS_VOLC_CLUSTER:volcano_tts}}") String cluster,
            @Value("${han.aivideo.tts.volc.default-voice-type:${AIVIDEO_TTS_VOLC_DEFAULT_VOICE_TYPE:BV001_24k_streaming}}") String defaultVoiceType) {
        this.endpoint = firstText(endpoint, DEFAULT_ENDPOINT);
        this.appId = firstText(appId);
        this.accessToken = firstText(accessToken);
        this.cluster = firstText(cluster, DEFAULT_CLUSTER);
        this.defaultVoiceType = firstText(defaultVoiceType, DEFAULT_VOICE_TYPE);
    }

    @Override
    public TtsAudio synthesize(TtsRequest request) {
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new BusinessException("TTS文本不能为空");
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(accessToken)) {
            throw new BusinessException("火山语音合成未配置，请设置 AIVIDEO_TTS_VOLC_APP_ID 和 AIVIDEO_TTS_VOLC_ACCESS_TOKEN");
        }
        String requestId = firstText(request.requestId(), UUID.randomUUID().toString());
        Map<String, Object> body = buildRequestBody(request, requestId);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer;" + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(XuJsonUtil.toJsonString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new BusinessException("火山语音合成请求失败：" + exception.getMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("火山语音合成HTTP失败(" + response.statusCode() + "): " + response.body());
        }
        Map<String, Object> payload = XuJsonUtil.parseObject(response.body(), Map.class);
        int code = parseInt(payload.get("code"), -1);
        if (code != 3000) {
            throw new BusinessException("火山语音合成失败(" + code + "): " + firstText(String.valueOf(payload.get("message"))));
        }
        String base64 = firstText(String.valueOf(payload.get("data")));
        if (!StringUtils.hasText(base64)) {
            throw new BusinessException("火山语音合成未返回音频数据");
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        Map<?, ?> addition = payload.get("addition") instanceof Map<?, ?> map ? map : Map.of();
        Integer durationMs = parseInt(addition.get("duration"), null);
        return new TtsAudio(requestId, "audio/mpeg", "mp3", durationMs, bytes);
    }

    private Map<String, Object> buildRequestBody(TtsRequest request, String requestId) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("appid", appId);
        app.put("token", accessToken);
        app.put("cluster", cluster);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", "aivideo");

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("voice_type", firstText(request.voiceType(), defaultVoiceType));
        audio.put("encoding", "mp3");
        audio.put("rate", 24000);
        audio.put("speed_ratio", ratio(request.speedRatio()));
        audio.put("volume_ratio", ratio(request.volumeRatio()));
        audio.put("pitch_ratio", ratio(request.pitchRatio()));
        audio.put("language", "cn");

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("reqid", requestId);
        req.put("text", request.text());
        req.put("text_type", "plain");
        req.put("operation", "query");
        req.put("silence_duration", "125");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", app);
        body.put("user", user);
        body.put("audio", audio);
        body.put("request", req);
        return body;
    }

    private double ratio(BigDecimal value) {
        return value == null ? 1.0D : value.doubleValue();
    }

    private int parseInt(Object value, int fallback) {
        Integer parsed = parseInt(value, null);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseInt(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
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
}
