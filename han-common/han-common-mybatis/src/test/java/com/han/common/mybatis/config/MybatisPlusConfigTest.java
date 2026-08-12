package com.han.common.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.han.common.mybatis.handler.HanDataPermissionHandler;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.interceptor.HanTenantLineInnerInterceptor;
import com.han.common.mybatis.support.StubSecurityContext;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void rewritingInterceptorsMustRunBeforePagination() {
        assertThat(interceptors(new TenantProperties(), new DataPermissionProperties()))
                .containsExactly(
                        HanTenantLineInnerInterceptor.class,
                        DataPermissionInterceptor.class,
                        PaginationInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class);
    }

    @Test
    void tenantInterceptorIsSkippedWhenMultiTenancyDisabled() {
        TenantProperties properties = new TenantProperties();
        properties.setEnable(false);

        assertThat(interceptors(properties, new DataPermissionProperties()))
                .containsExactly(
                        DataPermissionInterceptor.class,
                        PaginationInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class);
    }

    @Test
    void dataPermissionInterceptorCanBeTurnedOff() {
        DataPermissionProperties dataPermissionProperties = new DataPermissionProperties();
        dataPermissionProperties.setEnable(false);

        assertThat(interceptors(new TenantProperties(), dataPermissionProperties))
                .containsExactly(
                        HanTenantLineInnerInterceptor.class,
                        PaginationInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class);
    }

    @Test
    void observerIsEnabledByDefaultAndCanBeTurnedOff() {
        MybatisPlusConfig config = new MybatisPlusConfig(StubSecurityContext.anonymous());
        TenantProperties properties = new TenantProperties();

        assertThat(config.missingTenantContextRecorder(properties).isEnabled()).isTrue();

        properties.setObserveMissingContext(false);
        assertThat(config.missingTenantContextRecorder(properties).isEnabled()).isFalse();
    }

    private List<Class<? extends InnerInterceptor>> interceptors(TenantProperties tenantProperties,
                                                                 DataPermissionProperties dataPermissionProperties) {
        StubSecurityContext securityContext = StubSecurityContext.tenantUser(100L, 1001L);
        MybatisPlusConfig config = new MybatisPlusConfig(securityContext);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(tenantProperties, dataPermissionProperties,
                new HanTenantLineHandler(tenantProperties, securityContext, new MissingTenantContextRecorder(false, 0L)),
                new HanDataPermissionHandler(securityContext));

        return interceptor.getInterceptors().stream()
                .<Class<? extends InnerInterceptor>>map(InnerInterceptor::getClass)
                .toList();
    }
}
