package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 租户初始化参数（创建租户时由 han-tenant 调用 han-system 初始化基础数据）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInitDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 租户名称（用作默认部门名称） */
    private String tenantName;

    /** 管理员用户名 */
    private String adminUsername;

    /** 管理员密码（明文，由 han-system 加密存储） */
    private String adminPassword;

    /** 管理员昵称 */
    private String adminNickname;

    /** 管理员手机号 */
    private String adminPhone;

    /** 套餐菜单ID列表（可选，用于初始化角色菜单） */
    private java.util.Set<Long> menuIds;
}
