package com.han.common.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL工具类
 * <p>
 * 拼接类方法（{@code buildXxxClause}）已标记废弃：即便做了转义，字符串拼接仍然依赖调用方
 * 保证列名可信。新代码请使用返回 {@link SqlFragment} 的参数化版本，把值交给 JDBC 占位符。
 */
public final class SqlUtil {

    private SqlUtil() {}

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('(''|''|')|;|--|xp_|\\bor|\\bexec|\\bunion|\\bselect|\\binsert|\\bupdate|\\bdelete|\\bdrop|\\bcreate|\\balter|\\btruncate)",
        Pattern.CASE_INSENSITIVE
    );

    /** 合法列名：标识符，允许一级表别名前缀 */
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile(
        "^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?$"
    );

    /** 合法排序项：列名 + 可选的 ASC/DESC */
    private static final Pattern ORDER_BY_ITEM_PATTERN = Pattern.compile(
        "^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?(\\s+(ASC|DESC))?$",
        Pattern.CASE_INSENSITIVE
    );

    /** 数值字面量形状，用于确认 Number 可以安全地不加引号直接拼接 */
    private static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile(
        "^[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?$"
    );

    /**
     * 参数化 SQL 片段：{@code sql} 内的值全部以 {@code ?} 占位，实参按顺序放在 {@code params}。
     */
    public record SqlFragment(String sql, List<Object> params) {

        public SqlFragment(String sql, List<Object> params) {
            this.sql = sql;
            this.params = Collections.unmodifiableList(new ArrayList<>(params));
        }

        /** 恒真片段，用于条件为空时占位 */
        public static SqlFragment alwaysTrue() {
            return new SqlFragment("1 = 1", List.of());
        }

        /** 恒假片段，用于 IN 空集合时占位 */
        public static SqlFragment alwaysFalse() {
            return new SqlFragment("1 = 0", List.of());
        }
    }

    public static boolean isSafeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        return !SQL_INJECTION_PATTERN.matcher(sql).find();
    }

    /**
     * 校验列名是否为合法标识符，非法直接抛出，避免列名成为注入入口。
     */
    public static String checkColumnName(String columnName) {
        if (columnName == null || !COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new IllegalArgumentException("非法的列名: " + columnName);
        }
        return columnName;
    }

    /**
     * 转义 LIKE 通配符。
     * <p>注意：本方法只处理 {@code \ % _}，不处理单引号；拼进字符串字面量前必须再过
     * {@link #escapeSingleQuote(String)}。
     */
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

    // ==================== 参数化片段（推荐） ====================

    /**
     * 生成参数化的 LIKE 片段：{@code col LIKE ?}，通配符已转义。
     */
    public static SqlFragment likeFragment(String columnName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return SqlFragment.alwaysTrue();
        }
        checkColumnName(columnName);
        return new SqlFragment(columnName + " LIKE ?", List.of("%" + escapeLike(value) + "%"));
    }

    /**
     * 生成参数化的 IN 片段：{@code col IN (?, ?, ...)}。
     */
    public static SqlFragment inFragment(String columnName, Object... values) {
        if (values == null || values.length == 0) {
            return SqlFragment.alwaysFalse();
        }
        checkColumnName(columnName);
        StringBuilder sb = new StringBuilder(columnName).append(" IN (");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        sb.append(")");
        return new SqlFragment(sb.toString(), Arrays.asList(values));
    }

    /**
     * 生成参数化的区间片段：两端都有值时用 BETWEEN，只有一端时退化为 {@code >=} / {@code <=}。
     */
    public static SqlFragment betweenFragment(String columnName, Object minValue, Object maxValue) {
        if (minValue == null && maxValue == null) {
            return SqlFragment.alwaysTrue();
        }
        checkColumnName(columnName);
        if (minValue != null && maxValue != null) {
            return new SqlFragment(columnName + " BETWEEN ? AND ?", Arrays.asList(minValue, maxValue));
        }
        if (minValue != null) {
            return new SqlFragment(columnName + " >= ?", List.of(minValue));
        }
        return new SqlFragment(columnName + " <= ?", List.of(maxValue));
    }

    // ==================== 字符串拼接（已废弃，仅保留兼容） ====================

    /**
     * @deprecated 改用 {@link #inFragment(String, Object...)}。
     */
    @Deprecated(since = "1.0.0")
    public static String buildInClause(String columnName, Object... values) {
        if (values == null || values.length == 0) {
            return "1 = 0";
        }
        checkColumnName(columnName);

        StringBuilder sb = new StringBuilder();
        sb.append(columnName).append(" IN (");

        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(toSqlLiteral(values[i]));
        }

        sb.append(")");
        return sb.toString();
    }

    /**
     * @deprecated 改用 {@link #likeFragment(String, String)}。
     */
    @Deprecated(since = "1.0.0")
    public static String buildLikeClause(String columnName, String value) {
        if (value == null || value.trim().isEmpty()) {
            return "1 = 1";
        }
        checkColumnName(columnName);
        return columnName + " LIKE '%" + escapeSingleQuote(escapeLike(value)) + "%'";
    }

    /**
     * @deprecated 改用 {@link #betweenFragment(String, Object, Object)}。
     */
    @Deprecated(since = "1.0.0")
    public static String buildBetweenClause(String columnName, Object minValue, Object maxValue) {
        if (minValue == null && maxValue == null) {
            return "1 = 1";
        }
        checkColumnName(columnName);

        StringBuilder sb = new StringBuilder(columnName);

        if (minValue != null && maxValue != null) {
            sb.append(" BETWEEN ").append(toSqlLiteral(minValue)).append(" AND ").append(toSqlLiteral(maxValue));
        } else if (minValue != null) {
            sb.append(" >= ").append(toSqlLiteral(minValue));
        } else {
            sb.append(" <= ").append(toSqlLiteral(maxValue));
        }

        return sb.toString();
    }

    /**
     * 校验排序表达式。只放行「列名[ ASC|DESC]」形式的逗号分隔列表，其余一律回退默认值。
     */
    public static String checkOrderBy(String orderBy, String defaultOrderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            return defaultOrderBy;
        }

        for (String item : orderBy.split(",")) {
            if (!ORDER_BY_ITEM_PATTERN.matcher(item.trim()).matches()) {
                return defaultOrderBy;
            }
        }

        return orderBy;
    }

    /**
     * 把值转为 SQL 字面量：数值/布尔原样输出，其余一律加引号并转义单引号。
     */
    private static String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        String text = String.valueOf(value);
        if (value instanceof Number && NUMERIC_LITERAL_PATTERN.matcher(text).matches()) {
            return text;
        }
        return "'" + escapeSingleQuote(text) + "'";
    }
}
