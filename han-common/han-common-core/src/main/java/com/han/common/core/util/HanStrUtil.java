package com.han.common.core.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 字符串工具类
 */
public final class HanStrUtil {

    private HanStrUtil() {}

    /**
     * 判断字符串是否为空
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 空字符串转null
     */
    public static String nullIfEmpty(String str) {
        return isEmpty(str) ? null : str;
    }

    /**
     * null转空字符串
     */
    public static String emptyIfNull(String str) {
        return str == null ? "" : str;
    }

    /**
     * 去除前后空格
     */
    public static String trim(String str) {
        return str == null ? null : str.trim();
    }

    /**
     * 生成UUID字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 转换为UTF-8字节数组
     */
    public static byte[] getBytesUtf8(String str) {
        return str == null ? null : str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 从UTF-8字节数组转字符串
     */
    public static String newStringUtf8(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 拼接字符串
     */
    public static String join(String... strings) {
        if (strings == null || strings.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String str : strings) {
            if (isNotEmpty(str)) {
                sb.append(str);
            }
        }
        return sb.toString();
    }

    /**
     * 分割字符串
     */
    public static String[] split(String str, String delimiter) {
        if (isBlank(str)) {
            return new String[0];
        }
        return str.split(delimiter);
    }

    /**
     * 替换字符串
     */
    public static String replace(String str, String oldStr, String newStr) {
        if (isBlank(str)) {
            return str;
        }
        return str.replace(oldStr, newStr);
    }

    /**
     * 大小写转换
     */
    public static String toUpperCase(String str) {
        return str == null ? null : str.toUpperCase();
    }

    public static String toLowerCase(String str) {
        return str == null ? null : str.toLowerCase();
    }

    /**
     * 判断是否相等
     */
    public static boolean equals(String str1, String str2) {
        if (str1 == str2) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equals(str2);
    }

    /**
     * 判断是否相等（忽略大小写）
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == str2) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }
}
