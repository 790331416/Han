package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志传输对象（han-auth → han-system）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogDTO {

    /** 用户名 */
    private String username;

    /** 租户ID */
    private Long tenantId;

    /** 登录IP */
    private String ipAddr;

    /** 登录地点 */
    private String loginLocation;

    /** 登录状态（0成功 1失败） */
    private Integer status;

    /** 提示消息 */
    private String message;

    /** 客户端类型 */
    private String clientType;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;
}
