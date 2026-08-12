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
