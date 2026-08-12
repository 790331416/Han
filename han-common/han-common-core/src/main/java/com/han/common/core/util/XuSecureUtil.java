package com.han.common.core.util;

/**
 * 安全工具类
 *
 * @deprecated 与 {@link HanSecureUtil} 重复且已落后（缺 RSA 能力），现已全部委托给 {@code HanSecureUtil}。
 * 新代码请直接使用 {@link HanSecureUtil}；密码相关能力请使用 {@link PasswordUtil}。
 */
@Deprecated(since = "1.0.0")
public final class XuSecureUtil {

    private XuSecureUtil() {}

    /**
     * MD5加密
     */
    public static String md5(String str) {
        return HanSecureUtil.md5(str);
    }

    /**
     * SHA256加密
     */
    public static String sha256(String str) {
        return HanSecureUtil.sha256(str);
    }

    /**
     * Base64编码
     */
    public static String base64Encode(String str) {
        return HanSecureUtil.base64Encode(str);
    }

    /**
     * Base64解码
     */
    public static String base64Decode(String str) {
        return HanSecureUtil.base64Decode(str);
    }

    /**
     * 生成随机密码
     *
     * @deprecated 请改用 {@link PasswordUtil#generatePassword(int)}，它保证各类字符齐全。
     */
    @Deprecated(since = "1.0.0")
    public static String generatePassword(int length) {
        return HanSecureUtil.generatePassword(length);
    }

    /**
     * 验证密码强度
     *
     * @deprecated 密码策略唯一入口是 {@link PasswordUtil#validate(String)}。
     */
    @Deprecated(since = "1.0.0")
    public static boolean isStrongPassword(String password) {
        return HanSecureUtil.isStrongPassword(password);
    }
}
