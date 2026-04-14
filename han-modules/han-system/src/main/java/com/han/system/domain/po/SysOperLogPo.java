package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_oper_log")
public class SysOperLogPo {

    /** 日志ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 模块名称 */
    private String module;

    /** 操作类型 */
    private Integer operType;

    /** 操作人员 */
    private String operName;

    /** 操作人ID */
    private Long operUserId;

    /** 部门名称 */
    private String deptName;

    /** 请求URL */
    private String operUrl;

    /** 操作IP */
    private String operIp;

    /** 操作归属地 */
    private String operLocation;

    /** 请求方式 */
    private String requestMethod;

    /** 请求参数 */
    private String operParam;

    /** 返回结果 */
    private String jsonResult;

    /** 状态 0成功 1失败 */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 耗时(ms) */
    private long costTime;

    /** 操作时间 */
    private LocalDateTime operTime;
}
