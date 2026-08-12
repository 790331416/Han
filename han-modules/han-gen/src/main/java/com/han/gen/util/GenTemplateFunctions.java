package com.han.gen.util;

import java.util.Locale;
import java.util.Set;

/**
 * 暴露给 Velocity 模板的辅助函数（模板里以 ${fn} 引用）
 *
 * <p>模板语言本身没有字符串处理能力，属性名首字母大写、查询方式到 Wrapper 方法名的映射
 * 这类逻辑放在这里，避免在每个 vm 里手写分支。
 */
public final class GenTemplateFunctions {

    /** 生成 Vue 表单时按字典渲染下拉的显示类型 */
    private static final Set<String> DICT_HTML_TYPES = Set.of("select", "radio", "checkbox");

    /** 首字母大写，用于拼 getXxx / setXxx */
    public String cap(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /** 首字母小写 */
    public String uncap(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }

    /** 查询方式 → LambdaQueryWrapper 方法名 */
    public String wrapperMethod(String queryType) {
        if (queryType == null) {
            return "eq";
        }
        return switch (queryType.toUpperCase(Locale.ROOT)) {
            case "NE" -> "ne";
            case "GT" -> "gt";
            case "GE" -> "ge";
            case "LT" -> "lt";
            case "LE" -> "le";
            case "LIKE" -> "like";
            case "BETWEEN" -> "between";
            default -> "eq";
        };
    }

    /** 是否区间查询（生成 xxxBegin / xxxEnd 两个查询字段） */
    public boolean isBetween(String queryType) {
        return "BETWEEN".equalsIgnoreCase(queryType);
    }

    /** 字符串类型的字段用 hasText 判空，其余用 != null */
    public boolean isStringType(String javaType) {
        return "String".equals(javaType);
    }

    /** 日期时间类型，表单用日期控件、查询用区间 */
    public boolean isDateType(String javaType) {
        return "LocalDateTime".equals(javaType) || "LocalDate".equals(javaType)
                || "OffsetDateTime".equals(javaType);
    }

    /** 仅到日的日期类型，日期控件不需要时分秒 */
    public boolean isDateOnlyType(String javaType) {
        return "LocalDate".equals(javaType);
    }

    /** 该显示类型是否需要字典下拉 */
    public boolean isDictHtmlType(String htmlType) {
        return htmlType != null && DICT_HTML_TYPES.contains(htmlType);
    }

    /**
     * Java 类型 → TypeScript 类型。
     * <p>Long / BigDecimal 超出 JS 安全整数范围时后端会以字符串下发，保留联合类型。
     */
    public String tsType(String javaType) {
        if (javaType == null) {
            return "string";
        }
        return switch (javaType) {
            case "Long", "Integer", "BigDecimal", "Float", "Double" -> "string | number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    /**
     * 表单字段的 TypeScript 类型。
     * <p>Long 可能超出 JS 安全整数范围，保留联合类型并用文本框录入；其余数值类型可直接用数字输入框。
     */
    public String tsFormType(String javaType) {
        if (javaType == null) {
            return "string";
        }
        return switch (javaType) {
            case "Integer", "Float", "Double", "BigDecimal" -> "number";
            case "Long" -> "string | number";
            case "Boolean" -> "boolean";
            default -> "string";
        };
    }

    /** 该字段是否适合用 el-input-number 录入 */
    public boolean isNumberInput(String javaType) {
        return "Integer".equals(javaType) || "Float".equals(javaType)
                || "Double".equals(javaType) || "BigDecimal".equals(javaType);
    }

    /** 单引号转义，避免注释/表名注入到生成的 SQL 脚本里 */
    public String sqlLiteral(String raw) {
        return raw == null ? "" : raw.replace("'", "''");
    }

    /** 转义成 Java 字符串字面量内容，避免列注释里的引号截断生成代码 */
    public String javaString(String raw) {
        return raw == null ? "" : raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 转义成 HTML 属性值，避免列注释里的引号截断生成的 Vue 模板 */
    public String attr(String raw) {
        return raw == null ? "" : raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
