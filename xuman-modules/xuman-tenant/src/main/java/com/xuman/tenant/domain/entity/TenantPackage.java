package com.xuman.tenant.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xuman.common.mybatis.domain.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户套餐实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_package")
public class TenantPackage extends BaseEntity {

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 关联菜单ID(JSON数组)
     */
    private String menuIds;

    /**
     * 状态(0正常 1停用)
     */
    private Integer status;
}
