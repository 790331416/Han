package com.han.ai.service;

import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;

/**
 * Internal video generation service.
 */
public interface IAiVideoGenerationService {

    AiVideoGenerateResponse generate(AiVideoGenerateRequest request);

    AiVideoTaskQueryResponse queryTask(AiVideoTaskQueryRequest request);

    String renderPrompt(AiVideoGenerateRequest request);
}
