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
        StubSecurityContext securityContext = StubSecurityContext.tenantUser(100L, 1001L);
        MybatisPlusConfig config = new MybatisPlusConfig(securityContext);
        TenantProperties properties = new TenantProperties();

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(properties,
                new HanTenantLineHandler(properties, securityContext, new MissingTenantContextRecorder(false, 0L)),
                new HanDataPermissionHandler(securityContext));

        // 顺序错会导致分页 count 语句拿不到租户与数据权限条件
        assertThat(interceptor.getInterceptors())
                .extracting(InnerInterceptor::getClass)
                .containsExactly(
                        HanTenantLineInnerInterceptor.class,
                        DataPermissionInterceptor.class,
                        PaginationInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class);
    }

    @Test
    void tenantInterceptorIsSkippedWhenMultiTenancyDisabled() {
        StubSecurityContext securityContext = StubSecurityContext.tenantUser(100L, 1001L);
        MybatisPlusConfig config = new MybatisPlusConfig(securityContext);
        TenantProperties properties = new TenantProperties();
        properties.setEnable(false);

        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor(properties,
                new HanTenantLineHandler(properties, securityContext, new MissingTenantContextRecorder(false, 0L)),
                new HanDataPermissionHandler(securityContext));

        List<InnerInterceptor> interceptors = interceptor.getInterceptors();
        assertThat(interceptors).noneMatch(HanTenantLineInnerInterceptor.class::isInstance);
        assertThat(interceptors).extracting(InnerInterceptor::getClass)
                .containsExactly(
                        DataPermissionInterceptor.class,
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
}
