package com.han.api.tenant.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户信息VO
 */
@Data
public class TenantVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 租户名称 */
    private String tenantName;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 套餐ID */
    private Long packageId;

    /** 用户数量限制（-1不限制） */
    private Integer userLimit;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 状态 */
    private Integer status;

    /** 数据隔离类型（logical/physical/hybrid） */
    private String isolationType;
}
