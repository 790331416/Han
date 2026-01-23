package com.xuman.tenant.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户DTO
 */
@Data
public class TenantDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 租户ID(编辑时使用)
     */
    private Long tenantId;

    /**
     * 租户名称
     */
    @NotBlank(message = "租户名称不能为空")
    @Size(max = 100, message = "租户名称不能超过100个字符")
    private String tenantName;

    /**
     * 联系人
     */
    @Size(max = 50, message = "联系人不能超过50个字符")
    private String contactName;

    /**
     * 联系电话
     */
    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;

    /**
     * 联系邮箱
     */
    @Size(max = 100, message = "联系邮箱不能超过100个字符")
    private String contactEmail;

    /**
     * 套餐ID
     */
    private Long packageId;

    /**
     * 用户数量限制
     */
    private Integer userLimit;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 隔离类型
     */
    private String isolationType;

    /**
     * 绑定域名
     */
    private String domain;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 管理员用户名(新增租户时创建)
     */
    private String adminUsername;

    /**
     * 管理员密码(新增租户时创建)
     */
    private String adminPassword;
}
