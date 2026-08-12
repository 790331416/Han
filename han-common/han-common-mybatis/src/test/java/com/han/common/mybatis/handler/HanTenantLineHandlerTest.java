package com.han.common.mybatis.handler;

import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.support.StubSecurityContext;
import com.han.common.tenant.enums.MissingTenantContextStrategy;
import com.han.common.tenant.exception.MissingTenantContextException;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import com.han.common.tenant.observe.MissingTenantContextSample;
import com.han.sample.caller.SampleBusinessCaller;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HanTenantLineHandlerTest {

    private static final long TENANT_A = 100L;

    @Test
    void injectsTenantConditionWhenTenantContextPresent() {
        HanTenantLineHandler handler = handler(new TenantProperties(), StubSecurityContext.tenantUser(TENANT_A, 1001L));

        assertThat(handler.ignoreTable("ai_agent")).isFalse();
        assertThat(handler.getTenantId()).isInstanceOf(LongValue.class);
        assertThat(((LongValue) handler.getTenantId()).getValue()).isEqualTo(TENANT_A);
    }

    @Test
    void keepsFailOpenByDefaultWhenTenantContextMissing() {
        TenantProperties properties = new TenantProperties();
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 0L);
        HanTenantLineHandler handler = new HanTenantLineHandler(properties, StubSecurityContext.anonymous(), recorder);

        // 本轮明确不翻转 fail-open：拿不到租户时依然跳过过滤
        assertThat(properties.getMissingContext()).isEqualTo(MissingTenantContextStrategy.IGNORE);
        assertThat(handler.ignoreTable("ai_agent")).isTrue();
    }

    @Test
    void recordsObservableSampleWhenTenantContextMissing() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 0L);
        HanTenantLineHandler handler = new HanTenantLineHandler(new TenantProperties(),
                StubSecurityContext.anonymous(), recorder);

        SampleBusinessCaller.queryTenantScopedTable(handler, "ai_agent");
        SampleBusinessCaller.queryTenantScopedTable(handler, "ai_agent");

        List<MissingTenantContextSample> samples = recorder.snapshot();
        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().operation()).isEqualTo("SQL");
        assertThat(samples.getFirst().tableName()).isEqualTo("ai_agent");
        assertThat(samples.getFirst().count()).isEqualTo(2L);
        // 调用点必须能定位到业务代码，否则运维无法统计"来自哪里"
        assertThat(samples.getFirst().callSite())
                .contains(SampleBusinessCaller.class.getName())
                .contains(SampleBusinessCaller.QUERY_METHOD);
    }

    @Test
    void excludedTablesAreNotObservedBecauseTheyNeverNeedTenantFilter() {
        MissingTenantContextRecorder recorder = new MissingTenantContextRecorder(true, 0L);
        HanTenantLineHandler handler = new HanTenantLineHandler(new TenantProperties(),
                StubSecurityContext.anonymous(), recorder);

        handler.ignoreTable("sys_login_log");

        assertThat(recorder.snapshot()).isEmpty();
    }

    @Test
    void filterStrategyInjectsNeverMatchingCondition() {
        TenantProperties properties = new TenantProperties();
        properties.setMissingContext(MissingTenantContextStrategy.FILTER);
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.anonymous());

        assertThat(handler.ignoreTable("ai_agent")).isFalse();
        assertThat(handler.getTenantId()).isInstanceOf(NullValue.class);
    }

    @Test
    void rejectStrategyThrowsInsteadOfSilentlyReturningEmptyResult() {
        TenantProperties properties = new TenantProperties();
        properties.setMissingContext(MissingTenantContextStrategy.REJECT);
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.anonymous());

        assertThatThrownBy(() -> handler.ignoreTable("ai_agent"))
                .isInstanceOf(MissingTenantContextException.class)
                .hasMessageContaining("ai_agent");
    }

    @Test
    void rejectStrategyStillSkipsExcludedTables() {
        TenantProperties properties = new TenantProperties();
        properties.setMissingContext(MissingTenantContextStrategy.REJECT);
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.anonymous());

        assertThat(handler.ignoreTable("sys_menu")).isTrue();
        assertThat(handler.ignoreTable("SYS_OPER_LOG")).isTrue();
        assertThat(handler.ignoreTable("pg_class")).isTrue();
        assertThat(handler.ignoreTable("information_schema.columns")).isTrue();
    }

    @Test
    void defaultExcludeListIsUnchanged() {
        HanTenantLineHandler handler = handler(new TenantProperties(), StubSecurityContext.tenantUser(TENANT_A, 1001L));

        assertThat(List.of("sys_menu", "sys_tenant", "sys_tenant_package", "sys_tenant_package_menu",
                        "sys_oper_log", "sys_login_log", "sys_user_role", "sys_user_post",
                        "sys_role_menu", "sys_role_dept"))
                .allMatch(handler::isExcludedTable);
        assertThat(handler.isExcludedTable("ai_agent")).isFalse();
    }

    @Test
    void configuredExcludesTakeEffect() {
        TenantProperties properties = new TenantProperties();
        properties.setExcludes(List.of("sys_client"));
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L));

        assertThat(handler.ignoreTable("sys_client")).isTrue();
        assertThat(handler.ignoreTable("SYS_CLIENT")).isTrue();
    }

    @Test
    void sharedTableIsOnlyRecognizedWhenConfigured() {
        TenantProperties properties = new TenantProperties();
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L));
        assertThat(handler.isSharedTable("sys_config")).isFalse();

        properties.getShared().setTables(List.of("sys_config"));
        assertThat(handler.isSharedTable("sys_config")).isTrue();
        assertThat(handler.isSharedTable("SYS_CONFIG")).isTrue();
        assertThat(handler.isSharedTable("sys_dict_data")).isFalse();
    }

    @Test
    void sharedExpressionMatchesNullByDefault() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("sys_config"));
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L));

        assertThat(handler.buildSharedTenantExpression("c.tenant_id"))
                .hasToString("c.tenant_id IS NULL");
    }

    @Test
    void sharedExpressionSupportsExplicitSharedTenantIds() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("ai_model"));
        properties.getShared().setMatchNull(false);
        properties.getShared().setTenantIds(List.of(0L));
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.tenantUser(TENANT_A, 1001L));

        assertThat(handler.buildSharedTenantExpression("m.tenant_id"))
                .hasToString("m.tenant_id = 0");
    }

    @Test
    void sharedExpressionIsNotGrantedWithoutTenantContext() {
        TenantProperties properties = new TenantProperties();
        properties.getShared().setTables(List.of("sys_config"));
        HanTenantLineHandler handler = handler(properties, StubSecurityContext.anonymous());

        assertThat(handler.buildSharedTenantExpression("c.tenant_id")).isNull();
    }

    private HanTenantLineHandler handler(TenantProperties properties, StubSecurityContext securityContext) {
        return new HanTenantLineHandler(properties, securityContext, new MissingTenantContextRecorder(false, 0L));
    }
}
