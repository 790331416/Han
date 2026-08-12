package com.han.gen.util;

import com.han.common.core.exception.BusinessException;
import com.han.gen.domain.GenTable;
import com.han.gen.domain.GenTableColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成模板的产物校验。
 *
 * <p>模板缺陷会被复制到每一个新生成的模块，因此这里不只断言「渲染没抛异常」，而是对产出的
 * 文本做结构断言，并把全部产物落到 {@code target/gen-verify/} 供人工核对与试编译。
 */
class VelocityHelperTest {

    private static final Path DUMP_ROOT = Path.of("target", "gen-verify");

    private static VelocityHelper helper;

    @BeforeAll
    static void setUp() {
        helper = new VelocityHelper();
        helper.init();
    }

    private Map<String, String> render(GenTable table) {
        Map<String, String> files = helper.renderTemplates(table);
        dump(table.getTableName(), files);
        return files;
    }

    /** 把产物写到 target/gen-verify/<表名>/ 下，方便人工核对与 javac 试编译 */
    private void dump(String tableName, Map<String, String> files) {
        try {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path target = DUMP_ROOT.resolve(tableName).resolve(entry.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IllegalStateException("落盘生成产物失败", e);
        }
    }

    private String fileEndingWith(Map<String, String> files, String suffix) {
        return files.entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未生成以 " + suffix + " 结尾的文件，实际产物：" + files.keySet()));
    }

    @Test
    @DisplayName("11 个模板必须全部产出，缺一个就说明有模板被静默跳过")
    void shouldRenderAllTemplates() {
        Map<String, String> files = render(GenFixtures.sysNotice());
        assertEquals(11, files.size(), "实际产物：" + files.keySet());
        files.forEach((name, content) ->
                assertFalse(content.isBlank(), "文件 " + name + " 内容为空"));
    }

    @Test
    @DisplayName("审计列齐全的表继承 BizEntity，且不重复声明父类字段")
    void shouldExtendBizEntityWhenAllAuditColumnsPresent() {
        String po = fileEndingWith(render(GenFixtures.sysNotice()), "NoticePo.java");

        assertTrue(po.contains("extends BizEntity"), po);
        assertTrue(po.contains("import com.han.common.mybatis.domain.entity.BizEntity;"), po);
        assertTrue(po.contains("@EqualsAndHashCode(callSuper = true)"), po);
        assertTrue(po.contains("private String noticeTitle;"), po);
        // 父类已经声明的 11 个字段一个都不能在子类里重复出现
        for (String inherited : List.of("private Long id;", "private Long tenantId;", "private Long createBy;",
                "private String createName;", "private Long updateBy;", "private String updateName;",
                "private Long createDept;", "private String remark;", "private LocalDateTime createTime;",
                "private LocalDateTime updateTime;", "private Integer delFlag;")) {
            assertFalse(po.contains(inherited), "不应重复声明父类字段：" + inherited + "\n" + po);
        }
    }

    @Test
    @DisplayName("缺 create_by/create_name/create_dept/remark 的表降级到 TenantEntity")
    void shouldDegradeToTenantEntityWhenBizAuditColumnsMissing() {
        String po = fileEndingWith(render(GenFixtures.sysNoticeRead()), "NoticeReadPo.java");

        assertTrue(po.contains("extends TenantEntity"), po);
        assertFalse(po.contains("extends BizEntity"), po);
        assertTrue(po.contains("private Long noticeId;"), po);
        assertTrue(po.contains("private LocalDateTime readTime;"), po);
        // TenantEntity 已带 id/tenantId/createTime/updateTime/delFlag
        assertFalse(po.contains("private Long tenantId;"), po);
        assertFalse(po.contains("private Integer delFlag;"), po);
    }

    @Test
    @DisplayName("缺 del_flag 的表不继承任何基类，自带 @TableId 与全部列")
    void shouldNotExtendAnyBaseClassWhenDelFlagMissing() {
        String po = fileEndingWith(render(GenFixtures.genTable()), "GenBizTablePo.java");

        assertFalse(po.contains("extends "), po);
        assertTrue(po.contains("implements Serializable"), po);
        assertTrue(po.contains("@TableId(value = \"id\", type = IdType.ASSIGN_ID)"), po);
        assertTrue(po.contains("private Long tenantId;"), po);
        assertTrue(po.contains("private LocalDateTime createTime;"), po);
        assertFalse(po.contains("@EqualsAndHashCode"), po);
    }

    @Test
    @DisplayName("主键不叫 id 时按真实主键列生成，自增列用 IdType.AUTO")
    void shouldUseRealPrimaryKeyColumn() {
        Map<String, String> files = render(GenFixtures.customPkTable());
        String po = fileEndingWith(files, "ProjectPo.java");
        String controller = fileEndingWith(files, "AProjectController.java");

        assertTrue(po.contains("@TableId(value = \"project_id\", type = IdType.AUTO)"), po);
        assertTrue(po.contains("private Long projectId;"), po);
        assertFalse(po.contains("private Long id;"), po);

        assertTrue(controller.contains("@GetMapping(\"/{projectId}\")"), controller);
        assertTrue(controller.contains("@PathVariable Long projectId"), controller);
        assertTrue(controller.contains("getById(projectId)"), controller);
        assertFalse(controller.contains("@PathVariable Long id"), controller);
    }

    @Test
    @DisplayName("复合主键表不生成按单主键操作的端点，也不产生无法编译的引用")
    void shouldSkipSingleKeyEndpointsForCompositeKey() {
        Map<String, String> files = render(GenFixtures.sysUserRole());
        String controller = fileEndingWith(files, "AUserRoleController.java");
        String po = fileEndingWith(files, "UserRolePo.java");

        assertFalse(controller.contains("removeById"), controller);
        assertFalse(controller.contains("getById"), controller);
        assertTrue(controller.contains("没有单列主键"), controller);
        assertTrue(controller.contains(":list'"), controller);
        // 复合主键无法用 @TableId 表达，退化为普通字段，与 SysUserRolePo 的既有写法一致
        assertFalse(po.contains("@TableId"), po);
        assertTrue(po.contains("private Long userId;"), po);
    }

    @Test
    @DisplayName("生成的控制器逐方法分权，五个权限点齐全")
    void shouldGeneratePerMethodPermissions() {
        String controller = fileEndingWith(render(GenFixtures.sysNotice()), "ANoticeController.java");

        for (String action : List.of("list", "query", "add", "edit", "remove")) {
            assertTrue(controller.contains("@ss.hasAuthority('demo:notice:" + action + "')"),
                    "缺少权限点 demo:notice:" + action + "\n" + controller);
        }
        assertTrue(controller.contains("@AdminAuth"), controller);
        assertTrue(controller.contains("@RepeatSubmit"), controller);
        assertTrue(controller.contains("@OperLog("), controller);
        assertTrue(controller.contains("@Valid @RequestBody NoticeDto dto"), controller);
    }

    @Test
    @DisplayName("menu.sql 用真实列名 sort、显式给出 id，并生成四条按钮权限")
    void shouldGenerateExecutableMenuSql() {
        String sql = fileEndingWith(render(GenFixtures.sysNotice()), "_menu.sql");

        assertFalse(sql.contains("order_num"), "sys_menu 没有 order_num 列\n" + sql);
        assertTrue(sql.contains("sort, visible, status"), sql);
        // id 无默认值也无序列，脚本必须自己算出来
        assertTrue(sql.contains("COALESCE(MAX(id), 0) + 1"), sql);
        assertTrue(sql.contains("ancestors"), sql);
        for (String action : List.of("query", "add", "edit", "remove")) {
            assertTrue(sql.contains("'demo:notice:" + action + "'"), "缺少按钮权限 " + action + "\n" + sql);
        }
        assertTrue(sql.contains("INSERT INTO sys_role_menu"), sql);
    }

    @Test
    @DisplayName("isRequired 落到 DTO 校验注解与 Vue 表单规则")
    void shouldApplyRequiredFlag() {
        Map<String, String> files = render(GenFixtures.sysNotice());
        String dto = fileEndingWith(files, "NoticeDto.java");
        String vue = fileEndingWith(files, "index.vue");

        assertTrue(dto.contains("import jakarta.validation.constraints.NotBlank;"), dto);
        assertTrue(dto.contains("@NotBlank(message = \"公告标题不能为空\")"), dto);
        assertTrue(vue.contains("noticeTitle: [{ required: true"), vue);
    }

    @Test
    @DisplayName("queryType 落到 LambdaQueryWrapper，BETWEEN 生成 Begin/End 两个查询字段")
    void shouldApplyQueryType() {
        // 时间列不在默认查询字段里，这里模拟使用者在配置页勾上「创建时间」——审计里点名的常见操作
        Map<String, String> files = render(withQueryColumn(GenFixtures.sysNotice(), "createTime"));
        String query = fileEndingWith(files, "NoticeQuery.java");
        String impl = fileEndingWith(files, "NoticeServiceImpl.java");

        // notice_title 含 title，按模糊查
        assertTrue(impl.contains("wrapper.like("), impl);
        assertTrue(impl.contains("NoticePo::getNoticeTitle"), impl);
        // create_time 是时间列，按区间查；Query 必须自带 import，否则生成的代码编译不过
        assertTrue(query.contains("private LocalDateTime createTimeBegin;"), query);
        assertTrue(query.contains("private LocalDateTime createTimeEnd;"), query);
        assertTrue(query.contains("import java.time.LocalDateTime;"), query);
        assertTrue(impl.contains("wrapper.between("), impl);
    }

    /** 模拟使用者在配置页把某一列勾成查询字段 */
    private GenTable withQueryColumn(GenTable table, String javaField) {
        table.getColumns().stream()
                .filter(c -> javaField.equals(c.getJavaField()))
                .forEach(c -> c.setIsQuery(1));
        return table;
    }

    @Test
    @DisplayName("生成的 api.ts 里 TS 模板字面量必须真的插值，而不是漏出 Velocity 转义写法")
    void shouldEmitRealTemplateLiteralInApi() {
        String api = fileEndingWith(render(GenFixtures.sysNotice()), "notice.ts");

        // ${'$'} 不是合法的 Velocity 引用，会被原样输出，导致请求打到字面量 URL 上
        assertFalse(api.contains("$'"), api);
        assertTrue(api.contains("`/demo/notice/${id}`"), api);
        assertTrue(api.contains("`/demo/notice/remove/${id}`"), api);
    }

    @Test
    @DisplayName("模板渲染失败必须抛出并带上失败模板名，不能静默少文件")
    void shouldFailLoudlyWhenColumnsMissing() {
        GenTable table = GenFixtures.sysNotice();
        table.setColumns(List.of());

        BusinessException ex = assertThrows(BusinessException.class, () -> helper.renderTemplates(table));
        assertTrue(ex.getMessage().contains("没有列信息"), ex.getMessage());
    }

    @Test
    @DisplayName("功能名/包名为空或非法时给出明确报错，而不是 NPE / 越界")
    void shouldRejectInvalidNaming() {
        GenTable blankFunction = GenFixtures.sysNotice();
        blankFunction.setFunctionName("");
        assertTrue(assertThrows(BusinessException.class, () -> helper.renderTemplates(blankFunction))
                .getMessage().contains("功能名"));

        GenTable nullPackage = GenFixtures.sysNotice();
        nullPackage.setPackageName(null);
        assertTrue(assertThrows(BusinessException.class, () -> helper.renderTemplates(nullPackage))
                .getMessage().contains("包名"));

        GenTable chinesePackage = GenFixtures.sysNotice();
        chinesePackage.setPackageName("com.han.系统");
        assertThrows(BusinessException.class, () -> helper.renderTemplates(chinesePackage));
    }

    @Test
    @DisplayName("路径穿越的模块名被拒绝，ZIP 条目不会逃出解压目录")
    void shouldRejectZipSlip() {
        GenTable traversal = GenFixtures.sysNotice();
        traversal.setModuleName("../../../../Windows/System32");
        assertTrue(assertThrows(BusinessException.class, () -> helper.renderTemplates(traversal))
                .getMessage().contains("模块名"));

        // 即使绕过前置校验直接投喂条目名，打包出口仍要拦住
        Map<String, String> evil = new LinkedHashMap<>();
        evil.put("../../evil.java", "x");
        assertThrows(BusinessException.class, () -> helper.toZipBytes(evil, GenFixtures.sysNotice()));

        Map<String, String> absolute = new LinkedHashMap<>();
        absolute.put("/etc/passwd", "x");
        assertThrows(BusinessException.class, () -> helper.toZipBytes(absolute, GenFixtures.sysNotice()));
    }

    @Test
    @DisplayName("正常打包的 ZIP 条目全部是相对路径且能读回")
    void shouldPackageSafeZip() throws Exception {
        GenTable table = GenFixtures.sysNotice();
        Map<String, String> files = helper.renderTemplates(table);
        byte[] zip = assertDoesNotThrow(() -> helper.toZipBytes(files, table));

        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                assertFalse(entry.getName().startsWith("/"), entry.getName());
                assertFalse(entry.getName().contains(".."), entry.getName());
                assertFalse(entry.getName().contains("\\"), entry.getName());
                count++;
            }
        }
        assertEquals(11, count);
    }

    @Test
    @DisplayName("列注释里的引号与注释结束符不会截断生成代码")
    void shouldEscapeHostileColumnComment() {
        GenTable table = GenFixtures.sysNotice();
        GenTableColumn title = table.getColumns().stream()
                .filter(c -> "noticeTitle".equals(c.getJavaField()))
                .findFirst().orElseThrow();
        title.setColumnComment("标题*/ evil \" text");

        Map<String, String> files = helper.renderTemplates(table);
        String po = fileEndingWith(files, "NoticePo.java");
        String dto = fileEndingWith(files, "NoticeDto.java");

        assertFalse(po.contains("*/ evil"), po);
        assertTrue(dto.contains("\\\""), dto);
    }

    @Test
    @DisplayName("表注释里的单引号不会注入到生成的菜单 SQL")
    void shouldEscapeSqlLiteral() {
        GenTable table = GenFixtures.sysNotice();
        table.setTableComment("公告'); DROP TABLE sys_menu; --");

        String sql = fileEndingWith(helper.renderTemplates(table), "_menu.sql");
        // 表注释整体留在一个 SQL 字符串字面量里：单引号被翻倍，语句没有被提前闭合
        assertTrue(sql.contains("'公告''); DROP TABLE sys_menu; --'"), sql);
        assertFalse(sql.contains("v_ancestors, '公告');"), sql);
    }

    @Test
    @DisplayName("产物落盘供人工核对")
    void shouldDumpArtifactsForManualReview() {
        render(GenFixtures.sysNotice());
        render(GenFixtures.sysNoticeRead());
        render(GenFixtures.genTable());
        render(GenFixtures.sysUserRole());
        render(GenFixtures.customPkTable());
        // BETWEEN 走的是另一条渲染分支，单独落一份，避免被默认配置下的产物覆盖
        GenTable rangeQuery = withQueryColumn(GenFixtures.sysNotice(), "createTime");
        rangeQuery.setTableName("sys_notice_range_query");
        render(rangeQuery);
        assertNotNull(DUMP_ROOT);
    }
}
