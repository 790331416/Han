package com.han.open.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开放平台应用VO
 */
@Data
public class OpenAppVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用Key(Client ID)
     */
    private String appKey;

    /** 应用所属租户，仅用于授权链路内部恢复数据范围。 */
    private Long tenantId;

    /**
     * 应用图标
     */
    private String appIcon;

    /**
     * 应用描述
     */
    private String appDesc;

    /**
     * 应用类型
     */
    private String appType;

    /**
     * 登出回调地址
     */
    private String logoutUri;

    /**
     * 授权回调地址列表
     */
    private List<String> redirectUris;

    /**
     * 授权范围列表
     */
    private List<String> scopes;

    /** 开放目录可读取的学校 ID 列表。 */
    private List<Long> schoolIds;

    /**
     * 授权类型列表
     */
    private List<String> grantTypes;

    /**
     * AccessToken 有效期（秒）
     */
    private Integer accessTokenTtl;

    /**
     * RefreshToken 有效期（秒）
     */
    private Integer refreshTokenTtl;

    /**
     * 是否启用 PKCE
     */
    private Integer requirePkce;

    /**
     * 是否自动授权
     */
    private Integer autoApprove;

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

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /** 用于令牌失效版本判断；任意应用授权变更都会使旧 Token 失效。 */
    private LocalDateTime updateTime;
}
