package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Text-to-speech generation request.
 */
@Data
public class AiTtsGenerateRequest implements Serializable {

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

    /** 模型ID。为空时服务端按租户可见范围挑选默认 TTS 模型。 */
    private Long modelId;

    /**
     * 待合成文本。
     *
     * <p>契约层未设长度上界：合理的上限与计费和厂商配额相关，按仓库规范应落到配置或字典，
     * 而 {@code @Size} 只接受编译期常量。服务端必须自行按配置项校验并在超限时明确拒绝，
     * 不要依赖调用方自觉。
     */
    private String text;

    private String voiceType;

    private BigDecimal speedRatio;

    private BigDecimal volumeRatio;

    private BigDecimal pitchRatio;

    private String requestId;
}
