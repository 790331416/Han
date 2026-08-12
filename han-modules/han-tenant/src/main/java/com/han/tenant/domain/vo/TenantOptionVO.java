package com.han.tenant.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户下拉选项 VO。
 * <p>
 * 面向登录页等匿名场景，只暴露租户 ID 与名称，不含联系人姓名、手机号、邮箱等 PII，
 * 也不含套餐、用户数、过期时间等经营信息。新增字段前必须评估匿名暴露面。
 */
@Data
public class TenantOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 租户名称 */
    private String tenantName;
}
