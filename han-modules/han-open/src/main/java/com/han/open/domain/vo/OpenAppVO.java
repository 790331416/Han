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
     * 授权回调地址列表
     */
    private List<String> redirectUris;

    /**
     * 授权范围列表
     */
    private List<String> scopes;

    /**
     * 授权类型列表
     */
    private List<String> grantTypes;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
