package com.han.open.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端厂商入驻申请视图。 */
@Data
public class OpenVendorApplicationAdminVO {

    /** 兼容旧前端字段。 */
    private Long id;

    private Long applicationId;
    private Long vendorId;
    private Long applicantUserId;
    private String applicationNo;
    private Integer status;
    private String applyData;
    private String reason;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
