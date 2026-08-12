package com.han.api.ai.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Image generation request for internal AI service.
 */
@Data
public class AiImageGenerateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;

    /** 模型ID。为空时服务端按租户可见范围挑选默认图片模型。 */
    private Long modelId;

    private Long promptTemplateId;

    private String userPrompt;

    private String customPrompt;

    private Map<String, String> variables;

    /**
     * 出图张数。
     *
     * <p>服务端 {@code AiOpenAiCompatibleClient#buildImageRequest} 实际按
     * {@code min(max(n, 1), 4)} 归一，这里把既有上限显式写进契约，避免调用方误以为无上界。
     * 服务端 I 层启用 {@code @Validated} 后，超限请求会被 400 明确拒绝，
     * 而不是像现在这样静默截断到 4。
     */
    @Min(1)
    @Max(4)
    private Integer candidateCount;

    private String ratio;

    private String resolution;

    private String size;

    private List<String> referenceImageUrls;
}
