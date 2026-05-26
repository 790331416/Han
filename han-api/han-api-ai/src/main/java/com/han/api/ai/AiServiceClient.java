package com.han.api.ai;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
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
}
