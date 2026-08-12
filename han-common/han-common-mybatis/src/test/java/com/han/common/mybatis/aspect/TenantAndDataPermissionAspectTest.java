package com.han.common.mybatis.aspect;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.han.common.mybatis.annotation.DataPermission;
import com.han.common.mybatis.context.DataPermissionContextHolder;
import com.han.common.tenant.annotation.IgnoreTenant;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantAndDataPermissionAspectTest {

    @Test
    void ignoreTenantAspectOpensAndClosesTheIgnoreWindow() {
        SampleService service = ignoreTenantProxy(new SampleService());

        assertThat(service.crossTenantQuery()).isTrue();
        assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isFalse();
    }

    @Test
    void ignoreTenantAspectClearsStrategyOnException() {
        SampleService service = ignoreTenantProxy(new SampleService());

        assertThatThrownBy(service::crossTenantFailure).isInstanceOf(IllegalStateException.class);
        assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isFalse();
    }

    @Test
    void dataPermissionAspectPublishesAndRestoresDeclaration() {
        SampleService service = dataPermissionProxy(new SampleService());

        assertThat(service.scopedQuery()).isNotNull();
        assertThat(DataPermissionContextHolder.get()).isNull();
    }

    @Test
    void dataPermissionAspectRestoresDeclarationOnException() {
        SampleService service = dataPermissionProxy(new SampleService());

        assertThatThrownBy(service::scopedFailure).isInstanceOf(IllegalStateException.class);
        assertThat(DataPermissionContextHolder.get()).isNull();
    }

    @Test
    void dataPermissionContextSupportsNesting() {
        DataPermission outer = declaration("outer");
        DataPermission inner = declaration("inner");

        DataPermissionContextHolder.push(outer);
        DataPermissionContextHolder.push(inner);
        assertThat(DataPermissionContextHolder.get()).isSameAs(inner);

        DataPermissionContextHolder.poll();
        assertThat(DataPermissionContextHolder.get()).isSameAs(outer);

        DataPermissionContextHolder.poll();
        assertThat(DataPermissionContextHolder.get()).isNull();
    }

    private SampleService ignoreTenantProxy(SampleService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new IgnoreTenantAspect());
        return factory.getProxy();
    }

    private SampleService dataPermissionProxy(SampleService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new DataPermissionAspect());
        return factory.getProxy();
    }

    private DataPermission declaration(String alias) {
        try {
            return alias.equals("outer")
                    ? SampleService.class.getMethod("scopedQuery").getAnnotation(DataPermission.class)
                    : SampleService.class.getMethod("scopedFailure").getAnnotation(DataPermission.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class SampleService {

        @IgnoreTenant("登录时租户未知，必须跨租户查找候选用户")
        public boolean crossTenantQuery() {
            return InterceptorIgnoreHelper.hasIgnoreStrategy();
        }

        @IgnoreTenant("测试异常路径")
        public void crossTenantFailure() {
            throw new IllegalStateException("boom");
        }

        @DataPermission
        public DataPermission scopedQuery() {
            return DataPermissionContextHolder.get();
        }

        @DataPermission(deptAlias = "dept")
        public void scopedFailure() {
            throw new IllegalStateException("boom");
        }
    }
}
