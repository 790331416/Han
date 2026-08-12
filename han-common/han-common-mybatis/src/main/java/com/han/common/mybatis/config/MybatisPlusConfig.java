package com.han.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.handler.HanMetaObjectHandler;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.mybatis.interceptor.HanTenantLineInnerInterceptor;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 配置
 * <p>
 * 配置分页插件、多租户拦截器、乐观锁插件、审计字段自动填充。
 */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(TenantProperties.class)
@MapperScan("com.han.**.mapper")
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final SecurityContext securityContext;

    @PostConstruct
    public void init() {
        TenantHelper.setSecurityContext(securityContext);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantProperties tenantProperties,
                                                         HanTenantLineHandler hanTenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（必须放在第一位）
        if (Boolean.TRUE.equals(tenantProperties.getEnable())) {
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor(hanTenantLineHandler));
        }

        // 分页插件
        interceptor.addInnerInterceptor(paginationInnerInterceptor());

        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 无租户上下文观测器（默认只记录不改变行为）
     */
    @Bean
    public MissingTenantContextRecorder missingTenantContextRecorder(TenantProperties tenantProperties) {
        long interval = tenantProperties.getObserveLogIntervalMillis() == null
                ? 60_000L
                : tenantProperties.getObserveLogIntervalMillis();
        return new MissingTenantContextRecorder(
                !Boolean.FALSE.equals(tenantProperties.getObserveMissingContext()), interval);
    }

    /**
     * 多租户过滤处理器
     */
    @Bean
    public HanTenantLineHandler hanTenantLineHandler(TenantProperties tenantProperties,
                                                     MissingTenantContextRecorder missingTenantContextRecorder) {
        return new HanTenantLineHandler(tenantProperties, securityContext, missingTenantContextRecorder);
    }

    /**
     * 多租户拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = "tenant", name = "enable", havingValue = "true", matchIfMissing = true)
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(HanTenantLineHandler hanTenantLineHandler) {
        return new HanTenantLineInnerInterceptor(hanTenantLineHandler);
    }

    /**
     * 分页插件（PostgreSQL）
     */
    private PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        // 溢出总页数后是否处理（默认回到第一页）
        interceptor.setOverflow(true);
        // 单页分页条数限制（默认无限制，-1 不限制）
        interceptor.setMaxLimit(500L);
        return interceptor;
    }

    /**
     * 审计字段自动填充
     */
    @Bean
    public MetaObjectHandler metaObjectHandler(TenantProperties tenantProperties,
                                               HanTenantLineHandler hanTenantLineHandler,
                                               MissingTenantContextRecorder missingTenantContextRecorder) {
        return new HanMetaObjectHandler(securityContext, tenantProperties, hanTenantLineHandler,
                missingTenantContextRecorder);
    }
}
