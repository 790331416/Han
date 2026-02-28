package com.han.api.system.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色信息VO
 */
@Data
public class RoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色ID */
    private Long roleId;

    /** 租户ID */
    private Long tenantId;

    /** 角色名称 */
    private String roleName;

    /** 角色标识 */
    private String roleKey;

    /** 数据权限范围 */
    private String dataScope;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;
}
