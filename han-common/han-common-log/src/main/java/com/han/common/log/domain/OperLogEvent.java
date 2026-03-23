package com.han.common.log.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志事件（跨模块传输对象）
 * <p>
 * 由 OperLogAspect 构建，通过 IOperLogService 传递给 han-system 入库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperLogEvent {

    /** 租户ID */
    private Long tenantId;

    /** 模块标题 */
    private String module;

    /** 操作类型（0=其他, 1=新增, 2=修改, 3=删除...） */
    private Integer operType;

    /** 操作人员 */
    private String operName;

    /** 操作人员ID */
    private Long operUserId;

    /** 部门名称 */
    private String deptName;

    /** 请求URL */
    private String operUrl;

    /** 操作地址 */
    private String operIp;

    /** 操作归属地 */
    private String operLocation;

    /** 请求方式 */
    private String requestMethod;

    /** 请求参数 */
    private String operParam;

    /** 返回结果 */
    private String jsonResult;

    /** 操作状态（0正常 1异常） */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private long costTime;

    /** 操作时间 */
    private LocalDateTime operTime;
}
