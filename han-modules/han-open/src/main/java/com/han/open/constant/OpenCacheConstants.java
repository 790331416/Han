package com.han.open.constant;

import com.han.common.core.constant.CacheConstants;

/**
 * 开放平台缓存 Key 常量。
 *
 * <p>OAuth2 授权码与令牌统一使用独立命名空间，与平台会话令牌 {@link CacheConstants#TOKEN_KEY} 隔离，
 * 避免第三方应用令牌被当作平台登录态直接放行。
 */
public interface OpenCacheConstants {

    /** OAuth2 命名空间前缀 */
    String OAUTH2_PREFIX = CacheConstants.CACHE_PREFIX + "oauth2:";

    /** 授权码 */
    String AUTH_CODE_KEY = OAUTH2_PREFIX + "code:";

    /** 访问令牌 */
    String ACCESS_TOKEN_KEY = OAUTH2_PREFIX + "access:";

    /** 刷新令牌 */
    String REFRESH_TOKEN_KEY = OAUTH2_PREFIX + "refresh:";

    /** 用户在某应用下已签发的访问令牌索引（用于按用户维度撤销） */
    String USER_TOKEN_INDEX_KEY = OAUTH2_PREFIX + "user_tokens:";

    /** SSO Ticket */
    String SSO_TICKET_KEY = CacheConstants.CACHE_PREFIX + "sso:ticket:";
}
