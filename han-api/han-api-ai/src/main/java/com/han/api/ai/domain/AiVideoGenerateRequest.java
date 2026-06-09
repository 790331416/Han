package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Video generation request for internal AI service.
 */
@Data
public class AiVideoGenerateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;

    private Long modelId;

    private Long promptTemplateId;

    private String userPrompt;

    private String customPrompt;

    private Map<String, String> variables;

    private Integer candidateCount;

    private String ratio;

    private String resolution;

    private Integer durationSec;

    private String referenceImageUrl;

    private List<String> referenceImageUrls;

    private Boolean referenceImageAsFirstFrame;

    private String referenceVideoUrl;

    private String referenceAudioUrl;

    private List<String> referenceAudioUrls;

    private Boolean returnLastFrame;

    private Boolean generateAudio;
}
