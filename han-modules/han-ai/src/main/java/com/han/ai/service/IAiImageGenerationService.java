package com.han.ai.service;

import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;

/**
 * 内部图像生成服务。
 */
public interface IAiImageGenerationService {

    AiImageGenerateResponse generate(AiImageGenerateRequest request);

    String renderPrompt(AiImageGenerateRequest request);
}
