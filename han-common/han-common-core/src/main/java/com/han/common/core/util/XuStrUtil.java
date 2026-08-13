package com.han.common.core.util;

/**
 * 字符串工具类
 *
 * @deprecated 与 {@link HanStrUtil} 完全重复，已全部委托给 {@code HanStrUtil}。
 * 新代码请直接使用 {@link HanStrUtil}，存量调用点将在统一整改批次中迁移。
 */
@Deprecated(since = "1.0.0")
public final class XuStrUtil {

    private XuStrUtil() {}

    /**
     * 判断字符串是否为空
     */
    public static boolean isBlank(String str) {
        return HanStrUtil.isBlank(str);
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotBlank(String str) {
        return HanStrUtil.isNotBlank(str);
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return HanStrUtil.isEmpty(str);
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotEmpty(String str) {
        return HanStrUtil.isNotEmpty(str);
    }

    /**
     * 空字符串转null
     */
    public static String nullIfEmpty(String str) {
        return HanStrUtil.nullIfEmpty(str);
    }

    /**
     * null转空字符串
     */
    public static String emptyIfNull(String str) {
        return HanStrUtil.emptyIfNull(str);
    }

    /**
     * 去除前后空格
     */
    public static String trim(String str) {
        return HanStrUtil.trim(str);
    }

    /**
     * 生成UUID字符串
     */
    public static String uuid() {
        return HanStrUtil.uuid();
    }

    /**
     * 转换为UTF-8字节数组
     */
    public static byte[] getBytesUtf8(String str) {
        return HanStrUtil.getBytesUtf8(str);
    }

    /**
     * 从UTF-8字节数组转字符串
     */
    public static String newStringUtf8(byte[] bytes) {
        return HanStrUtil.newStringUtf8(bytes);
    }

    /**
     * 拼接字符串
     */
    public static String join(String... strings) {
        return HanStrUtil.join(strings);
    }

    /**
     * 分割字符串
     */
    public static String[] split(String str, String delimiter) {
        return HanStrUtil.split(str, delimiter);
    }

    /**
     * 替换字符串
     */
    public static String replace(String str, String oldStr, String newStr) {
        return HanStrUtil.replace(str, oldStr, newStr);
    }

    /**
     * 大小写转换
     */
    public static String toUpperCase(String str) {
        return HanStrUtil.toUpperCase(str);
    }

    public static String toLowerCase(String str) {
        return HanStrUtil.toLowerCase(str);
    }

    /**
     * 判断是否相等
     */
    public static boolean equals(String str1, String str2) {
        return HanStrUtil.equals(str1, str2);
    }

    /**
     * 判断是否相等（忽略大小写）
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        return HanStrUtil.equalsIgnoreCase(str1, str2);
    }
}
