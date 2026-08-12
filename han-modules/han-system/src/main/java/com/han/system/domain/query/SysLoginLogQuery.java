package com.han.system.domain.query;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志查询对象
 *
 * <p>字段口径与 {@link SysOperLogQuery} 对齐：时间范围统一用 beginTime / endTime。
 */
@Data
public class SysLoginLogQuery {

    /** 用户名（模糊匹配） */
    private String username;

    /** 登录IP（模糊匹配） */
    private String ipAddr;

    /** 登录状态（0成功 1失败） */
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
