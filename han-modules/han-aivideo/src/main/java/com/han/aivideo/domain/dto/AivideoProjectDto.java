package com.han.aivideo.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * AI short-drama project write DTO.
 */
@Data
public class AivideoProjectDto {

    private Long projectId;

    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    private String topicType;

    private String targetPlatform;

    private String defaultRatio;

    private String defaultStyle;

    private Integer defaultShotDuration;

    private Integer candidateImageCount;

    private String previewMode;

    private BigDecimal budgetLimit;

    private String summary;

    private String sourceType;

    private Long fileId;

    private String fileName;

    private String rawText;
}
