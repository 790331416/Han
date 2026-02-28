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
}
