package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 开放平台应用持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_app")
public class OpenAppPo extends BizEntity {

    /** 应用名称 */
    private String appName;

    /** 应用Key(Client ID) */
    private String appKey;

    /** 应用密钥(Client Secret) */
    private String appSecret;

    /** 应用图标 */
    private String appIcon;

    /** 应用描述 */
    private String appDesc;

    /** 应用类型(web/native/spa) */
    private String appType;

    /** 授权回调地址(多个用逗号分隔) */
    private String redirectUris;

    /** 登出回调地址 */
    private String logoutUri;

    /** 授权范围(多个用逗号分隔) */
    private String scopes;

    /**
     * 可读取教育目录的学校 ID，逗号分隔。
     *
     * <p>ponytail: 当前应用授权量小，先用单列收口；学校范围需要按单校独立审计或大量筛选时，
     * 升级为 open_app_school_scope 关联表。</p>
     */
    private String schoolScope;

    /** 授权类型(authorization_code,client_credentials,refresh_token) */
    private String grantTypes;

    /** AccessToken有效期(秒) */
    private Integer accessTokenTtl;

    /** RefreshToken有效期(秒) */
    private Integer refreshTokenTtl;

    /** 是否启用PKCE */
    private Integer requirePkce;

    /** 是否自动授权(跳过授权确认页) */
    private Integer autoApprove;

    /** 状态(0正常 1停用) */
    private Integer status;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 厂商ID */
    private Long vendorId;

    /** 生命周期状态：0草稿 1待审核 2沙箱已开通 3调测中 4生产待审核 5生产已开通 6暂停 7撤销 */
    private Integer lifecycleStatus;

    /** 环境策略：SANDBOX_FIRST仅沙箱、PROD_ONLY仅生产、ALL所有环境 */
    private String environmentPolicy;
}
