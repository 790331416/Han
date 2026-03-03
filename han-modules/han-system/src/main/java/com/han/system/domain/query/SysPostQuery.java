package com.han.system.domain.query;

import lombok.Data;

/**
 * 岗位查询对象
 */
@Data
public class SysPostQuery {

    /** 岗位编码（模糊匹配） */
    private String postCode;

    /** 岗位名称（模糊匹配） */
    private String postName;

    /** 状态 */
    private Integer status;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
