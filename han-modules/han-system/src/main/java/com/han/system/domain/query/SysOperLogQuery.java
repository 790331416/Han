package com.han.system.domain.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志查询对象
 */
@Data
public class SysOperLogQuery {

    /** 模块名称（模糊匹配） */
    private String module;

    /** 操作类型 */
    private Integer operType;

    /** 操作人员（模糊匹配） */
    private String operName;

    /** 状态 */
    private Integer status;

    /** 开始时间 */
    private LocalDateTime beginTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
