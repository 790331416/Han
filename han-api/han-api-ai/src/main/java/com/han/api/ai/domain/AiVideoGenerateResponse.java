package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Video generation response from internal AI service.
 */
@Data
public class AiVideoGenerateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String prompt;

    private String providerTaskId;

    private String taskStatus;

    private Integer progress;

    private String videoUrl;

    private String lastFrameUrl;

    private String rawResponse;
}
