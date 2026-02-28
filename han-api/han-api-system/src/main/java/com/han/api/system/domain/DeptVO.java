package com.han.api.system.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部门信息VO
 */
@Data
public class DeptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门ID */
    private Long deptId;

    /** 租户ID */
    private Long tenantId;

    /** 父部门ID */
    private Long parentId;

    /** 祖级列表 */
    private String ancestors;

    /** 部门名称 */
    private String deptName;

    /** 负责人 */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;
}
