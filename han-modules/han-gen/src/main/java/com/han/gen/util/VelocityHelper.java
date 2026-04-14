package com.han.gen.util;

import com.han.gen.domain.GenTable;
import com.han.gen.domain.GenTableColumn;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Velocity 模板渲染工具
 */
@Component
public class VelocityHelper {

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
     * @return Map<显示文件名, 代码内容>
     */
    public Map<String, String> renderTemplates(GenTable table) {
        VelocityContext ctx = buildContext(table);
        Map<String, String> result = new LinkedHashMap<>();
        for (String tpl : TEMPLATE_FILES) {
            try {
                Template template = Velocity.getTemplate(tpl, "UTF-8");
                StringWriter sw = new StringWriter();
                template.merge(ctx, sw);
                String fileName = getOutputFileName(tpl, table);
                result.put(fileName, sw.toString());
            } catch (Exception e) {
                // 模板不存在则跳过
            }
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
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes("UTF-8"));
                zos.closeEntry();
            }
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("生成ZIP失败", e);
        }
    }

    private VelocityContext buildContext(GenTable table) {
        VelocityContext ctx = new VelocityContext();
        String className = table.getFunctionName();
        String classname = className.substring(0, 1).toLowerCase() + className.substring(1);

        ctx.put("table", table);
        ctx.put("tableName", table.getTableName());
        ctx.put("tableComment", table.getTableComment());
        ctx.put("className", className);
        ctx.put("classname", classname);
        ctx.put("moduleName", table.getModuleName());
        ctx.put("businessName", table.getBusinessName());
        ctx.put("packageName", table.getPackageName());
        ctx.put("author", table.getAuthor());
        ctx.put("date", LocalDate.now().toString());
        ctx.put("columns", table.getColumns());

        // 主键列
        GenTableColumn pkColumn = table.getColumns().stream()
                .filter(c -> c.getIsPk() != null && c.getIsPk() == 1)
                .findFirst().orElse(table.getColumns().get(0));
        ctx.put("pkColumn", pkColumn);

        // 需要导入的 Java 类型（仅收集需要 import 的全限定名）
        Set<String> javaTypes = table.getColumns().stream()
                .map(GenTableColumn::getJavaType)
                .collect(Collectors.toSet());
        Set<String> importTypes = new LinkedHashSet<>();
        if (javaTypes.contains("BigDecimal")) importTypes.add("java.math.BigDecimal");
        if (javaTypes.contains("LocalDateTime")) importTypes.add("java.time.LocalDateTime");
        if (javaTypes.contains("LocalDate")) importTypes.add("java.time.LocalDate");
        if (javaTypes.contains("LocalTime")) importTypes.add("java.time.LocalTime");
        ctx.put("importTypes", importTypes);

        return ctx;
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
}
