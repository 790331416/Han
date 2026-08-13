package com.han.api.ai.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * AI 内部服务的视频生成请求。
 */
@Data
public class AiVideoGenerateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调用方自报的租户ID。
     *
     * @deprecated 服务端必须以内部签名覆盖的 {@code X-Tenant-Id} 请求头为准；本字段只在头透传
     *         能力就位前作为回退，服务端无法校验其真伪。两者都取不到时必须 fail-close，
     *         只放行平台级模型。完整约定见 {@link com.han.api.ai.AiServiceClient}。
     */
    @Deprecated
    private Long tenantId;

    /** 模型ID。为空时服务端按租户可见范围挑选默认视频模型。 */
    private Long modelId;

    private Long promptTemplateId;

    private String userPrompt;

    private String customPrompt;

    private Map<String, String> variables;

    private Integer candidateCount;

    private String ratio;

    private String resolution;

    /**
     * 视频时长（秒）。
     *
     * <p>服务端 {@code AiOpenAiCompatibleClient#normalizeVideoDuration} 的实际归一规则是：
     * 空或小于 1 取 5，否则取 {@code min(max(d, 4), 15)}。这里把既有上限写进契约。
     * 服务端 I 层启用 {@code @Validated} 后超限会被 400 拒绝，而不是静默截断。
     */
    @Min(1)
    @Max(15)
    private Integer durationSec;

    /**
     * 单张参考图地址。
     *
     * @deprecated 已由 {@link #referenceImageUrls} 取代。服务端
     *         {@code AiVideoGenerationServiceImpl#resolveReferenceImageUrls} 的实际语义是
     *         <b>合并去重</b>：先按顺序取复数字段的元素，再把本字段追加到末尾，重复项丢弃。
     *         两者都填不会报错，但顺序由复数字段决定。新调用方一律只用复数字段。
     */
    @Deprecated
    private String referenceImageUrl;

    private List<String> referenceImageUrls;

    private Boolean referenceImageAsFirstFrame;

    private String referenceVideoUrl;

    /**
     * 单段参考音频地址。
     *
     * @deprecated 已由 {@link #referenceAudioUrls} 取代，合并规则同
     *         {@link #referenceImageUrl}：复数字段在前，本字段追加在后，重复项丢弃。
     */
    @Deprecated
    private String referenceAudioUrl;

    private List<String> referenceAudioUrls;

    private Boolean returnLastFrame;

    private Boolean generateAudio;
}
