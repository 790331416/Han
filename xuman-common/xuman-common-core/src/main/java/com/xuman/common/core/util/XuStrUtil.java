package com.xuman.common.core.util;

import cn.hutool.core.util.StrUtil;

/**
 * 字符串工具类（封装Hutool，便于后续扩展或替换）
 */
public final class XuStrUtil {

    private XuStrUtil() {}

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(CharSequence str) {
        return StrUtil.isEmpty(str);
    }

    /**
     * 判断字符串是否为空白
     */
    public static boolean isBlank(CharSequence str) {
        return StrUtil.isBlank(str);
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotEmpty(CharSequence str) {
        return StrUtil.isNotEmpty(str);
    }

    /**
     * 判断字符串是否非空白
     */
    public static boolean isNotBlank(CharSequence str) {
        return StrUtil.isNotBlank(str);
    }

    /**
     * 格式化字符串（使用{}占位符）
     */
    public static String format(CharSequence template, Object... params) {
        return StrUtil.format(template, params);
    }

    /**
     * 脱敏处理
     */
    public static String hide(CharSequence str, int startInclude, int endExclude) {
        return StrUtil.hide(str, startInclude, endExclude);
    }

    /**
     * 下划线转驼峰
     */
    public static String toCamelCase(CharSequence str) {
        return StrUtil.toCamelCase(str);
    }

    /**
     * 驼峰转下划线
     */
    public static String toUnderlineCase(CharSequence str) {
        return StrUtil.toUnderlineCase(str);
    }

    /**
     * 去除首尾空白
     */
    public static String trim(CharSequence str) {
        return StrUtil.trim(str);
    }

    /**
     * 截取字符串
     */
    public static String sub(CharSequence str, int fromIndex, int toIndex) {
        return StrUtil.sub(str, fromIndex, toIndex);
    }

    /**
     * 安全截取字符串（防止越界）
     */
    public static String safeSub(String str, int start, int end) {
        if (isEmpty(str)) {
            return str;
        }
        int length = str.length();
        if (start < 0) start = 0;
        if (end > length) end = length;
        if (start >= end) return "";
        return str.substring(start, end);
    }

    /**
     * 首字母大写
     */
    public static String upperFirst(CharSequence str) {
        return StrUtil.upperFirst(str);
    }

    /**
     * 首字母小写
     */
    public static String lowerFirst(CharSequence str) {
        return StrUtil.lowerFirst(str);
    }

    /**
     * 判断是否包含
     */
    public static boolean contains(CharSequence str, CharSequence searchStr) {
        return StrUtil.contains(str, searchStr);
    }

    /**
     * 判断是否以指定字符串开头
     */
    public static boolean startWith(CharSequence str, CharSequence prefix) {
        return StrUtil.startWith(str, prefix);
    }

    /**
     * 判断是否以指定字符串结尾
     */
    public static boolean endWith(CharSequence str, CharSequence suffix) {
        return StrUtil.endWith(str, suffix);
    }

    /**
     * 分割字符串
     */
    public static String[] split(CharSequence str, CharSequence separator) {
        return StrUtil.splitToArray(str, separator);
    }

    /**
     * 重复字符串
     */
    public static String repeat(CharSequence str, int count) {
        return StrUtil.repeat(str, count);
    }

    /**
     * 判断是否相等（忽略大小写）
     */
    public static boolean equalsIgnoreCase(CharSequence str1, CharSequence str2) {
        return StrUtil.equalsIgnoreCase(str1, str2);
    }

    /**
     * 移除前缀
     */
    public static String removePrefix(CharSequence str, CharSequence prefix) {
        return StrUtil.removePrefix(str, prefix);
    }

    /**
     * 移除后缀
     */
    public static String removeSuffix(CharSequence str, CharSequence suffix) {
        return StrUtil.removeSuffix(str, suffix);
    }
}
