package com.han.ai.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
 * Volcengine Doubao speech (TTS) client. The Volcengine speech protocol differs from the
 * OpenAI-compatible endpoints used for chat/image/video, so it is implemented as a dedicated client.
 */
@Slf4j
@Component
class AiVolcSpeechClient {

    private static final int SUCCESS_CODE = 3000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    SpeechResult synthesize(SpeechCredential credential, SpeechRequest request) {
        if (credential == null || !credential.configured()) {
            throw new BusinessException("火山语音合成未配置，请在 AI模型管理中新增并启用 TTS 模型，或设置 AIVIDEO_TTS_VOLC_APP_ID 和 AIVIDEO_TTS_VOLC_ACCESS_TOKEN");
        }
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new BusinessException("TTS文本不能为空");
        }
        String requestId = StringUtils.hasText(request.requestId()) ? request.requestId().trim() : UUID.randomUUID().toString();
        Map<String, Object> body = buildRequestBody(credential, request, requestId);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(credential.endpoint()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer;" + credential.accessToken())
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
        if (payload == null) {
            throw new BusinessException("火山语音合成返回内容不是有效 JSON");
        }
        int code = parseInt(payload.get("code"), -1);
        if (code != SUCCESS_CODE) {
            throw new BusinessException("火山语音合成失败(" + code + "): " + text(payload.get("message")));
        }
        String base64 = text(payload.get("data"));
        if (!StringUtils.hasText(base64)) {
            throw new BusinessException("火山语音合成未返回音频数据");
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        Map<?, ?> addition = payload.get("addition") instanceof Map<?, ?> map ? map : Map.of();
        Integer durationMs = parseInteger(addition.get("duration"));
        return new SpeechResult(requestId, "audio/mpeg", "mp3", durationMs, bytes);
    }

    private Map<String, Object> buildRequestBody(SpeechCredential credential, SpeechRequest request, String requestId) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("appid", credential.appId());
        app.put("token", credential.accessToken());
        app.put("cluster", credential.cluster());

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", "han-ai");

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("voice_type", StringUtils.hasText(request.voiceType()) ? request.voiceType().trim() : credential.defaultVoiceType());
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int parseInt(Object value, int fallback) {
        Integer parsed = parseInteger(value);
        return parsed == null ? fallback : parsed;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    record SpeechCredential(String endpoint, String appId, String accessToken, String cluster, String defaultVoiceType) {

        boolean configured() {
            return StringUtils.hasText(appId) && StringUtils.hasText(accessToken) && StringUtils.hasText(endpoint);
        }
    }

    record SpeechRequest(String text, String voiceType, BigDecimal speedRatio, BigDecimal volumeRatio,
                         BigDecimal pitchRatio, String requestId) {
    }

    record SpeechResult(String providerRequestId, String mimeType, String extension, Integer durationMs, byte[] bytes) {
    }
}
