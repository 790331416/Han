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

    private Long modelId;

    private String text;

    private String voiceType;

    private BigDecimal speedRatio;

    private BigDecimal volumeRatio;

    private BigDecimal pitchRatio;

    private String requestId;
}
