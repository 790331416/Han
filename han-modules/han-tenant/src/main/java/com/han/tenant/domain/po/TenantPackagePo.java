package com.han.tenant.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户套餐持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_package")
public class TenantPackagePo extends BaseEntity {

    /** 套餐名称 */
    private String packageName;

    /** 关联菜单ID(JSON数组) */
    private String menuIds;

    /** 状态(0正常 1停用) */
    private Integer status;
}
