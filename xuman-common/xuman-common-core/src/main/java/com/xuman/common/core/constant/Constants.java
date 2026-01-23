package com.xuman.common.core.constant;

/**
 * 通用常量
 */
public interface Constants {

    /** 成功标记 */
    int SUCCESS = 200;

    /** 失败标记 */
    int FAIL = 500;

    /** 未认证 */
    int UNAUTHORIZED = 401;

    /** 无权限 */
    int FORBIDDEN = 403;

    /** UTF-8 字符集 */
    String UTF8 = "UTF-8";

    /** 登录用户 Key */
    String LOGIN_USER_KEY = "login_user";

    /** 令牌前缀 */
    String TOKEN_PREFIX = "Bearer ";

    /** 令牌头 */
    String AUTHORIZATION_HEADER = "Authorization";

    /** 租户ID头 */
    String TENANT_ID_HEADER = "X-Tenant-Id";

    /** 客户端类型头 */
    String CLIENT_TYPE_HEADER = "X-Client-Type";

    /** 设备ID头 */
    String DEVICE_ID_HEADER = "X-Device-Id";

    /** 是否为管理员 */
    Long ADMIN_ID = 1L;

    /** 超级管理员角色Key */
    String SUPER_ADMIN_ROLE = "admin";

    /** 删除标志：正常 */
    int DEL_FLAG_NORMAL = 0;

    /** 删除标志：删除 */
    int DEL_FLAG_DELETED = 1;

    /** 状态：启用 */
    int STATUS_ENABLE = 0;

    /** 状态：禁用 */
    int STATUS_DISABLE = 1;
}
