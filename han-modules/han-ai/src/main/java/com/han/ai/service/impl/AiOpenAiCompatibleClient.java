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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Minimal OpenAI-compatible client used for model connectivity checks and chat requests.
 */
@Slf4j
@Component
class AiOpenAiCompatibleClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_REQUEST_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String IMAGE_GENERATIONS_PATH = "/images/generations";
    private static final String CURL_STATUS_MARKER = "__CURL_STATUS__:";

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

    String testImageGeneration(AiModelPo model, String apiKey) {
        ImageGenerationResult result = imageGeneration(model, apiKey,
                "纯净无人室内空间，白色墙面，自然光，empty room, no humans", 1, "2048x2048");
        if (result == null || result.images().isEmpty()) {
            throw new BusinessException("图片模型连通性测试未返回有效图片");
        }
        return "图片模型真实连通成功: " + model.getProvider() + "/" + model.getModelCode()
                + " -> " + result.images().size() + " 张候选图";
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

    String chatCompletionStream(AiModelPo model, String apiKey, List<ProviderMessage> messages,
                                Integer maxTokensOverride, Consumer<String> deltaConsumer) {
        validateArguments(model, apiKey, messages);
        ChatCompletionRequest payload = buildRequest(model, messages, maxTokensOverride);
        payload.stream = true;
        URI requestUri = buildChatCompletionUri(model.getBaseUrl());
        String requestBody = XuJsonUtil.toJsonString(payload);
        try {
            String content = executeStreamingRequest(requestUri, apiKey, requestBody, deltaConsumer);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException("模型未返回有效文本内容");
            }
            return content.trim();
        } catch (IOException e) {
            log.warn("AI provider streaming request IO error, provider={}, modelCode={}", model.getProvider(), model.getModelCode(), e);
            throw new BusinessException("模型流式调用失败: " + e.getMessage());
        }
    }

    ImageGenerationResult imageGeneration(AiModelPo model, String apiKey, String prompt, Integer candidateCount, String size) {
        validateImageArguments(model, apiKey, prompt);
        ImageGenerationRequest payload = buildImageRequest(model, prompt, candidateCount, size);
        URI requestUri = buildImageGenerationUri(model.getBaseUrl());
        String requestBody = XuJsonUtil.toJsonString(payload);
        try {
            HttpResponsePayload response = executeRequest(requestUri, apiKey, requestBody);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(buildErrorMessage(response.body(), response.statusCode()));
            }
            ImageGenerationResponse generation = XuJsonUtil.parseObject(response.body(), ImageGenerationResponse.class);
            List<GeneratedImage> images = extractImages(generation);
            if (images.isEmpty()) {
                throw new BusinessException("图片模型未返回有效图片");
            }
            return new ImageGenerationResult(images);
        } catch (IOException e) {
            log.warn("AI image provider request IO error, provider={}, modelCode={}", model.getProvider(), model.getModelCode(), e);
            throw new BusinessException("图片模型调用失败: " + e.getMessage());
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

    private void validateImageArguments(AiModelPo model, String apiKey, String prompt) {
        if (model == null) {
            throw new BusinessException("图片模型配置不能为空");
        }
        if (!StringUtils.hasText(model.getBaseUrl())) {
            throw new BusinessException("图片模型 Base URL 未配置");
        }
        if (!StringUtils.hasText(model.getModelCode())) {
            throw new BusinessException("图片模型标识未配置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未找到可用的图片模型 API Key");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new BusinessException("图片生成提示词不能为空");
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

    private ImageGenerationRequest buildImageRequest(AiModelPo model, String prompt, Integer candidateCount, String size) {
        ImageGenerationRequest request = new ImageGenerationRequest();
        request.model = model.getModelCode();
        request.prompt = prompt;
        request.n = candidateCount == null || candidateCount < 1 ? 1 : Math.min(candidateCount, 4);
        request.size = StringUtils.hasText(size) ? size.trim() : "2048x2048";
        return request;
    }

    private URI buildChatCompletionUri(String baseUrl) {
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return URI.create(normalizedBaseUrl + CHAT_COMPLETIONS_PATH);
    }

    private URI buildImageGenerationUri(String baseUrl) {
        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return URI.create(normalizedBaseUrl + IMAGE_GENERATIONS_PATH);
    }

    private HttpResponsePayload executeRequest(URI requestUri, String apiKey, String requestBody) throws IOException {
        try {
            return executeWithHttpURLConnection(requestUri, apiKey, requestBody);
        } catch (UnknownHostException exception) {
            log.warn("Primary provider request hit DNS resolution issue, falling back to curl, uri={}", requestUri, exception);
            return executeWithCurl(requestUri, apiKey, requestBody);
        }
    }

    private HttpResponsePayload executeWithHttpURLConnection(URI requestUri, String apiKey, String requestBody) throws IOException {
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

    private String executeStreamingRequest(URI requestUri, String apiKey, String requestBody,
                                           Consumer<String> deltaConsumer) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) requestUri.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) STREAM_REQUEST_TIMEOUT.toMillis());
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            try (var outputStream = connection.getOutputStream()) {
                outputStream.write(bodyBytes);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = readBody(connection.getErrorStream());
                throw new BusinessException(buildErrorMessage(responseBody, statusCode));
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isStreamDoneLine(line)) {
                        break;
                    }
                    String delta = parseStreamLine(line);
                    if (delta == null) {
                        continue;
                    }
                    content.append(delta);
                    if (deltaConsumer != null) {
                        deltaConsumer.accept(delta);
                    }
                }
            }
            return content.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isStreamDoneLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String trimmed = line.trim();
        return trimmed.startsWith("data:") && "[DONE]".equals(trimmed.substring("data:".length()).trim());
    }

    private String parseStreamLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        String payload = trimmed.substring("data:".length()).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return null;
        }
        try {
            ChatCompletionStreamResponse response = XuJsonUtil.parseObject(payload, ChatCompletionStreamResponse.class);
            return extractDelta(response);
        } catch (RuntimeException exception) {
            log.debug("Ignore unparsable provider stream chunk: {}", payload, exception);
            return null;
        }
    }

    private HttpResponsePayload executeWithCurl(URI requestUri, String apiKey, String requestBody) throws IOException {
        List<String> command = List.of(
                "curl",
                "--silent",
                "--show-error",
                "--ipv4",
                "--connect-timeout",
                String.valueOf(CONNECT_TIMEOUT.toSeconds()),
                "--max-time",
                String.valueOf(REQUEST_TIMEOUT.toSeconds()),
                "-X",
                "POST",
                requestUri.toString(),
                "-H",
                HttpHeaders.AUTHORIZATION + ": Bearer " + apiKey,
                "-H",
                HttpHeaders.CONTENT_TYPE + ": " + MediaType.APPLICATION_JSON_VALUE,
                "--data-raw",
                requestBody,
                "-w",
                "\n" + CURL_STATUS_MARKER + "%{http_code}"
        );
        Process process = new ProcessBuilder(command).start();
        String responseBody;
        String errorBody;
        try (InputStream stdout = process.getInputStream(); InputStream stderr = process.getErrorStream()) {
            responseBody = readBody(stdout);
            errorBody = readBody(stderr);
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("curl request interrupted", exception);
        }
        if (exitCode != 0) {
            String message = trimResponse(errorBody);
            if (!StringUtils.hasText(message)) {
                message = trimResponse(responseBody);
            }
            throw new IOException(StringUtils.hasText(message) ? message : "curl request failed with exit code " + exitCode);
        }
        return parseCurlResponse(responseBody);
    }

    private HttpResponsePayload parseCurlResponse(String rawResponse) throws IOException {
        int markerIndex = rawResponse.lastIndexOf(CURL_STATUS_MARKER);
        if (markerIndex < 0) {
            throw new IOException("Unable to parse curl response status");
        }
        String body = rawResponse.substring(0, markerIndex).trim();
        String statusText = rawResponse.substring(markerIndex + CURL_STATUS_MARKER.length()).trim();
        try {
            return new HttpResponsePayload(Integer.parseInt(statusText), body);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid curl response status: " + statusText, exception);
        }
    }

    private String trimResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        String trimmed = responseBody.replaceAll("\\s+", " ").trim();
        if (trimmed.length() > 300) {
            return trimmed.substring(0, 300) + "...";
        }
        return trimmed;
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

    private String extractDelta(ChatCompletionStreamResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            return null;
        }
        for (StreamChoice choice : response.choices) {
            if (choice == null || choice.delta == null) {
                continue;
            }
            if (StringUtils.hasText(choice.delta.content)) {
                return choice.delta.content;
            }
        }
        return null;
    }

    private List<GeneratedImage> extractImages(ImageGenerationResponse response) {
        List<GeneratedImage> images = new ArrayList<>();
        if (response == null || response.data == null) {
            return images;
        }
        for (int i = 0; i < response.data.size(); i++) {
            ImageData data = response.data.get(i);
            if (data == null) {
                continue;
            }
            if (StringUtils.hasText(data.url) || StringUtils.hasText(data.base64Json)) {
                images.add(new GeneratedImage(i + 1, data.url, data.base64Json,
                        StringUtils.hasText(data.mimeType) ? data.mimeType : "image/png", data.revisedPrompt));
            }
        }
        return images;
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

    static class ImageGenerationRequest {
        @JsonProperty("model")
        public String model;

        @JsonProperty("prompt")
        public String prompt;

        @JsonProperty("n")
        public Integer n;

        @JsonProperty("size")
        public String size;
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
    static class ChatCompletionStreamResponse {
        @JsonProperty("choices")
        public List<StreamChoice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ImageGenerationResponse {
        @JsonProperty("data")
        public List<ImageData> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ImageData {
        @JsonProperty("url")
        public String url;

        @JsonProperty("b64_json")
        public String base64Json;

        @JsonProperty("mime_type")
        public String mimeType;

        @JsonProperty("revised_prompt")
        public String revisedPrompt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StreamChoice {
        @JsonProperty("delta")
        public ChatMessage delta;
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

    record GeneratedImage(int index, String url, String base64Data, String mimeType, String revisedPrompt) {
    }

    record ImageGenerationResult(List<GeneratedImage> images) {
    }
}
