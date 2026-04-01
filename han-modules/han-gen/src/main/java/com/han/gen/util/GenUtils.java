package com.han.gen.util;

import com.han.gen.domain.DbColumnInfo;
import com.han.gen.domain.GenTableColumn;

/**
 * 代码生成工具类 — DB 类型到 Java 类型映射
 */
public final class GenUtils {

    private GenUtils() {}

    /**
     * PostgreSQL 列类型 → Java 类型
     */
    public static String dbTypeToJavaType(String columnType) {
        if (columnType == null) return "String";
        String type = columnType.toLowerCase().trim();

        if (type.startsWith("bigint") || type.startsWith("int8")) return "Long";
        if (type.startsWith("integer") || type.startsWith("int4") || type.startsWith("int2") || type.startsWith("smallint")) return "Integer";
        if (type.startsWith("numeric") || type.startsWith("decimal")) return "BigDecimal";
        if (type.startsWith("real") || type.startsWith("float4")) return "Float";
        if (type.startsWith("double") || type.startsWith("float8")) return "Double";
        if (type.startsWith("boolean") || type.startsWith("bool")) return "Boolean";
        if (type.startsWith("timestamp")) return "LocalDateTime";
        if (type.startsWith("date")) return "LocalDate";
        if (type.startsWith("time")) return "LocalTime";
        if (type.startsWith("bytea")) return "byte[]";
        if (type.startsWith("json") || type.startsWith("jsonb")) return "String";
        return "String";
    }

    /**
     * 列名 → Java 字段名（下划线转驼峰）
     */
    public static String columnNameToJavaField(String columnName) {
        if (columnName == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
    }

    /**
     * 表名 → 类名（下划线转帕斯卡，去除前缀）
     */
    public static String tableNameToClassName(String tableName) {
        if (tableName == null) return "";
        // 去除 sys_ / gen_ 等常见前缀
        String name = tableName;
        if (name.contains("_")) {
            String prefix = name.substring(0, name.indexOf('_'));
            if (prefix.length() <= 4) {
                name = name.substring(name.indexOf('_') + 1);
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean upperNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return sb.toString();
    }

    /**
     * 将 DB 列信息转换为 GenTableColumn
     */
    public static GenTableColumn toGenColumn(DbColumnInfo col, Long tableId, int sort) {
        return GenTableColumn.builder()
                .tableId(tableId)
                .columnName(col.getColumnName())
                .columnComment(col.getColumnComment())
                .columnType(col.getColumnType())
                .javaType(dbTypeToJavaType(col.getColumnType()))
                .javaField(columnNameToJavaField(col.getColumnName()))
                .isPk("PRI".equals(col.getColumnKey()) ? 1 : 0)
                .isIncrement(0)
                .isRequired("NO".equals(col.getIsNullable()) ? 1 : 0)
                .isInsert(isInsertColumn(col) ? 1 : 0)
                .isEdit(isEditColumn(col) ? 1 : 0)
                .isList(isListColumn(col) ? 1 : 0)
                .isQuery(isQueryColumn(col) ? 1 : 0)
                .queryType("EQ")
                .htmlType(guessHtmlType(col))
                .sort(sort)
                .build();
    }

    private static boolean isInsertColumn(DbColumnInfo col) {
        String name = col.getColumnName();
        return !"id".equals(name) && !"create_time".equals(name) && !"create_by".equals(name)
                && !"update_time".equals(name) && !"update_by".equals(name) && !"del_flag".equals(name)
                && !"tenant_id".equals(name);
    }

    private static boolean isEditColumn(DbColumnInfo col) {
        return isInsertColumn(col) && !"PRI".equals(col.getColumnKey());
    }

    private static boolean isListColumn(DbColumnInfo col) {
        String name = col.getColumnName();
        return !"del_flag".equals(name) && !"tenant_id".equals(name);
    }

    private static boolean isQueryColumn(DbColumnInfo col) {
        String name = col.getColumnName();
        return name.contains("name") || name.contains("title") || "status".equals(name) || name.contains("type");
    }

    private static String guessHtmlType(DbColumnInfo col) {
        String javaType = dbTypeToJavaType(col.getColumnType());
        String name = col.getColumnName();
        if ("LocalDateTime".equals(javaType) || "LocalDate".equals(javaType)) return "datetime";
        if (name.contains("content") || name.contains("remark")) return "textarea";
        if (name.contains("status") || name.contains("type") || name.contains("sex")) return "select";
        return "input";
    }
}
