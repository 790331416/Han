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
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 最小可用的 OpenAI 兼容协议客户端，用于模型测试与对话调用。
 */
@Slf4j
@Component
class AiOpenAiCompatibleClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    String testConnection(AiModelPo model, String apiKey) {
        String content = chatCompletion(model, apiKey, List.of(
                ProviderMessage.system("You are a connectivity checker."),
                ProviderMessage.user("Reply with OK only.")
        ), 32);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("模型连通测试未返回有效内容");
        }
        return "模型真实连通成功: " + model.getProvider() + "/" + model.getModelCode() + " -> " + content.trim();
    }

    String chatCompletion(AiModelPo model, String apiKey, List<ProviderMessage> messages, Integer maxTokensOverride) {
        validateArguments(model, apiKey, messages);
        ChatCompletionRequest payload = buildRequest(model, messages, maxTokensOverride);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildChatCompletionUri(model.getBaseUrl()))
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(XuJsonUtil.toJsonString(payload)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI provider request interrupted, provider={}, modelCode={}", model.getProvider(), model.getModelCode(), e);
            throw new BusinessException("模型调用被中断");
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
            // Provider 错误体不一定是 JSON，回退到摘要文本即可。
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
}
