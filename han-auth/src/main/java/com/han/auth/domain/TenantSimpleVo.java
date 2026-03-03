package com.han.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户简要信息（用于租户切换列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSimpleVo {

    /** 租户ID */
    private Long tenantId;

    /** 租户名称 */
    private String tenantName;

    /** 状态 0正常 1停用 */
    private Integer status;

    /** 是否当前租户 */
    private boolean current;
}
