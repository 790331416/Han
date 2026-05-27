package com.han.aivideo.domain.dto;

import lombok.Data;

/**
 * AI short-drama admin setting DTO.
 */
@Data
public class AivideoAdminSettingDto {

    private Long textModelId;

    private Long imageModelId;

    private Long videoModelId;

    private Long polishPromptTemplateId;

    private Long scriptPromptTemplateId;

    private Long characterPromptTemplateId;

    private Long scenePromptTemplateId;

    private Long sceneImagePromptTemplateId;

    private Long shotPromptTemplateId;

    private String defaultRatio;

    private String defaultResolution;

    private Integer imageCandidateCount;

    private Integer videoCandidateCount;

    private Integer defaultShotDuration;

    private String previewMode;

    private String contentAuditEnabled;

    private String mediaAccessPolicy;

    private String remark;
}
