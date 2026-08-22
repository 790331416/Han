package com.han.open.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端授权申请视图。 */
@Data
public class OpenAuthorizationRequestAdminVO {

    /** 兼容旧前端字段。 */
    private Long id;

    private Long requestId;
    private Long appId;
    private Long grantId;
    private String environment;
    private Integer requestType;
    private Integer status;
    private String requestData;
    private String reason;
    private String reviewReason;
    private Long applicantId;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
