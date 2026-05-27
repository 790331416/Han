package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Video generation task query response.
 */
@Data
public class AiVideoTaskQueryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String provider;

    private String modelCode;

    private String providerTaskId;

    private String taskStatus;

    private Integer progress;

    private String videoUrl;

    private String rawResponse;
}
