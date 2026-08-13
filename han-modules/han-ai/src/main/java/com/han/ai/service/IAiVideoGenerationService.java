package com.han.ai.service;

import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;

/**
 * 内部视频生成服务。
 */
public interface IAiVideoGenerationService {

    AiVideoGenerateResponse generate(AiVideoGenerateRequest request);

    AiVideoTaskQueryResponse queryTask(AiVideoTaskQueryRequest request);

    String renderPrompt(AiVideoGenerateRequest request);
}
