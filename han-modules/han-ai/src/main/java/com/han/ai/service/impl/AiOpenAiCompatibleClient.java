package com.han.ai.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.han.ai.domain.po.AiModelPo;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal OpenAI-compatible client used for model connectivity checks and chat requests.
 */
@Slf4j
@Component
class AiOpenAiCompatibleClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    String testConnection(AiModelPo model, String apiKey) {
        String content = chatCompletion(model, apiKey, List.of(
                ProviderMessage.system("You are a connectivity checker."),
                ProviderMessage.user("Reply with OK only.")
        ), 32);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("模型连通性测试未返回有效内容");
        }
        return "模型真实连通成功: " + model.getProvider() + "/" + model.getModelCode() + " -> " + content.trim();
    }

    String chatCompletion(AiModelPo model, String apiKey, List<ProviderMessage> messages, Integer maxTokensOverride) {
        validateArguments(model, apiKey, messages);
        ChatCompletionRequest payload = buildRequest(model, messages, maxTokensOverride);
        URI requestUri = buildChatCompletionUri(model.getBaseUrl());
        String requestBody = XuJsonUtil.toJsonString(payload);
        try {
            HttpResponsePayload response = executeRequest(requestUri, apiKey, requestBody);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(buildErrorMessage(response.body(), response.statusCode()));
            }
            ChatCompletionResponse completion = XuJsonUtil.parseObject(response.body(), ChatCompletionResponse.class);
            String content = extractContent(completion);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("模型未返回有效文本内容");
            }
            return content.trim();
        } catch (IOException e) {
            log.warn("AI provider request IO error, provider={}, modelCode={}", model.getProvider(), model.getModelCode(), e);
            throw new BusinessException("模型调用失败: " + e.getMessage());
        }
    }

    private void validateArguments(AiModelPo model, String apiKey, List<ProviderMessage> messages) {
        if (model == null) {
            throw new BusinessException("模型配置不能为空");
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new BusinessException("模型 Base URL 未配置");
        }
        if (!StringUtils.hasText(model.getModelCode())) {
            throw new BusinessException("模型标识未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未找到可用的模型 API Key");
        }
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException("模型请求消息不能为空");
        }
    }

    private ChatCompletionRequest buildRequest(AiModelPo model, List<ProviderMessage> messages, Integer maxTokensOverride) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.model = model.getModelCode();
        request.stream = false;
        request.temperature = resolveTemperature(model.getTemperature());
        request.maxTokens = resolveMaxTokens(model.getMaxTokens(), maxTokensOverride);
        request.messages = new ArrayList<>();
        for (ProviderMessage message : messages) {
            request.messages.add(new ChatMessage(message.role(), message.content()));
        }
        return request;
    }

    private URI buildChatCompletionUri(String baseUrl) {
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return URI.create(normalizedBaseUrl + CHAT_COMPLETIONS_PATH);
    }

    private HttpResponsePayload executeRequest(URI requestUri, String apiKey, String requestBody) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) REQUEST_TIMEOUT.toMillis());
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            try (var outputStream = connection.getOutputStream()) {
                outputStream.write(bodyBytes);
            }
            int statusCode = connection.getResponseCode();
            String responseBody = readBody(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            return new HttpResponsePayload(statusCode, responseBody);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private double resolveTemperature(BigDecimal temperature) {
        return temperature != null ? temperature.doubleValue() : 0.7D;
    }

    private int resolveMaxTokens(Integer configuredMaxTokens, Integer maxTokensOverride) {
        if (maxTokensOverride != null && maxTokensOverride > 0) {
            return maxTokensOverride;
        }
        if (configuredMaxTokens != null && configuredMaxTokens > 0) {
            return configuredMaxTokens;
        }
        return 2048;
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            return null;
        }
        for (Choice choice : response.choices) {
            if (choice == null || choice.message == null) {
                continue;
            }
            if (StringUtils.hasText(choice.message.content)) {
                return choice.message.content;
            }
        }
        return null;
    }

    private String buildErrorMessage(String body, int statusCode) {
        try {
            ErrorEnvelope envelope = XuJsonUtil.parseObject(body, ErrorEnvelope.class);
            if (envelope != null && envelope.error != null && StringUtils.hasText(envelope.error.message)) {
                return "模型调用失败(" + statusCode + "): " + envelope.error.message;
            }
        } catch (RuntimeException ignored) {
            // Provider error bodies are not guaranteed to be JSON.
        }
        String excerpt = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 180) {
            excerpt = excerpt.substring(0, 180) + "...";
        }
        return "模型调用失败(" + statusCode + "): " + excerpt;
    }

    record ProviderMessage(String role, String content) {

        static ProviderMessage system(String content) {
            return new ProviderMessage("system", content);
        }

        static ProviderMessage user(String content) {
            return new ProviderMessage("user", content);
        }

        static ProviderMessage assistant(String content) {
            return new ProviderMessage("assistant", content);
        }
    }

    static class ChatCompletionRequest {
        @JsonProperty("model")
        public String model;

        @JsonProperty("messages")
        public List<ChatMessage> messages;

        @JsonProperty("temperature")
        public Double temperature;

        @JsonProperty("max_tokens")
        public Integer maxTokens;

        @JsonProperty("stream")
        public Boolean stream;
    }

    static class ChatMessage {
        @JsonProperty("role")
        public String role;

        @JsonProperty("content")
        public String content;

        ChatMessage() {
        }

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChatCompletionResponse {
        @JsonProperty("choices")
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Choice {
        @JsonProperty("message")
        public ChatMessage message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ErrorEnvelope {
        @JsonProperty("error")
        public ErrorBody error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ErrorBody {
        @JsonProperty("message")
        public String message;
    }

    record HttpResponsePayload(int statusCode, String body) {
    }
}
