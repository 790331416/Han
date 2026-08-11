package com.han.gen.util;

import com.han.gen.domain.DbColumnInfo;
import com.han.gen.domain.GenTableColumn;

/**
 * 代码生成工具类 — DB 类型到 Java 类型映射
 */
public final class GenUtils {

    private GenUtils() {}

    /**
     * PostgreSQL / MySQL 列类型 → Java 类型。
     *
     * <p>PostgreSQL 侧入参是 {@code format_type(atttypid, atttypmod)} 的结果，形如
     * {@code character varying(64)}、{@code timestamp with time zone}、{@code integer[]}；
     * MySQL 侧入参是 {@code information_schema.columns.column_type}，形如
     * {@code datetime}、{@code longblob}、{@code tinyint(1)}。两种来源都走这一份映射。
     *
     * <p>带时区的类型必须先于不带时区的判断，否则时区语义会被 {@code LocalDateTime} 静默吃掉。
     */
    public static String dbTypeToJavaType(String columnType) {
        if (columnType == null) return "String";
        String type = columnType.toLowerCase().trim();

        // 数组类型没有开箱可用的 TypeHandler，先按字符串落地并在 PO 上提示需要自定义处理
        if (isArrayType(type)) return "String";

        if (type.startsWith("bigint") || type.startsWith("int8")) return "Long";
        if (type.startsWith("integer") || type.startsWith("int4") || type.startsWith("int2")
                || type.startsWith("smallint") || type.startsWith("mediumint")
                || type.startsWith("tinyint") || type.startsWith("int")) return "Integer";
        if (type.startsWith("numeric") || type.startsWith("decimal")) return "BigDecimal";
        if (type.startsWith("real") || type.startsWith("float4")) return "Float";
        if (type.startsWith("double") || type.startsWith("float8")) return "Double";
        if (type.startsWith("boolean") || type.startsWith("bool")) return "Boolean";
        if (type.startsWith("timestamp with time zone") || type.startsWith("timestamptz")) return "OffsetDateTime";
        // MySQL 的 datetime 无时区，与 PostgreSQL 不带时区的 timestamp 同语义
        if (type.startsWith("timestamp") || type.startsWith("datetime")) return "LocalDateTime";
        if (type.startsWith("date")) return "LocalDate";
        if (type.startsWith("time with time zone") || type.startsWith("timetz")) return "OffsetTime";
        if (type.startsWith("time")) return "LocalTime";
        // bytea 是 PostgreSQL 口径，blob / binary 系列是 MySQL 口径
        if (type.startsWith("bytea") || type.contains("blob") || type.contains("binary")) return "byte[]";
        // uuid / json / jsonb / inet / interval 等按文本落地，两种驱动都可直接读写字符串
        return "String";
    }

    /**
     * PostgreSQL 数组类型：{@code integer[]} 这种 format_type 形式，或 {@code _int4} 这种内部名
     */
    public static boolean isArrayType(String columnType) {
        if (columnType == null) return false;
        String type = columnType.toLowerCase().trim();
        return type.endsWith("[]") || type.startsWith("_");
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
                .isIncrement(isIncrementColumn(col) ? 1 : 0)
                .isRequired("NO".equals(col.getIsNullable()) ? 1 : 0)
                .isInsert(isInsertColumn(col) ? 1 : 0)
                .isEdit(isEditColumn(col) ? 1 : 0)
                .isList(isListColumn(col) ? 1 : 0)
                .isQuery(isQueryColumn(col) ? 1 : 0)
                .queryType(guessQueryType(col))
                .htmlType(guessHtmlType(col))
                .sort(sort)
                .build();
    }

    /**
     * PostgreSQL 的 serial / identity 列，默认值形如 nextval('xxx_id_seq'::regclass)
     */
    private static boolean isIncrementColumn(DbColumnInfo col) {
        String def = col.getColumnDefault();
        return def != null && def.toLowerCase().contains("nextval(");
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

    /**
     * 查询方式推断：时间列按区间查，名称类文本按模糊查，其余按等值查
     */
    private static String guessQueryType(DbColumnInfo col) {
        String javaType = dbTypeToJavaType(col.getColumnType());
        if (isDateType(javaType)) return "BETWEEN";
        String name = col.getColumnName();
        if ("String".equals(javaType) && (name.contains("name") || name.contains("title") || name.contains("code"))) {
            return "LIKE";
        }
        return "EQ";
    }

    private static String guessHtmlType(DbColumnInfo col) {
        String javaType = dbTypeToJavaType(col.getColumnType());
        String name = col.getColumnName();
        if (isDateType(javaType)) return "datetime";
        if (name.contains("content") || name.contains("remark")) return "textarea";
        if (name.contains("status") || name.contains("type") || name.contains("sex")) return "select";
        return "input";
    }

    /** 日期时间类 Java 类型，查询按区间、表单用日期控件 */
    public static boolean isDateType(String javaType) {
        return "LocalDateTime".equals(javaType) || "LocalDate".equals(javaType)
                || "OffsetDateTime".equals(javaType);
    }
}
