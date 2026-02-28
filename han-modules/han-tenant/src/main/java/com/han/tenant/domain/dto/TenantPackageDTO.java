package com.han.tenant.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Set;

/**
 * 租户套餐DTO
 */
@Data
public class TenantPackageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 套餐ID
     */
    private Long packageId;

    /**
     * 套餐名称
     */
    @NotBlank(message = "套餐名称不能为空")
    private String packageName;

    /**
     * 菜单ID列表
     */
    private Set<Long> menuIds;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
