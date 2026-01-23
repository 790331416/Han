package com.xuman.common.core.util;

import java.util.regex.Pattern;

/**
 * XSS防护工具类
 */
public final class XssUtil {

    private XssUtil() {}

    /** XSS危险标签/属性模式 */
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("src[\r\n]*=[\r\n]*'(.*?)'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("src[\r\n]*=[\r\n]*\"(.*?)\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("onerror(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("onclick(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("onmouseover(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
        Pattern.compile("onfocus(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
    };

    /**
     * 清理XSS
     */
    public static String clean(String value) {
        if (XuStrUtil.isBlank(value)) {
            return value;
        }
        String result = value;
        for (Pattern pattern : XSS_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        // HTML实体编码
        result = result.replace("<", "&lt;")
                       .replace(">", "&gt;")
                       .replace("\"", "&quot;")
                       .replace("'", "&#39;");
        return result;
    }

    /**
     * 检查是否包含XSS攻击代码
     */
    public static boolean hasXss(String value) {
        if (XuStrUtil.isBlank(value)) {
            return false;
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * HTML实体编码
     */
    public static String escapeHtml(String value) {
        if (XuStrUtil.isBlank(value)) {
            return value;
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * HTML实体解码
     */
    public static String unescapeHtml(String value) {
        if (XuStrUtil.isBlank(value)) {
            return value;
        }
        return value.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");
    }
}
