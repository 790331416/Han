package com.han.gen.util;

import com.han.common.core.exception.BusinessException;
import com.han.gen.domain.GenTable;
import com.han.gen.domain.GenTableColumn;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Velocity 模板渲染工具
 */
@Slf4j
@Component
public class VelocityHelper {

    /** 允许出现在生成路径中的单段标识符（模块名 / 业务名 / 功能名） */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    /** 允许的 Java 包名（点分标识符） */
    private static final Pattern SAFE_PACKAGE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}(\\.[A-Za-z][A-Za-z0-9_]{0,63}){0,15}$");

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.setProperty("resource.loaders", "class");
        props.setProperty("resource.loader.class.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        Velocity.init(props);
    }

    private static final String[] TEMPLATE_FILES = {
            "vm/java/po.java.vm",
            "vm/java/dto.java.vm",
            "vm/java/query.java.vm",
            "vm/java/mapper.java.vm",
            "vm/java/mapper.xml.vm",
            "vm/java/service.java.vm",
            "vm/java/serviceImpl.java.vm",
            "vm/java/controller.java.vm",
            "vm/vue/index.vue.vm",
            "vm/vue/api.ts.vm",
            "vm/sql/menu.sql.vm"
    };

    /**
     * 渲染所有模板
     *
     * @return Map<显示文件名, 代码内容>
     */
    public Map<String, String> renderTemplates(GenTable table) {
        VelocityContext ctx = buildContext(table);
        Map<String, String> result = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (String tpl : TEMPLATE_FILES) {
            try {
                Template template = Velocity.getTemplate(tpl, "UTF-8");
                StringWriter sw = new StringWriter();
                template.merge(ctx, sw);
                result.put(getOutputFileName(tpl, table), sw.toString());
            } catch (Exception e) {
                failed.add(tpl);
                log.error("代码生成失败：表[{}] 渲染模板[{}] 异常", table.getTableName(), tpl, e);
            }
        }
        if (!failed.isEmpty()) {
            throw new BusinessException(
                    "代码生成失败，以下模板渲染异常：" + String.join("、", failed) + "，详情见服务端日志");
        }
        return result;
    }

    /**
     * 打包为 ZIP 字节数组
     */
    public byte[] toZipBytes(Map<String, String> codes, GenTable table) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                zos.putNextEntry(new ZipEntry(requireSafeEntryName(entry.getKey())));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return bos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("代码生成失败：表[{}] 打包 ZIP 异常", table == null ? null : table.getTableName(), e);
            throw new BusinessException("生成ZIP失败：" + e.getMessage());
        }
    }

    /**
     * ZIP 条目名归一化与越界校验（CWE-22 zip slip）。
     * <p>条目名由配置字段拼成，即使各字段已通过格式校验，落盘前仍做一次前缀与穿越检查。
     */
    private String requireSafeEntryName(String rawName) {
        if (!StringUtils.hasText(rawName)) {
            throw new BusinessException("生成ZIP失败：存在空的文件名");
        }
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new BusinessException("生成ZIP失败：非法的文件路径[" + rawName + "]");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException("生成ZIP失败：非法的文件路径[" + rawName + "]");
            }
        }
        return normalized;
    }

    private VelocityContext buildContext(GenTable table) {
        if (table == null) {
            throw new BusinessException("生成表配置不存在");
        }
        List<GenTableColumn> columns = table.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new BusinessException("表[" + table.getTableName() + "]没有列信息，无法生成代码");
        }
        columns.forEach(c -> {
            c.setColumnComment(safeComment(c.getColumnComment()));
            // 空串在 Velocity 里的真假判定依赖引擎配置，统一归一成 null 让模板 #if 可靠
            if (!StringUtils.hasText(c.getDictType())) {
                c.setDictType(null);
            }
        });
        String className = requireIdentifier(table.getFunctionName(), "功能名");
        String businessName = requireIdentifier(table.getBusinessName(), "业务名");
        String moduleName = requireIdentifier(table.getModuleName(), "模块名");
        String packageName = requirePackageName(table.getPackageName());
        String classname = decapitalize(className);

        VelocityContext ctx = new VelocityContext();
        ctx.put("table", table);
        ctx.put("tableName", table.getTableName());
        ctx.put("tableComment", safeComment(StringUtils.hasText(table.getTableComment())
                ? table.getTableComment() : table.getTableName()));
        ctx.put("className", className);
        ctx.put("classname", classname);
        ctx.put("moduleName", moduleName);
        ctx.put("businessName", businessName);
        ctx.put("packageName", packageName);
        ctx.put("author", StringUtils.hasText(table.getAuthor()) ? table.getAuthor() : "HanCloud");
        ctx.put("date", LocalDate.now().toString());
        ctx.put("columns", columns);

        // 主键列：以 isPk 标记为准；复合主键或无主键时不生成按主键操作的端点
        List<GenTableColumn> pkColumns = columns.stream()
                .filter(c -> c.getIsPk() != null && c.getIsPk() == 1)
                .toList();
        GenTableColumn pkColumn = pkColumns.size() == 1 ? pkColumns.get(0) : null;
        ctx.put("pkColumns", pkColumns);
        ctx.put("pkColumn", pkColumn);
        ctx.put("hasPk", pkColumn != null);
        ctx.put("pkJavaField", pkColumn == null ? "" : pkColumn.getJavaField());
        ctx.put("pkJavaType", pkColumn == null ? "" : pkColumn.getJavaType());
        ctx.put("pkColumnName", pkColumn == null ? "" : pkColumn.getColumnName());
        ctx.put("pkAutoIncrement", pkColumn != null
                && pkColumn.getIsIncrement() != null && pkColumn.getIsIncrement() == 1);

        // 基类：按表实际拥有的列决定，缺列时降级到更浅的基类甚至不继承
        BaseClass baseClass = BaseClass.resolve(columns, pkColumn);
        ctx.put("baseClass", baseClass.simpleName);
        ctx.put("baseClassImport", baseClass.importName);
        ctx.put("hasBaseClass", baseClass != BaseClass.NONE);
        ctx.put("inheritedFields", baseClass.fields());
        ctx.put("hasDelFlag", columns.stream().anyMatch(c -> "delFlag".equals(c.getJavaField())));

        // 主键单独渲染，表单字段里不再重复出现
        List<GenTableColumn> dtoColumns = columns.stream()
                .filter(VelocityHelper::isDtoColumn)
                .filter(c -> pkColumn == null || !pkColumn.getJavaField().equals(c.getJavaField()))
                .toList();
        List<GenTableColumn> queryColumns = columns.stream().filter(VelocityHelper::isQueryColumn).toList();
        List<GenTableColumn> listColumns = columns.stream()
                .filter(c -> c.getIsList() != null && c.getIsList() == 1)
                .toList();
        // 需要加载字典下拉的列：列表要显示字典标签、搜索和表单要渲染下拉
        List<GenTableColumn> dictColumns = columns.stream()
                .filter(c -> StringUtils.hasText(c.getDictType()))
                .filter(c -> (c.getIsList() != null && c.getIsList() == 1)
                        || isQueryColumn(c) || isDtoColumn(c))
                .toList();
        ctx.put("formColumns", dtoColumns);
        ctx.put("queryColumns", queryColumns);
        ctx.put("listColumns", listColumns);
        ctx.put("dictColumns", dictColumns);
        ctx.put("importTypes", importsOf(columns));
        ctx.put("poImportTypes", importsOf(columns.stream()
                .filter(c -> !baseClass.fields().contains(c.getJavaField()))
                .toList()));
        ctx.put("dtoImportTypes", importsOf(dtoColumns));
        ctx.put("queryImportTypes", importsOf(queryColumns));

        ctx.put("fn", new GenTemplateFunctions());
        ctx.put("hasDtoColumn", !dtoColumns.isEmpty());
        ctx.put("hasQueryColumn", !queryColumns.isEmpty());
        ctx.put("dtoHasNotBlank", dtoColumns.stream().anyMatch(c -> isRequired(c) && "String".equals(c.getJavaType())));
        ctx.put("dtoHasNotNull", dtoColumns.stream().anyMatch(c -> isRequired(c) && !"String".equals(c.getJavaType())));
        ctx.put("queryHasText", queryColumns.stream()
                .anyMatch(c -> "String".equals(c.getJavaType()) && !"BETWEEN".equalsIgnoreCase(c.getQueryType())));
        ctx.put("hasListColumn", columns.stream().anyMatch(VelocityHelper::isListColumn));
        ctx.put("formRequired", dtoColumns.stream().anyMatch(VelocityHelper::isRequired));

        // 生成的 Vue 在 noUnusedLocals 下不容忍多余 import，import 清单按实际用到的能力算好再交给模板
        List<String> dictTypes = columns.stream()
                .map(GenTableColumn::getDictType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        ctx.put("dictTypes", dictTypes);
        ctx.put("hasDictColumn", !dictTypes.isEmpty());
        ctx.put("hasDictListColumn", columns.stream()
                .filter(VelocityHelper::isListColumn)
                .anyMatch(c -> StringUtils.hasText(c.getDictType())));

        List<String> icons = new ArrayList<>();
        if (!queryColumns.isEmpty()) {
            icons.add("Search");
            icons.add("Refresh");
        }
        if (!dtoColumns.isEmpty()) {
            icons.add("Plus");
            if (pkColumn != null) {
                icons.add("Edit");
            }
        }
        if (pkColumn != null) {
            icons.add("Delete");
        }
        ctx.put("iconImports", icons);

        // 菜单 SQL 用到的字符串字面量需要转义单引号，避免注入到生成的脚本里
        ctx.put("sqlTableComment", sqlLiteral(String.valueOf(ctx.get("tableComment"))));
        ctx.put("parentMenuId", table.getParentMenuId() == null ? 0L : table.getParentMenuId());

        return ctx;
    }

    private static boolean isDtoColumn(GenTableColumn c) {
        return (c.getIsInsert() != null && c.getIsInsert() == 1)
                || (c.getIsEdit() != null && c.getIsEdit() == 1);
    }

    private static boolean isQueryColumn(GenTableColumn c) {
        return c.getIsQuery() != null && c.getIsQuery() == 1;
    }

    private static boolean isRequired(GenTableColumn c) {
        return c.getIsRequired() != null && c.getIsRequired() == 1;
    }

    private static boolean isListColumn(GenTableColumn c) {
        return c.getIsList() != null && c.getIsList() == 1;
    }

    private static Set<String> importsOf(List<GenTableColumn> columns) {
        Set<String> javaTypes = columns.stream()
                .map(GenTableColumn::getJavaType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> imports = new LinkedHashSet<>();
        if (javaTypes.contains("BigDecimal")) imports.add("java.math.BigDecimal");
        if (javaTypes.contains("LocalDateTime")) imports.add("java.time.LocalDateTime");
        if (javaTypes.contains("LocalDate")) imports.add("java.time.LocalDate");
        if (javaTypes.contains("LocalTime")) imports.add("java.time.LocalTime");
        if (javaTypes.contains("OffsetDateTime")) imports.add("java.time.OffsetDateTime");
        if (javaTypes.contains("OffsetTime")) imports.add("java.time.OffsetTime");
        return imports;
    }

    private static String decapitalize(String name) {
        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }

    private static String sqlLiteral(String raw) {
        return raw == null ? "" : raw.replace("'", "''");
    }

    /** 注释会被写进生成代码的块注释里，`*&#47;` 会提前闭合注释导致产物编译不过 */
    private static String safeComment(String raw) {
        return raw == null ? null : raw.replace("*/", "*\\/").replace("\r", " ").replace("\n", " ");
    }

    private String requireIdentifier(String value, String label) {
        if (!StringUtils.hasText(value) || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new BusinessException(label + "只能由字母开头的字母、数字、下划线组成，当前值[" + value + "]非法");
        }
        return value;
    }

    private String requirePackageName(String value) {
        if (!StringUtils.hasText(value) || !SAFE_PACKAGE.matcher(value).matches()) {
            throw new BusinessException("包名只能是点分的合法 Java 标识符，当前值[" + value + "]非法");
        }
        return value;
    }

    private String getOutputFileName(String template, GenTable table) {
        String className = table.getFunctionName();
        String classname = table.getBusinessName();
        String pkg = table.getPackageName().replace(".", "/");
        String module = table.getModuleName();

        return switch (template) {
            case "vm/java/po.java.vm" -> pkg + "/domain/po/" + className + "Po.java";
            case "vm/java/dto.java.vm" -> pkg + "/domain/dto/" + className + "Dto.java";
            case "vm/java/query.java.vm" -> pkg + "/domain/query/" + className + "Query.java";
            case "vm/java/mapper.java.vm" -> pkg + "/mapper/" + className + "Mapper.java";
            case "vm/java/mapper.xml.vm" -> "mapper/" + className + "Mapper.xml";
            case "vm/java/service.java.vm" -> pkg + "/service/I" + className + "Service.java";
            case "vm/java/serviceImpl.java.vm" -> pkg + "/service/impl/" + className + "ServiceImpl.java";
            case "vm/java/controller.java.vm" -> pkg + "/controller/admin/A" + className + "Controller.java";
            case "vm/vue/index.vue.vm" -> "vue/views/" + module + "/" + classname + "/index.vue";
            case "vm/vue/api.ts.vm" -> "vue/api/" + module + "/" + classname + ".ts";
            case "vm/sql/menu.sql.vm" -> "sql/" + classname + "_menu.sql";
            default -> template;
        };
    }

    /**
     * 生成的 PO 可继承的基类。
     *
     * <p>基类里声明的每个字段都会参与 MyBatis-Plus 的 SQL 拼装，表里缺任何一列都会在运行期
     * 报 column does not exist，因此只有列齐全（且 Java 类型匹配）时才允许继承。
     */
    private enum BaseClass {

        BIZ("BizEntity", "com.han.common.mybatis.domain.entity.BizEntity"),
        TENANT("TenantEntity", "com.han.common.mybatis.domain.entity.TenantEntity"),
        BASE("BaseEntity", "com.han.common.mybatis.domain.entity.BaseEntity"),
        NONE("", "");

        /** BaseEntity 声明的持久化字段 → 要求的 Java 类型 */
        private static final Map<String, String> BASE_FIELDS = Map.of(
                "id", "Long",
                "createTime", "LocalDateTime",
                "updateTime", "LocalDateTime",
                "delFlag", "Integer");

        /** TenantEntity 在 BaseEntity 之上追加的字段 */
        private static final Map<String, String> TENANT_FIELDS = Map.of("tenantId", "Long");

        /** BizEntity 在 TenantEntity 之上追加的字段 */
        private static final Map<String, String> BIZ_FIELDS = Map.of(
                "createBy", "Long",
                "createName", "String",
                "updateBy", "Long",
                "updateName", "String",
                "createDept", "Long",
                "remark", "String");

        private final String simpleName;
        private final String importName;

        BaseClass(String simpleName, String importName) {
            this.simpleName = simpleName;
            this.importName = importName;
        }

        Set<String> fields() {
            return switch (this) {
                case BIZ -> union(BASE_FIELDS.keySet(), TENANT_FIELDS.keySet(), BIZ_FIELDS.keySet());
                case TENANT -> union(BASE_FIELDS.keySet(), TENANT_FIELDS.keySet(), Set.of());
                case BASE -> union(BASE_FIELDS.keySet(), Set.of(), Set.of());
                case NONE -> Set.of();
            };
        }

        static BaseClass resolve(List<GenTableColumn> columns, GenTableColumn pkColumn) {
            // BaseEntity 把 id 固定为雪花主键，主键列名或类型对不上就不能继承
            if (pkColumn == null || !"id".equals(pkColumn.getJavaField()) || !"Long".equals(pkColumn.getJavaType())) {
                return NONE;
            }
            Map<String, String> actual = columns.stream()
                    .filter(c -> StringUtils.hasText(c.getJavaField()))
                    .collect(Collectors.toMap(GenTableColumn::getJavaField,
                            c -> c.getJavaType() == null ? "" : c.getJavaType(), (a, b) -> a));
            if (!covers(actual, BASE_FIELDS)) {
                return NONE;
            }
            if (!covers(actual, TENANT_FIELDS)) {
                return BASE;
            }
            return covers(actual, BIZ_FIELDS) ? BIZ : TENANT;
        }

        private static boolean covers(Map<String, String> actual, Map<String, String> required) {
            return required.entrySet().stream()
                    .allMatch(e -> e.getValue().equals(actual.get(e.getKey())));
        }

        private static Set<String> union(Set<String> a, Set<String> b, Set<String> c) {
            Set<String> all = new LinkedHashSet<>(a);
            all.addAll(b);
            all.addAll(c);
            return all;
        }
    }
}
