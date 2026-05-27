package com.han.aivideo.domain.vo;

import lombok.Data;

/**
 * AI short-drama admin setting VO.
 */
@Data
public class AivideoAdminSettingVo {

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

    private String remark;
}
