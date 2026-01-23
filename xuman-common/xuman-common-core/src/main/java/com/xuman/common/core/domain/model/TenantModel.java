package com.xuman.common.core.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户模型 - 支持多租户的纯 POJO
 * <p>
 * 适用场景：需要租户隔离的 DTO、VO 等非持久化对象
 * <p>
 * 如需持久化，请使用 xuman-common-mybatis 中的 TenantEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantModel extends BaseModel {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;
}
