package com.han.system.domain.query;

import lombok.Data;

/**
 * 部门查询对象
 */
@Data
public class SysDeptQuery {

    /** 部门名称（模糊匹配） */
    private String deptName;

    /** 状态 */
    private Integer status;
}
