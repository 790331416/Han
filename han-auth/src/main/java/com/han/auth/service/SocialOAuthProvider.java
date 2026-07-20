package com.han.auth.service;

import com.han.auth.domain.SocialUser;

/**
 * 社交登录 OAuth 提供方抽象
 *
 * <p>新增提供方（微信/GitHub/…）实现本接口并注册为 Spring Bean，
 * 由 {@link com.han.auth.service.SocialLoginService} 统一编排 state 防 CSRF、
 * 绑定票据与 token 签发，提供方只负责各自的授权 URL 与用户信息获取。
 */
public interface SocialOAuthProvider {

    /** 提供方标识（sys_user_social.provider 取值） */
    String provider();

    /** 凭据是否已配置（未配置时登录入口隐藏、接口拒绝） */
    boolean isConfigured();

    /** 构造第三方授权页 URL（state 由编排层生成并校验） */
    String buildAuthorizeUrl(String redirectUri, String state);

    /** 用授权码换取第三方用户信息 */
    SocialUser fetchUser(String code);
}
