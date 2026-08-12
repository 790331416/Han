package com.han.common.mybatis.interceptor;

import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.support.StubSecurityContext;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HanTenantLineInnerInterceptorTest {

    private static final long TENANT_A = 100L;

    @Test
    void ordinaryTableKeepsPlainTenantCondition() {
        TenantProperties properties = new TenantProperties();
        Expression expression = interceptor(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L))
                .buildTableExpression(aliased("ai_agent", "a"), null, "");

        assertThat(expression).hasToString("a.tenant_id = 100");
    }

    @Test
    void sharedTableAlsoMatchesPlatformSharedRows() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("sys_config"));
        Expression expression = interceptor(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L))
                .buildTableExpression(aliased("sys_config", "c"), null, "");

        // 必须带括号，否则与其他条件 AND 拼接时会被 OR 拆开
        assertThat(expression).hasToString("(c.tenant_id = 100 OR c.tenant_id IS NULL)");
    }

    @Test
    void sharedTableSupportsZeroAsSharedTenant() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("ai_model"));
        properties.getShared().setTenantIds(List.of(0L));
        Expression expression = interceptor(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L))
                .buildTableExpression(aliased("ai_model", "m"), null, "");

        assertThat(expression)
                .hasToString("(m.tenant_id = 100 OR m.tenant_id IS NULL OR m.tenant_id = 0)");
    }

    @Test
    void excludedTableProducesNoCondition() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("sys_menu"));
        Expression expression = interceptor(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L))
                .buildTableExpression(aliased("sys_menu", "m"), null, "");

        assertThat(expression).isNull();
    }

    @Test
    void missingTenantContextKeepsFailOpenSoNoConditionIsBuilt() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("sys_config"));
        Expression expression = interceptor(properties, StubSecurityContext.anonymous())
                .buildTableExpression(aliased("sys_config", "c"), null, "");

        assertThat(expression).isNull();
    }

    private HanTenantLineInnerInterceptor interceptor(TenantProperties properties, StubSecurityContext securityContext) {
        return new HanTenantLineInnerInterceptor(
                new HanTenantLineHandler(properties, securityContext, new MissingTenantContextRecorder(false, 0L)));
    }

    private Table aliased(String name, String alias) {
        Table table = new Table(name);
        table.setAlias(new Alias(alias, false));
        return table;
    }
}
