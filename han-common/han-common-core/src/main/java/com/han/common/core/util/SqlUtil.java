package com.han.common.core.util;

import java.util.regex.Pattern;

/**
 * SQL工具类
 */
public final class SqlUtil {

    private SqlUtil() {}

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('(''|''|')|;|--|xp_|\\bor|\\bexec|\\bunion|\\bselect|\\binsert|\\bupdate|\\bdelete|\\bdrop|\\bcreate|\\balter|\\btruncate)",
        Pattern.CASE_INSENSITIVE
    );

    public static boolean isSafeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        return !SQL_INJECTION_PATTERN.matcher(sql).find();
    }

    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }

    public static String escapeSingleQuote(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("'", "''");
    }

    public static String buildInClause(String columnName, Object... values) {
        if (values == null || values.length == 0) {
            return "1 = 0";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(columnName).append(" IN (");

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Object value = values[i];
            if (value instanceof String s) {
                sb.append("'").append(escapeSingleQuote(s)).append("'");
            } else if (value == null) {
                sb.append("NULL");
            } else {
                sb.append(value);
            }
        }

        sb.append(")");
        return sb.toString();
    }

    public static String buildLikeClause(String columnName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return "1 = 1";
        }
        return columnName + " LIKE '%" + escapeLike(value) + "%'";
    }

    public static String buildBetweenClause(String columnName, Object minValue, Object maxValue) {
        if (minValue == null && maxValue == null) {
            return "1 = 1";
        }

        StringBuilder sb = new StringBuilder(columnName);

        if (minValue != null && maxValue != null) {
            sb.append(" BETWEEN ").append(minValue).append(" AND ").append(maxValue);
        } else if (minValue != null) {
            sb.append(" >= ").append(minValue);
        } else {
            sb.append(" <= ").append(maxValue);
        }

        return sb.toString();
    }

    public static String checkOrderBy(String orderBy, String defaultOrderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            return defaultOrderBy;
        }

        if (!isSafeSql(orderBy)) {
            return defaultOrderBy;
        }

        return orderBy;
    }
}
