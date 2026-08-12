package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Image generation response from internal AI service.
 */
@Data
public class AiImageGenerateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String prompt;

    private List<AiImageCandidate> candidates;
}
