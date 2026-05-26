package com.han.aivideo.domain.vo;

import lombok.Data;

/**
 * Prompt preview rendered by backend.
 */
@Data
public class AivideoPromptPreviewVo {

    private Long promptTemplateId;

    private String systemPrompt;

    private String userPrompt;

    private String customPrompt;

    private String effectivePrompt;
}
