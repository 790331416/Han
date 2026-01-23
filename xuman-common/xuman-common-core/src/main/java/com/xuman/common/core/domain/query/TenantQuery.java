package com.xuman.common.core.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户查询对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID（自动注入，无需手动传） */
    private Long tenantId;
}
