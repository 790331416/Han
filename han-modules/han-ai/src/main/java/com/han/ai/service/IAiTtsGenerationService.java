package com.han.ai.service;

import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;

/**
 * Internal text-to-speech generation service.
 */
public interface IAiTtsGenerationService {

    /**
     * Synthesize speech audio by configured TTS model.
     *
     * @param request synthesis request
     * @return synthesis response with Base64-encoded audio
     */
    AiTtsGenerateResponse synthesize(AiTtsGenerateRequest request);
}
