package com.han.common.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.handler.HanMetaObjectHandler;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.helper.TenantHelper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Properties;

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
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantProperties tenantProperties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（必须放在第一位）
        if (Boolean.TRUE.equals(tenantProperties.getEnable())) {
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor(tenantProperties, securityContext));
        }

        // 分页插件
        interceptor.addInnerInterceptor(paginationInnerInterceptor());

        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    /**
     * 多租户拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = "tenant", name = "enable", havingValue = "true", matchIfMissing = true)
    public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties tenantProperties, SecurityContext securityContext) {
        return new TenantLineInnerInterceptor(new HanTenantLineHandler(tenantProperties, securityContext));
    }

    /**
     * 分页插件。数据库方言由 MyBatis-Plus 根据 JDBC URL 自动识别。
     */
    private PaginationInnerInterceptor paginationInnerInterceptor() {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor();
        // 溢出总页数后是否处理（默认回到第一页）
        interceptor.setOverflow(true);
        // 单页分页条数限制（默认无限制，-1 不限制）
        interceptor.setMaxLimit(500L);
        return interceptor;
    }

    /**
     * 为少量数据库元数据 SQL 提供稳定的 PostgreSQL/MySQL 标识。
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("MySQL", "mysql");
        provider.setProperties(properties);
        return provider;
    }

    /**
     * 审计字段自动填充
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new HanMetaObjectHandler(securityContext);
    }
}
