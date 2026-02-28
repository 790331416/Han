package com.han.common.mybatis.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户实体 - 支持多租户的持久化实体
 * <p>
 * 如需纯 POJO，请使用 han-common-core 中的 TenantModel
 * 
 * @see com.han.common.core.domain.model.TenantModel
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
}
