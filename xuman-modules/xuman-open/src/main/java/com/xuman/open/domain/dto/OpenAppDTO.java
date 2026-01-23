package com.xuman.open.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 开放平台应用DTO
 */
@Data
public class OpenAppDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用ID(编辑时使用)
     */
    private Long appId;

    /**
     * 应用名称
     */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称不能超过100个字符")
    private String appName;

    /**
     * 应用图标
     */
    private String appIcon;

    /**
     * 应用描述
     */
    @Size(max = 500, message = "应用描述不能超过500个字符")
    private String appDesc;

    /**
     * 应用类型(web/native/spa)
     */
    @NotBlank(message = "应用类型不能为空")
    private String appType;

    /**
     * 授权回调地址列表
     */
    private List<String> redirectUris;

    /**
     * 登出回调地址
     */
    private String logoutUri;

    /**
     * 授权范围列表
     */
    private List<String> scopes;

    /**
     * 授权类型列表
     */
    private List<String> grantTypes;

    /**
     * AccessToken有效期(秒)
     */
    private Integer accessTokenTtl;

    /**
     * RefreshToken有效期(秒)
     */
    private Integer refreshTokenTtl;

    /**
     * 是否启用PKCE
     */
    private Boolean requirePkce;

    /**
     * 是否自动授权
     */
    private Boolean autoApprove;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;
}
