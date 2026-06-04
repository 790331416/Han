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

    private Long characterImagePromptTemplateId;

    private Long sceneImagePromptTemplateId;

    private Long shotPromptTemplateId;

    private Long videoPromptTemplateId;

    private String defaultRatio;

    private String defaultResolution;

    private Integer imageCandidateCount;

    private Integer videoCandidateCount;

    private Integer defaultShotDuration;

    private String previewMode;

    private String contentAuditEnabled;

    private String mediaAccessPolicy;

    private String defaultStyle;

    private String generationStrategy;

    private String audioMode;

    private String subtitleMode;

    private String referenceStrategy;

    private String actionIntensity;

    private String continuityLevel;

    private String multiRoleStrategy;

    private String characterDesignType;

    private String globalPrompt;

    private String polishPrompt;

    private String scriptPrompt;

    private String assetPrompt;

    private String characterImagePrompt;

    private String sceneImagePrompt;

    private String shotVideoPrompt;

    private String remark;
}
