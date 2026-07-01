package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Text generation request.
 */
@Data
public class AiTextGenerateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;

    private Long modelId;

    private Long promptTemplateId;

    private String systemPrompt;

    private String userPrompt;

    private String customPrompt;

    private Map<String, String> variables;

    private Integer maxTokens;
}
