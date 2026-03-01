package com.han.system.domain.query;

import lombok.Data;

/**
 * 角色查询对象
 */
@Data
public class SysRoleQuery {

    /** 角色名称（模糊匹配） */
    private String roleName;

    /** 角色权限字符串（模糊匹配） */
    private String roleKey;

    /** 状态 */
    private Integer status;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
