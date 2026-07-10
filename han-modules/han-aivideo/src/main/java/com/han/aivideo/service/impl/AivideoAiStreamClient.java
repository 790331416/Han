package com.han.aivideo.service.impl;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.common.core.config.InnerAuthProperties;
import com.han.common.core.constant.Constants;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.InnerAuthSignUtil;
import com.han.common.core.util.XuJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Streaming bridge from han-aivideo to han-ai internal text generation.
 */
@Component
@RequiredArgsConstructor
class AivideoAiStreamClient {

    private static final String AI_SERVICE_NAME = "han-ai";
    private static final String STREAM_PATH = "/inner/ai/text/generate/stream";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(300);

    private final LoadBalancerClient loadBalancerClient;
    private final InnerAuthProperties innerAuthProperties;
    private final Environment environment;

    StreamResult streamText(AiTextGenerateRequest request, Consumer<String> deltaConsumer) {
        ServiceInstance instance = loadBalancerClient.choose(AI_SERVICE_NAME);
        if (instance == null) {
            throw new BusinessException("AI 文本服务暂不可用");
        }
        URI requestUri = instance.getUri().resolve(STREAM_PATH);
        String requestBody = XuJsonUtil.toJsonString(request);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
            applyInnerAuthHeaders(connection);

            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            try (var outputStream = connection.getOutputStream()) {
                outputStream.write(bodyBytes);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new BusinessException("AI 文本服务流式请求失败(" + statusCode + "): "
                        + readBody(connection.getErrorStream()));
            }
            return consumeStream(connection.getInputStream(), deltaConsumer);
        } catch (IOException exception) {
            throw new BusinessException("AI 文本服务流式请求失败: " + exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private StreamResult consumeStream(InputStream inputStream, Consumer<String> deltaConsumer) throws IOException {
        StringBuilder content = new StringBuilder();
        Map<String, Object> meta = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trimmed.substring("data:".length()).trim();
                if ("[DONE]".equals(payload)) {
                    break;
                }
                StreamEvent event = parseEvent(payload);
                if (event == null) {
                    continue;
                }
                if ("error".equals(event.type())) {
                    throw new BusinessException(String.valueOf(event.content()));
                }
                if ("meta".equals(event.type()) && event.content() instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        meta.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    continue;
                }
                if ("delta".equals(event.type()) && event.content() != null) {
                    String chunk = String.valueOf(event.content());
                    content.append(chunk);
                    if (deltaConsumer != null) {
                        deltaConsumer.accept(chunk);
                    }
                }
            }
        }
        return new StreamResult(content.toString(), meta);
    }

    @SuppressWarnings("unchecked")
    private StreamEvent parseEvent(String payload) {
        try {
            Map<String, Object> map = XuJsonUtil.parseObject(payload, Map.class);
            if (map == null || !map.containsKey("type")) {
                return null;
            }
            return new StreamEvent(String.valueOf(map.get("type")), map.get("content"));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void applyInnerAuthHeaders(HttpURLConnection connection) {
        if (innerAuthProperties == null || !innerAuthProperties.isEnabled()
                || !StringUtils.hasText(innerAuthProperties.getSecret())) {
            return;
        }
        String clientName = environment.getProperty("spring.application.name", "han-aivideo");
        long timestamp = System.currentTimeMillis();
        String signature = InnerAuthSignUtil.sign(clientName, "POST", STREAM_PATH, timestamp, innerAuthProperties.getSecret());
        connection.setRequestProperty(Constants.INNER_AUTH_CLIENT_HEADER, clientName);
        connection.setRequestProperty(Constants.INNER_AUTH_TIMESTAMP_HEADER, String.valueOf(timestamp));
        connection.setRequestProperty(Constants.INNER_AUTH_SIGNATURE_HEADER, signature);
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record StreamResult(String content, Map<String, Object> meta) {
    }

    private record StreamEvent(String type, Object content) {
    }
}
