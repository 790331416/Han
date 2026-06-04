package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * AI short-drama character image generation request.
 */
@Data
public class AivideoCharacterImageGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "角色ID不能为空")
    private Long characterId;

    private Integer candidateCount;

    private Long modelId;

    private String ratio;

    private String resolution;

    private String size;

    private String defaultStyle;

    private String characterDesignType;

    private String referenceImageUrl;

    private List<Long> referenceMediaIds;

    private List<String> referenceImageUrls;

    private String customPrompt;
}
