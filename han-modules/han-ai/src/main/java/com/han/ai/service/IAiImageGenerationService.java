package com.han.ai.service;

import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;

/**
 * Internal image generation service.
 */
public interface IAiImageGenerationService {

    AiImageGenerateResponse generate(AiImageGenerateRequest request);

    String renderPrompt(AiImageGenerateRequest request);
}
