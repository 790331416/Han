package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线调测审计记录。
 *
 * <p>只保存请求目录定位信息和脱敏后的结果摘要，不保存请求体、响应体、请求头或凭证。</p>
 */
@Data
@TableName("open_api_test_run")
public class OpenApiTestRunPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long vendorId;
    private Long appId;
    private Long resourceId;
    private String environment;
    private String requestMethod;
    private String requestPath;
    private Integer statusCode;
    private String result;
    private String traceId;
    private Integer durationMs;
    private String redactedSummary;
    private LocalDateTime createTime;
}
