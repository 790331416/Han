package com.han.common.core.constant;

/**
 * 缓存Key常量
 */
public interface CacheConstants {

    /** 缓存前缀 */
    String CACHE_PREFIX = "han:";

    /** Token缓存 */
    String TOKEN_KEY = CACHE_PREFIX + "token:";

    /** 刷新Token缓存 */
    String REFRESH_TOKEN_KEY = CACHE_PREFIX + "refresh:";

    /** 用户在线设备 */
    String ONLINE_KEY = CACHE_PREFIX + "online:";

    /** 设备Token映射 */
    String DEVICE_KEY = CACHE_PREFIX + "device:";

    /** 登录用户信息 */
    String LOGIN_USER_KEY = CACHE_PREFIX + "login_user:";

    /** 验证码 */
    String CAPTCHA_KEY = CACHE_PREFIX + "captcha:";

    /** 防重复提交 */
    String REPEAT_SUBMIT_KEY = CACHE_PREFIX + "repeat:";

    /** 限流 */
    String RATE_LIMIT_KEY = CACHE_PREFIX + "rate_limit:";

    /** 字典缓存 */
    String DICT_KEY = CACHE_PREFIX + "dict:";

    /** 参数缓存 */
    String CONFIG_KEY = CACHE_PREFIX + "config:";

    /** 租户缓存 */
    String TENANT_KEY = CACHE_PREFIX + "tenant:";

    /** 用户权限缓存 */
    String USER_PERMISSION_KEY = CACHE_PREFIX + "user_permission:";

    /** 社交登录 OAuth state（防 CSRF，一次性） */
    String SOCIAL_STATE_KEY = CACHE_PREFIX + "social_state:";

    /** 社交登录绑定/选租户临时凭证（一次性） */
    String SOCIAL_TICKET_KEY = CACHE_PREFIX + "social_ticket:";

    /**
     * 会话索引：账号下全部 accessToken（Redis Set）。
     * <p>登录/刷新时加入，登出/身份撤销/账号撤销时移除；撤销全部会话不再只依赖
     * {@code login_user:{userId}:{clientType}} 读最后一枚 token。
     */
    String SESSION_USER_KEY = CACHE_PREFIX + "auth:sessions:user:";

    /**
     * 会话索引：账号 + 身份下全部 accessToken（Redis Set）。
     * <p>仅 identityScoped 登录态加入，身份粒度撤销时据此删除该身份全部客户端会话。
     */
    String SESSION_IDENTITY_KEY = CACHE_PREFIX + "auth:sessions:identity:";

    /**
     * 身份索引：账号下全部 identityId（Redis Set）。
     * <p>账号粒度撤销时据此逐个删除身份会话 Set 并撤销身份课堂凭证。
     */
    String IDENTITIES_USER_KEY = CACHE_PREFIX + "auth:identities:user:";
}
