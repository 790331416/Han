package com.han.ai.service;

import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;

import java.util.function.Consumer;

/**
 * 内部文本生成服务。
 */
public interface IAiTextGenerationService {

    /**
     * 按已配置的模型与提示词生成文本。
     *
     * @param request generation request
     * @return generation response
     */
    AiTextGenerateResponse generate(AiTextGenerateRequest request);

    /**
     * 以流式方式逐块返回供应商生成的文本。
     *
     * @param request generation request
     * @param deltaConsumer chunk callback
     * @return aggregated response
     */
    AiTextGenerateResponse stream(AiTextGenerateRequest request, Consumer<String> deltaConsumer);

    /**
     * 只渲染最终的用户提示词，不调用模型。
     *
     * @param request generation request
     * @return rendered prompt
     */
    String renderPrompt(AiTextGenerateRequest request);
}
