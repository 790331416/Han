package com.han.ai.service;

import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;

/**
 * 内部语音合成服务。
 */
public interface IAiTtsGenerationService {

    /**
     * 按已配置的 TTS 模型合成语音音频。
     *
     * @param request synthesis request
     * @return synthesis response with Base64-encoded audio
     */
    AiTtsGenerateResponse synthesize(AiTtsGenerateRequest request);
}
