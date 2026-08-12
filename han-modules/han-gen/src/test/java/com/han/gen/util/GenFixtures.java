package com.han.gen.util;

import com.han.gen.domain.DbColumnInfo;
import com.han.gen.domain.GenTable;
import com.han.gen.domain.GenTableColumn;

import java.util.ArrayList;
import java.util.List;

/**
 * 用真实 DDL 构造生成器输入。
 *
 * <p>列定义逐列抄自 {@code sql/tiers/full/full-init.sql}，并且走 {@link GenUtils#toGenColumn}
 * 这条真实导入路径，保证测试覆盖的是「从库里导入表」之后的实际配置，而不是手工摆出来的理想数据。
 */
final class GenFixtures {

    private GenFixtures() {}

    /** sys_notice：Han 全套审计列 + 租户列，应当继承 BizEntity */
    static GenTable sysNotice() {
        List<DbColumnInfo> cols = new ArrayList<>();
        cols.add(col("id", "bigint", false, true, null, "公告ID"));
        cols.add(col("tenant_id", "bigint", false, false, null, "租户ID"));
        cols.add(col("notice_title", "character varying(100)", false, false, null, "公告标题"));
        cols.add(col("notice_type", "character(1)", false, false, null, "公告类型"));
        cols.add(col("notice_content", "text", true, false, null, "公告内容"));
        cols.add(col("status", "smallint", true, false, "0", "状态"));
        cols.add(col("create_by", "bigint", true, false, null, "创建者ID"));
        cols.add(col("create_name", "character varying(50)", true, false, null, "创建者名称"));
        cols.add(col("create_dept", "bigint", true, false, null, "创建部门ID"));
        cols.add(col("create_time", "timestamp without time zone", true, false, "CURRENT_TIMESTAMP", "创建时间"));
        cols.add(col("update_by", "bigint", true, false, null, "更新者ID"));
        cols.add(col("update_name", "character varying(50)", true, false, null, "更新者名称"));
        cols.add(col("update_time", "timestamp without time zone", true, false, null, "更新时间"));
        cols.add(col("del_flag", "smallint", true, false, "0", "删除标志"));
        cols.add(col("remark", "character varying(500)", true, false, null, "备注"));
        return table("sys_notice", "通知公告", "Notice", cols);
    }

    /** sys_notice_read：有租户与基础审计列，但没有 create_by/create_name/create_dept/remark，只能继承 TenantEntity */
    static GenTable sysNoticeRead() {
        List<DbColumnInfo> cols = new ArrayList<>();
        cols.add(col("id", "bigint", false, true, null, "主键"));
        cols.add(col("tenant_id", "bigint", false, false, null, "租户ID"));
        cols.add(col("notice_id", "bigint", false, false, null, "通知ID"));
        cols.add(col("user_id", "bigint", false, false, null, "用户ID"));
        cols.add(col("read_time", "timestamp without time zone", true, false, "CURRENT_TIMESTAMP", "已读时间"));
        cols.add(col("create_time", "timestamp without time zone", true, false, "CURRENT_TIMESTAMP", "创建时间"));
        cols.add(col("update_time", "timestamp without time zone", true, false, null, "更新时间"));
        cols.add(col("del_flag", "smallint", true, false, "0", "删除标志"));
        return table("sys_notice_read", "用户通知已读状态", "NoticeRead", cols);
    }

    /** gen_table：没有 del_flag，任何基类都不满足，只能不继承 */
    static GenTable genTable() {
        List<DbColumnInfo> cols = new ArrayList<>();
        cols.add(col("id", "bigint", false, true, null, "主键"));
        cols.add(col("tenant_id", "bigint", true, false, "0", "租户ID"));
        cols.add(col("table_name", "character varying(200)", false, false, "''::character varying", "表名称"));
        cols.add(col("table_comment", "character varying(500)", true, false, null, "表描述"));
        cols.add(col("create_time", "timestamp without time zone", true, false, null, "创建时间"));
        cols.add(col("update_time", "timestamp without time zone", true, false, null, "更新时间"));
        return table("gen_table", "代码生成业务表", "GenBizTable", cols);
    }

    /** sys_user_role：复合主键，既没有 id 也没有单列主键 */
    static GenTable sysUserRole() {
        List<DbColumnInfo> cols = new ArrayList<>();
        cols.add(col("user_id", "bigint", false, true, null, "用户ID"));
        cols.add(col("role_id", "bigint", false, true, null, "角色ID"));
        return table("sys_user_role", "用户角色关联", "UserRole", cols);
    }

    /** 主键不叫 id、且是自增序列的表 */
    static GenTable customPkTable() {
        List<DbColumnInfo> cols = new ArrayList<>();
        cols.add(col("project_id", "bigint", false, true, "nextval('demo_project_project_id_seq'::regclass)", "项目ID"));
        cols.add(col("project_name", "character varying(64)", false, false, null, "项目名称"));
        cols.add(col("amount", "numeric(10,2)", true, false, null, "金额"));
        cols.add(col("started_at", "timestamp without time zone", true, false, null, "开始时间"));
        return table("demo_project", "示例项目", "Project", cols);
    }

    private static DbColumnInfo col(String name, String type, boolean nullable, boolean pk,
                                    String defaultValue, String comment) {
        DbColumnInfo info = new DbColumnInfo();
        info.setColumnName(name);
        info.setColumnType(type);
        info.setIsNullable(nullable ? "YES" : "NO");
        info.setColumnKey(pk ? "PRI" : "");
        info.setColumnDefault(defaultValue);
        info.setColumnComment(comment);
        return info;
    }

    /** 与 GenTableService.importTable 一致的配置构造方式 */
    private static GenTable table(String tableName, String comment, String className, List<DbColumnInfo> cols) {
        List<GenTableColumn> genColumns = new ArrayList<>();
        int sort = 1;
        for (DbColumnInfo col : cols) {
            genColumns.add(GenUtils.toGenColumn(col, 1L, sort++));
        }
        GenTable table = GenTable.builder()
                .id(1L)
                .tableName(tableName)
                .tableComment(comment)
                .packageName("com.han.demo")
                .moduleName("demo")
                .businessName(className.substring(0, 1).toLowerCase() + className.substring(1))
                .functionName(className)
                .author("HanCloud")
                .build();
        table.setColumns(genColumns);
        return table;
    }
}
