package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.domain.entity.TenantEntity;
import com.han.common.mybatis.support.StubSecurityContext;
import com.han.common.tenant.enums.MissingTenantContextStrategy;
import com.han.common.tenant.exception.MissingTenantContextException;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HanMetaObjectHandlerTest {

    private static final long TENANT_A = 100L;

    @BeforeAll
    static void registerTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.han.common.mybatis.handler.HanMetaObjectHandlerTest");
        TableInfoHelper.initTableInfo(assistant, TenantScopedEntity.class);
        TableInfoHelper.initTableInfo(assistant, ExcludedEntity.class);
    }

    @Test
    void fillsTenantIdWhenTenantContextPresent() {
        TenantScopedEntity entity = new TenantScopedEntity();
        handler(new TenantProperties(), StubSecurityContext.tenantUser(TENANT_A, 1001L), recorder())
                .insertFill(metaObject(entity));

        assertThat(entity.getTenantId()).isEqualTo(TENANT_A);
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void doesNotOverrideExplicitTenantId() {
        TenantScopedEntity entity = new TenantScopedEntity();
        entity.setTenantId(200L);
        handler(new TenantProperties(), StubSecurityContext.tenantUser(TENANT_A, 1001L), recorder())
                .insertFill(metaObject(entity));

        assertThat(entity.getTenantId()).isEqualTo(200L);
    }

    @Test
    void keepsCurrentBehaviourButRecordsWhenTenantContextMissing() {
        MissingTenantContextRecorder recorder = recorder();
        TenantScopedEntity entity = new TenantScopedEntity();

        handler(new TenantProperties(), StubSecurityContext.anonymous(), recorder).insertFill(metaObject(entity));

        // 默认策略下行为不变：仍然落库，但被观测到
        assertThat(entity.getTenantId()).isNull();
        assertThat(recorder.snapshot()).hasSize(1);
        assertThat(recorder.snapshot().getFirst().operation()).isEqualTo("INSERT");
        assertThat(recorder.snapshot().getFirst().tableName()).isEqualTo("ai_agent");
    }

    @Test
    void excludedTableInsertIsNotReportedAsMissingContext() {
        MissingTenantContextRecorder recorder = recorder();
        ExcludedEntity entity = new ExcludedEntity();

        handler(new TenantProperties(), StubSecurityContext.anonymous(), recorder).insertFill(metaObject(entity));

        assertThat(recorder.snapshot()).isEmpty();
    }

    @Test
    void explicitTenantIgnoreIsNotReportedAsMissingContext() {
        MissingTenantContextRecorder recorder = recorder();
        TenantScopedEntity entity = new TenantScopedEntity();

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {
            handler(new TenantProperties(), StubSecurityContext.anonymous(), recorder).insertFill(metaObject(entity));
        } finally {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }

        assertThat(recorder.snapshot()).isEmpty();
    }

    @Test
    void rejectStrategyRefusesOrphanInsert() {
        TenantProperties properties = new TenantProperties();
        properties.setMissingContext(MissingTenantContextStrategy.REJECT);
        HanMetaObjectHandler handler = handler(properties, StubSecurityContext.anonymous(), recorder());
        MetaObject metaObject = metaObject(new TenantScopedEntity());

        assertThatThrownBy(() -> handler.insertFill(metaObject))
                .isInstanceOf(MissingTenantContextException.class)
                .hasMessageContaining("ai_agent");
    }

    @Test
    void updateFillRefreshesUpdateTime() {
        TenantScopedEntity entity = new TenantScopedEntity();
        handler(new TenantProperties(), StubSecurityContext.tenantUser(TENANT_A, 1001L), recorder())
                .updateFill(metaObject(entity));

        assertThat(entity.getUpdateTime()).isNotNull();
    }

    private HanMetaObjectHandler handler(TenantProperties properties,
                                         StubSecurityContext securityContext,
                                         MissingTenantContextRecorder recorder) {
        HanTenantLineHandler tenantLineHandler = new HanTenantLineHandler(properties, securityContext,
                new MissingTenantContextRecorder(false, 0L));
        return new HanMetaObjectHandler(securityContext, properties, tenantLineHandler, recorder);
    }

    private MissingTenantContextRecorder recorder() {
        return new MissingTenantContextRecorder(true, 0L);
    }

    private MetaObject metaObject(Object entity) {
        return SystemMetaObject.forObject(entity);
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableName("ai_agent")
    static class TenantScopedEntity extends TenantEntity {
        private String name;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableName("sys_login_log")
    static class ExcludedEntity extends TenantEntity {
        private String name;
    }
}
