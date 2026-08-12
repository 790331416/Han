package com.han.starter.storage.config;

import com.han.common.core.context.SecurityContext;
import com.han.starter.storage.DynamicStorageProvider;
import com.han.starter.storage.StorageProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "han.storage.type", havingValue = "rustfs", matchIfMissing = true)
    @ConditionalOnMissingBean
    public StorageConfigRepository storageConfigRepository(Environment environment,
                                                           StorageProperties properties,
                                                           ObjectProvider<SecurityContext> securityContextProvider,
                                                           ObjectProvider<DataSource> dataSourceProvider) {
        StorageDatabaseProperties databaseProperties = StorageDatabaseProperties.fromEnvironment(environment);
        // 数据源在首次查询时才解析：此处不能提前触发 DataSource 初始化，否则会打乱自动装配顺序。
        return new JdbcStorageConfigRepository(
                databaseProperties,
                securityContextProvider.getIfAvailable(),
                dataSourceProvider::getIfAvailable,
                properties.getConfigCacheSeconds() * 1000L);
    }

    @Bean
    @ConditionalOnProperty(name = "han.storage.type", havingValue = "rustfs", matchIfMissing = true)
    @ConditionalOnMissingBean
    public StorageProvider rustFSStorageProvider(StorageProperties properties,
                                                 ObjectProvider<StorageConfigRepository> configRepositoryProvider) {
        StorageRuntimeConfig fallbackConfig = StorageRuntimeConfig.fromProperties(properties.getRustfs());
        StorageConfigRepository configRepository = configRepositoryProvider.getIfAvailable();
        return new DynamicStorageProvider(fallbackConfig, configRepository, properties.getProviderCacheSize());
    }
}
