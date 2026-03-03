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
 * 登录日志持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_login_log")
public class SysLoginLogPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private Long tenantId;

    private String ipAddr;

    private String loginLocation;

    /** 0成功 1失败 */
    private Integer status;

    private String message;

    private String clientType;

    private String browser;

    private String os;

    private LocalDateTime loginTime;
}
