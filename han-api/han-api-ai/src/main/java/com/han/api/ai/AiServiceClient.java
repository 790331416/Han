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
 * <p><b>租户与用户上下文（本契约的权威约定，各请求 DTO 的 {@code tenantId} 字段引用此处）</b>
 *
 * <p>身份必须由请求头承载，不能由请求体自报。底座在发起内部调用时，需要从当前线程的
 * {@code SecurityContext} 取出身份并注入下列头，<b>且把这三个头纳入内部签名的计算范围</b>
 * （只注入不签名等于给了伪造身份的口子）：
 * <ul>
 *   <li>{@code X-Tenant-Id}（{@code Constants.TENANT_ID_HEADER}）：当前租户ID；</li>
 *   <li>{@code X-User-Id}（{@code Constants.USER_ID_HEADER}）：当前用户ID；</li>
 *   <li>{@code X-User-Name}（{@code Constants.USERNAME_HEADER}）：当前用户名。</li>
 * </ul>
 * 这三个头与网关到微服务那一跳的做法一致（网关 {@code AuthFilter} 注入、
 * {@code HeaderAuthenticationFilter} 消费重建 {@code LoginUser}），服务间调用照抄同一套模式即可，
 * 区别只在于可信度要靠内部签名覆盖来保证。
 *
 * <p>请求体里的 {@code tenantId} 是头透传能力缺失时期的临时替代：由调用方自己填，
 * 服务端没有任何独立来源可以校验，<b>调用方填谁就是谁</b>。它是过渡字段，已标 {@code @Deprecated}。
 *
 * <p>迁移分两步，不能一步到位（否则新老服务混部时会互相打不通）：
 * <ol>
 *   <li>头就位后：服务端「头优先、字段回退」，两处都读，对外行为不变；</li>
 *   <li>全部调用方升级完成后：服务端只读头，字段从契约移除。</li>
 * </ol>
 *
 * <p><b>取不到租户身份时必须 fail-close</b>：头与字段都为空时，服务端只允许使用平台级模型
 * （{@code model.tenantId} 为空或 {@code <= 0}），不得放行任意租户的私有模型 —— 那会连带用掉
 * 该租户配置的 API Key 与用量。当前 {@code AiTextGenerationServiceImpl} /
 * {@code AiImageGenerationServiceImpl} / {@code AiVideoGenerationServiceImpl} 三处的
 * {@code tenantCanUseModel} 在 {@code requestTenantId} 为空时直接 {@code return true}，
 * 是 fail-open，必须翻转。
 *
 * <p><b>未纳入本契约的服务端端点</b>：{@code POST /inner/ai/text/generate/stream} 是 SSE
 * 流式接口，{@code @HttpExchange} 无法表达，故不在此声明。调用方不要以为它不存在，
 * 也不要为它另写一份内部签名逻辑 —— 流式调用应当收口到底座提供的公共组件。
 */
@HttpExchange("/inner/ai")
public interface AiServiceClient {

    /**
     * 用已配置的模型生成文本。
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/text/generate")
    R<AiTextGenerateResponse> generateText(@RequestBody AiTextGenerateRequest request);

    /**
     * 用已配置的提示词模板渲染出最终的用户提示词。
     *
     * <p>幂等、不计费。
     */
    @PostExchange("/text/prompt/render")
    R<String> renderTextPrompt(@RequestBody AiTextGenerateRequest request);

    /**
     * 用已配置的图像模型生成候选图片。
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/image/generate")
    R<AiImageGenerateResponse> generateImage(@RequestBody AiImageGenerateRequest request);

    /**
     * 用已配置的提示词模板渲染出最终的绘图提示词。
     *
     * <p>幂等、不计费。对应服务端 {@code IAiImageController#renderImagePrompt}。
     */
    @PostExchange("/image/prompt/render")
    R<String> renderImagePrompt(@RequestBody AiImageGenerateRequest request);

    /**
     * 用已配置的视频模型提交异步视频生成任务。
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/video/generate")
    R<AiVideoGenerateResponse> generateVideo(@RequestBody AiVideoGenerateRequest request);

    /**
     * 用已配置的提示词模板渲染出最终的视频提示词。
     *
     * <p>幂等、不计费。对应服务端 {@code IAiVideoController#renderVideoPrompt}。
     */
    @PostExchange("/video/prompt/render")
    R<String> renderVideoPrompt(@RequestBody AiVideoGenerateRequest request);

    /**
     * 查询供应商侧的视频生成任务。
     *
     * <p>幂等、不计费（HTTP 动词是 POST 只因为入参是对象，语义上是查询）。
     */
    @PostExchange("/video/task/query")
    R<AiVideoTaskQueryResponse> queryVideoTask(@RequestBody AiVideoTaskQueryRequest request);

    /**
     * 用已配置的 TTS 模型合成语音音频。
     *
     * <p>非幂等、计费。
     */
    @PostExchange("/tts/synthesize")
    R<AiTtsGenerateResponse> generateTts(@RequestBody AiTtsGenerateRequest request);
}
