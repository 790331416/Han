package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Video generation task query request.
 */
@Data
public class AiVideoTaskQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long tenantId;

    private Long modelId;

    private String providerTaskId;
}
