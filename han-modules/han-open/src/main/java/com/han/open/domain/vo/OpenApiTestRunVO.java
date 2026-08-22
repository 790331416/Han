package com.han.open.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 在线调测审计展示对象，不包含任何请求或响应敏感内容。 */
@Data
public class OpenApiTestRunVO {

    private Long id;
    private Long appId;
    private Long resourceId;
    private String environment;
    private String requestMethod;
    private String requestPath;
    private Integer statusCode;
    private String result;
    private String traceId;
    private Integer durationMs;
    private Long responseSize;
    private LocalDateTime createTime;
}
