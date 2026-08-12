package com.han.api.ai;

import com.han.api.ai.domain.AiImageGenerateRequest;
import com.han.api.ai.domain.AiImageGenerateResponse;
import com.han.api.ai.domain.AiTextGenerateRequest;
import com.han.api.ai.domain.AiTextGenerateResponse;
import com.han.api.ai.domain.AiTtsGenerateRequest;
import com.han.api.ai.domain.AiTtsGenerateResponse;
import com.han.api.ai.domain.AiVideoGenerateRequest;
import com.han.api.ai.domain.AiVideoGenerateResponse;
import com.han.api.ai.domain.AiVideoTaskQueryRequest;
import com.han.api.ai.domain.AiVideoTaskQueryResponse;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * AI 服务内部调用契约（han-aivideo / 其它业务模块 → han-ai）。
 *
 * <p><b>幂等性与重试</b>（底座实现重试策略时必须按此分级，不得一刀切）：
 * <ul>
 *   <li>幂等、可安全重试：{@link #renderTextPrompt}、{@link #renderImagePrompt}、
 *       {@link #renderVideoPrompt}、{@link #queryVideoTask} —— 纯渲染或纯查询，不落库不计费。</li>
 *   <li><b>非幂等且直接产生费用，禁止重试</b>：{@link #generateText}、{@link #generateImage}、
 *       {@link #generateVideo}、{@link #generateTts} —— 每次调用都会真实打到模型厂商并计费/扣配额。
 *       只有「TCP 连接尚未建立成功」这一种失败可以换实例重试；一旦请求已发出（含读超时），
 *       一律不得重试，否则会重复计费。</li>
 * </ul>
 *
 * <p><b>超时分级</b>：生成类接口耗时远高于系统查询类，读超时必须单独放宽（视频生成为异步
 * 提交，返回 taskId 即可，仍建议 ≥30s；文本/图片/TTS 同步返回，建议 ≥60s）。
 * 具体值由 {@code HttpClientFactoryBean} 按客户端接口维度配置。
 *
 * <p><b>租户身份</b>：请求体里的 {@code tenantId} 是调用方自报的、不可信的过渡字段，
 * 服务端必须优先采用内部签名覆盖的 {@code X-Tenant-Id} 请求头，详见各请求 DTO 的字段说明。
 *
 * <p><b>未纳入本契约的服务端端点</b>：{@code POST /inner/ai/text/generate/stream} 是 SSE
 * 流式接口，{@code @HttpExchange} 无法表达，故不在此声明。调用方不要以为它不存在，
 * 也不要为它另写一份内部签名逻辑 —— 流式调用应当收口到底座提供的公共组件。
 */
@HttpExchange("/inner/ai")
public interface AiServiceClient {

    /**
     * Generate text with a configured model.
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/text/generate")
    R<AiTextGenerateResponse> generateText(@RequestBody AiTextGenerateRequest request);

    /**
     * Render the effective user prompt with a configured prompt template.
     *
     * <p>幂等、不计费。
     */
    @PostExchange("/text/prompt/render")
    R<String> renderTextPrompt(@RequestBody AiTextGenerateRequest request);

    /**
     * Generate image candidates with a configured image model.
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/image/generate")
    R<AiImageGenerateResponse> generateImage(@RequestBody AiImageGenerateRequest request);

    /**
     * Render the effective image prompt with a configured prompt template.
     *
     * <p>幂等、不计费。对应服务端 {@code IAiImageController#renderImagePrompt}。
     */
    @PostExchange("/image/prompt/render")
    R<String> renderImagePrompt(@RequestBody AiImageGenerateRequest request);

    /**
     * Submit an async video generation task with a configured video model.
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/video/generate")
    R<AiVideoGenerateResponse> generateVideo(@RequestBody AiVideoGenerateRequest request);

    /**
     * Render the effective video prompt with a configured prompt template.
     *
     * <p>幂等、不计费。对应服务端 {@code IAiVideoController#renderVideoPrompt}。
     */
    @PostExchange("/video/prompt/render")
    R<String> renderVideoPrompt(@RequestBody AiVideoGenerateRequest request);

    /**
     * Query a provider video generation task.
     *
     * <p>幂等、不计费（HTTP 动词是 POST 只因为入参是对象，语义上是查询）。
     */
    @PostExchange("/video/task/query")
    R<AiVideoTaskQueryResponse> queryVideoTask(@RequestBody AiVideoTaskQueryRequest request);

    /**
     * Synthesize speech audio with a configured TTS model.
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/tts/synthesize")
    R<AiTtsGenerateResponse> generateTts(@RequestBody AiTtsGenerateRequest request);
}
