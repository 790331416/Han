package com.xuman.common.mybatis.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 业务实体 - 标准业务持久化实体（含创建人、更新人等审计字段）
 * <p>
 * 如需纯 POJO，请使用 xuman-common-core 中的 BizModel
 * 
 * @see com.xuman.common.core.domain.model.BizModel
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BizEntity extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建者ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建者名称 */
    @TableField(fill = FieldFill.INSERT)
    private String createName;

    /** 更新者ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 更新者名称 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateName;

    /** 创建部门ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createDept;

    /** 备注 */
    private String remark;
}
