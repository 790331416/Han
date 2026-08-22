package com.han.open.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 开放接口资源版本VO
 */
@Data
public class OpenApiResourceVersionVO {

    private Long id;
    private Long resourceId;
    private String version;
    private Map<String, Object> openapiSchema;
    private Map<String, Object> requestExample;
    private Map<String, Object> responseExamples;
    private Map<String, Object> errorExamples;
    private Map<String, Object> authConfig;
    private Map<String, Object> sandboxConfig;
    private Integer status;
    private LocalDateTime publishedAt;
    private LocalDateTime deprecatedAt;
}
