package com.han.ai.service;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;

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
}
