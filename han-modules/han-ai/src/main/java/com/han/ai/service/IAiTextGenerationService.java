package com.han.ai.service;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;

import java.util.function.Consumer;

/**
 * Internal text generation service.
 */
public interface IAiTextGenerationService {

    /**
     * Generate text by configured model and prompt.
     *
     * @param request generation request
     * @return generation response
     */
    AiTextGenerateResponse generate(AiTextGenerateRequest request);

    /**
     * Generate text by streaming provider chunks.
     *
     * @param request generation request
     * @param deltaConsumer chunk callback
     * @return aggregated response
     */
    AiTextGenerateResponse stream(AiTextGenerateRequest request, Consumer<String> deltaConsumer);

    /**
     * Render the effective user prompt without invoking a model.
     *
     * @param request generation request
     * @return rendered prompt
     */
    String renderPrompt(AiTextGenerateRequest request);
}
