package com.han.common.core.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 业务模型 - 标准业务对象的纯 POJO（含创建人、更新人等审计字段）
 * <p>
 * 适用场景：需要审计字段的 DTO、VO 等非持久化对象
 * <p>
 * 如需持久化，请使用 han-common-mybatis 中的 BizEntity
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BizModel extends TenantModel {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建者ID */
    private Long createBy;

    /** 创建者名称 */
    private String createName;

    /** 更新者ID */
    private Long updateBy;

    /** 更新者名称 */
    private String updateName;

    /** 创建部门ID */
    private Long createDept;

    /** 备注 */
    private String remark;
}
