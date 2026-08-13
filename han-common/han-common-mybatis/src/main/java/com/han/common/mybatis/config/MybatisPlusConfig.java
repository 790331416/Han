package com.han.common.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.handler.HanDataPermissionHandler;
import com.han.common.mybatis.handler.HanMetaObjectHandler;
import com.han.common.mybatis.handler.HanTenantLineHandler;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.mybatis.interceptor.HanTenantLineInnerInterceptor;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
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
 * 配置分页插件、多租户拦截器、数据权限拦截器、乐观锁插件、审计字段自动填充。
 * <p>
 * 插件顺序有硬性要求：所有会改写 SQL 的拦截器（多租户、数据权限）必须排在分页插件之前。
 * 分页插件在 {@code willDoQuery} 阶段用当前 SQL 生成 count 语句，排在它后面的改写
 * 拿不进 count 语句，会出现「列表按租户过滤、总数却是全平台」这类问题。
 */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties({TenantProperties.class, DataPermissionProperties.class})
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
                                                         DataPermissionProperties dataPermissionProperties,
                                                         HanTenantLineHandler hanTenantLineHandler,
                                                         HanDataPermissionHandler hanDataPermissionHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（必须放在第一位）
        if (Boolean.TRUE.equals(tenantProperties.getEnable())) {
            interceptor.addInnerInterceptor(tenantLineInnerInterceptor(hanTenantLineHandler));
        }

        // 数据权限插件（必须在分页插件之前，否则 count 语句拿不到数据范围条件）
        if (Boolean.TRUE.equals(dataPermissionProperties.getEnable())) {
            interceptor.addInnerInterceptor(dataPermissionInterceptor(hanDataPermissionHandler));
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
     * 数据权限处理器
     */
    @Bean
    public HanDataPermissionHandler hanDataPermissionHandler() {
        return new HanDataPermissionHandler(securityContext);
    }

    /**
     * 数据权限拦截器
     * <p>
     * 只对标注了 {@code @DataPermission} 的语句生效，未标注的语句不会产生任何额外条件。
     */
    @Bean
    @ConditionalOnProperty(prefix = "data-permission", name = "enable", havingValue = "true", matchIfMissing = true)
    public DataPermissionInterceptor dataPermissionInterceptor(HanDataPermissionHandler hanDataPermissionHandler) {
        return new DataPermissionInterceptor(hanDataPermissionHandler);
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
    public MetaObjectHandler metaObjectHandler(TenantProperties tenantProperties,
                                               HanTenantLineHandler hanTenantLineHandler,
                                               MissingTenantContextRecorder missingTenantContextRecorder) {
        return new HanMetaObjectHandler(securityContext, tenantProperties, hanTenantLineHandler,
                missingTenantContextRecorder);
    }
}
