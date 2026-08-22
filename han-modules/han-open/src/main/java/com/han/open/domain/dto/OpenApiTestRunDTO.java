package com.han.open.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 浏览器调测完成后提交的最小审计数据。目录方法和路径不由客户端提交。 */
@Data
public class OpenApiTestRunDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "应用ID不能为空")
    private Long appId;

    @NotNull(message = "资源ID不能为空")
    private Long resourceId;

    @NotBlank(message = "环境不能为空")
    @Pattern(regexp = "(?i)SANDBOX|PROD", message = "环境仅支持SANDBOX或PROD")
    private String environment;

    @NotNull(message = "响应状态码不能为空")
    @Min(value = 0, message = "响应状态码不合法")
    @Max(value = 599, message = "响应状态码不合法")
    private Integer statusCode;

    @NotNull(message = "请求耗时不能为空")
    @Min(value = 0, message = "请求耗时不合法")
    @Max(value = 600000, message = "请求耗时过大")
    private Integer durationMs;

    @NotNull(message = "响应大小不能为空")
    @Min(value = 0, message = "响应大小不合法")
    @Max(value = 50000000, message = "响应大小过大")
    private Long responseSize;

    /** 兼容旧客户端字段，但审计结果始终由服务端按 statusCode 派生。 */
    @JsonIgnore
    private String result;

    /** 仅接受链路追踪 ID 的安全字符，非法或空值由服务端生成。 */
    @Size(max = 64, message = "链路ID过长")
    @Pattern(regexp = "[A-Za-z0-9._:-]*", message = "链路ID格式不合法")
    private String traceId;
}
