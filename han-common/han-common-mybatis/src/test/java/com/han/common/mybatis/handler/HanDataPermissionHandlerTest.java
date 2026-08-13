package com.han.common.mybatis.handler;

import com.han.common.mybatis.annotation.DataPermission;
import com.han.common.mybatis.context.DataPermissionContextHolder;
import com.han.common.mybatis.support.StubSecurityContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HanDataPermissionHandlerTest {

    private static final String PLAIN_STATEMENT =
            "com.han.common.mybatis.handler.HanDataPermissionHandlerTest$PlainMapper.selectList";
    private static final String SCOPED_STATEMENT =
            "com.han.common.mybatis.handler.HanDataPermissionHandlerTest$ScopedMapper.selectList";
    private static final String SCOPED_COUNT_STATEMENT = SCOPED_STATEMENT + "_mpCount";

    @AfterEach
    void clearContext() {
        DataPermissionContextHolder.clear();
    }

    @Test
    void statementWithoutAnnotationProducesNoExtraCondition() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of(10L)));

        assertThat(handler.getSqlSegment(existingWhere(), PLAIN_STATEMENT)).isNull();
        assertThat(handler.getSqlSegment(null, PLAIN_STATEMENT)).isNull();
    }

    @Test
    void unknownStatementIsTreatedAsUnannotated() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of(10L)));

        assertThat(handler.getSqlSegment(null, "com.han.not.Exists.selectList")).isNull();
        assertThat(handler.getSqlSegment(null, "noDot")).isNull();
        assertThat(handler.getSqlSegment(null, null)).isNull();
    }

    @Test
    void deptScopeAppendsDeptAndSelfCondition() {
        Set<Long> deptIds = new LinkedHashSet<>(java.util.List.of(10L, 20L));
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(deptIds));

        Expression expression = handler.getSqlSegment(existingWhere(), SCOPED_STATEMENT);

        assertThat(expression).hasToString("u.status = 0 AND (d.dept_id IN (10, 20) OR u.create_by = 1001)");
    }

    @Test
    void countStatementResolvesTheSameAnnotation() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of(10L)));

        assertThat(handler.getSqlSegment(null, SCOPED_COUNT_STATEMENT))
                .hasToString("(d.dept_id IN (10) OR u.create_by = 1001)");
    }

    @Test
    void selfOnlyScopeFiltersByCreator() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of()));

        assertThat(handler.getSqlSegment(null, SCOPED_STATEMENT)).hasToString("(u.create_by = 1001)");
    }

    @Test
    void allScopeProducesNoCondition() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(null));

        assertThat(handler.getSqlSegment(existingWhere(), SCOPED_STATEMENT)).isNull();
    }

    @Test
    void adminIsNotRestricted() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1L).withAdmin(true).withDataScopeDeptIds(Set.of(10L)));

        assertThat(handler.getSqlSegment(existingWhere(), SCOPED_STATEMENT)).isNull();
    }

    @Test
    void anonymousAccessToAnnotatedStatementIsRejectedInsteadOfPassedThrough() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(StubSecurityContext.anonymous());

        assertThat(handler.getSqlSegment(null, SCOPED_STATEMENT)).hasToString("1 = 0");
    }

    @Test
    void serviceLevelDeclarationOverridesMapperLookup() throws Exception {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of()));
        DataPermission declared = ScopedMapper.class.getMethod("selectList").getAnnotation(DataPermission.class);

        DataPermissionContextHolder.push(declared);
        try {
            assertThat(handler.getSqlSegment(null, PLAIN_STATEMENT)).hasToString("(u.create_by = 1001)");
        } finally {
            DataPermissionContextHolder.poll();
        }

        assertThat(handler.getSqlSegment(null, PLAIN_STATEMENT)).isNull();
    }

    @Test
    void aliasCanBeOmittedForSingleTableStatements() {
        HanDataPermissionHandler handler = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of()));
        DataPermission declared = NoAliasMapper.class.getAnnotation(DataPermission.class);

        DataPermissionContextHolder.push(declared);
        try {
            assertThat(handler.getSqlSegment(null, PLAIN_STATEMENT)).hasToString("(create_by = 1001)");
        } finally {
            DataPermissionContextHolder.poll();
        }
    }

    @Test
    void legacyHelperMethodsKeepTheirContract() {
        HanDataPermissionHandler admin = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1L).withAdmin(true).withDataScopeDeptIds(Set.of(10L)));
        assertThat(admin.getDataScopeDeptIds()).isNull();
        assertThat(admin.needDataScope()).isFalse();

        HanDataPermissionHandler user = new HanDataPermissionHandler(
                StubSecurityContext.tenantUser(100L, 1001L).withDataScopeDeptIds(Set.of(10L)));
        assertThat(user.getDataScopeDeptIds()).containsExactly(10L);
        assertThat(user.needDataScope()).isTrue();
    }

    private Expression existingWhere() {
        return new EqualsTo(new Column("u.status"), new LongValue(0L));
    }

    interface PlainMapper {
        void selectList();
    }

    interface ScopedMapper {
        @DataPermission
        void selectList();
    }

    @DataPermission(deptAlias = "", userAlias = "")
    interface NoAliasMapper {
        void selectList();
    }
}
