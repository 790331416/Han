package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Scene image generation request.
 */
@Data
public class AivideoSceneImageGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "场景ID不能为空")
    private Long sceneId;

    private Integer candidateCount;

    private Long modelId;

    private String ratio;

    private String resolution;

    private String size;

    private String referenceImageUrl;

    private String customPrompt;
}
