package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Single shot video generation request.
 */
@Data
public class AivideoShotVideoGenerateDto {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "分镜ID不能为空")
    private Long shotId;

    private Integer candidateCount;

    private Long modelId;

    private String ratio;

    private String resolution;

    private Integer durationSec;

    private String customPrompt;

    private String generationStrategy;

    private String audioMode;

    private String subtitleMode;

    private String referenceStrategy;

    private String actionIntensity;

    private String continuityLevel;

    private String multiRoleStrategy;

    /**
     * Only recover/poll an existing provider task. Used by the workbench refresh action.
     */
    private Boolean recoverOnly;
}
