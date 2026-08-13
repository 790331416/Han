package com.han.api.ai.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文本生成响应。
 */
@Data
public class AiTextGenerateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String content;

    private Long modelId;

    private String provider;

    private String modelCode;

    private Integer tokenCount;
}
