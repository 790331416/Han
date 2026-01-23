package com.xuman.common.core.util;

import com.xuman.common.core.exception.SecurityException;

import java.util.regex.Pattern;

/**
 * SQL安全工具类
 */
public final class SqlUtil {

    private SqlUtil() {}

    /** SQL注入关键字 */
    private static final String[] SQL_KEYWORDS = {
        "select", "insert", "update", "delete", "drop", "truncate",
        "exec", "execute", "xp_", "sp_", "0x", "union", "--", "/*", "*/",
        "declare", "cast", "convert", "char(", "nchar(", "varchar(",
        "nvarchar(", "alter", "begin", "cursor", "end", "fetch",
        "kill", "open", "sysobjects", "syscolumns", "sys."
    };

    /** 特殊字符模式 */
    private static final Pattern SPECIAL_CHAR_PATTERN = 
        Pattern.compile("[';\\-\\-]|/\\*|\\*/|xp_|sp_|exec|execute", Pattern.CASE_INSENSITIVE);

    /**
     * 检查是否包含SQL注入关键字
     */
    public static boolean hasSqlInjection(String value) {
        if (XuStrUtil.isBlank(value)) {
            return false;
        }
        String lowerValue = value.toLowerCase();
        for (String keyword : SQL_KEYWORDS) {
            if (lowerValue.contains(keyword)) {
                return true;
            }
        }
        return SPECIAL_CHAR_PATTERN.matcher(value).find();
    }

    /**
     * SQL参数过滤
     */
    public static String escapeSql(String value) {
        if (XuStrUtil.isBlank(value)) {
            return value;
        }
        return value.replace("'", "''")
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }

    /**
     * 校验排序字段（防止order by注入）
     */
    public static String checkOrderBy(String orderBy) {
        if (XuStrUtil.isBlank(orderBy)) {
            return "";
        }
        // 只允许字母、数字、下划线、逗号、空格、点
        if (!orderBy.matches("^[a-zA-Z0-9_,\\s\\.]+$")) {
            throw new SecurityException("非法的排序参数");
        }
        if (hasSqlInjection(orderBy)) {
            throw new SecurityException("排序参数包含非法字符");
        }
        return orderBy;
    }

    /**
     * 校验表名/列名
     */
    public static String checkColumnName(String columnName) {
        if (XuStrUtil.isBlank(columnName)) {
            return "";
        }
        if (!columnName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new SecurityException("非法的列名");
        }
        return columnName;
    }

    /**
     * 校验参数值安全性
     */
    public static void checkParamSafe(String value) {
        if (hasSqlInjection(value)) {
            throw new SecurityException("检测到SQL注入攻击");
        }
    }
}
