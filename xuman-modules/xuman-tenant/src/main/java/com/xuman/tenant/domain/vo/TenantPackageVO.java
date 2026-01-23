package com.xuman.tenant.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 租户套餐VO
 */
@Data
public class TenantPackageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 套餐ID
     */
    private Long packageId;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 菜单ID列表
     */
    private Set<Long> menuIds;

    /**
     * 关联租户数
     */
    private Integer tenantCount;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 备注
     */
    private String remark;
}
