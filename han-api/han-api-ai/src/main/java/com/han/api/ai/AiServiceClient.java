package com.han.api.ai;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * AI service HTTP API.
 */
@HttpExchange("/inner/ai")
public interface AiServiceClient {

    /**
     * Generate text with a configured model.
     */
    @PostExchange("/text/generate")
    R<AiTextGenerateResponse> generateText(@RequestBody AiTextGenerateRequest request);

    /**
     * Render the effective user prompt with a configured prompt template.
     */
    @PostExchange("/text/prompt/render")
    R<String> renderTextPrompt(@RequestBody AiTextGenerateRequest request);

    /**
     * Generate image candidates with a configured image model.
     */
    @PostExchange("/image/generate")
    R<AiImageGenerateResponse> generateImage(@RequestBody AiImageGenerateRequest request);

    /**
     * Submit an async video generation task with a configured video model.
     */
    @PostExchange("/video/generate")
    R<AiVideoGenerateResponse> generateVideo(@RequestBody AiVideoGenerateRequest request);

    /**
     * Query a provider video generation task.
     */
    @PostExchange("/video/task/query")
    R<AiVideoTaskQueryResponse> queryVideoTask(@RequestBody AiVideoTaskQueryRequest request);
}
