package com.han.starter.storage.config;

import com.han.common.core.context.SecurityContext;
import com.han.starter.storage.DynamicStorageProvider;
import com.han.starter.storage.StorageProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnExpression("'${han.storage.type:s3}' == 's3' || '${han.storage.type:s3}' == 'rustfs'")
    @ConditionalOnMissingBean
    public StorageConfigRepository storageConfigRepository(Environment environment,
                                                           ObjectProvider<SecurityContext> securityContextProvider,
                                                           StorageProperties properties) {
        StorageDatabaseProperties databaseProperties = StorageDatabaseProperties.fromEnvironment(environment);
        if (!databaseProperties.isConfigured()) {
            return null;
        }
        return new JdbcStorageConfigRepository(databaseProperties, securityContextProvider.getIfAvailable(), properties.getMasterKey());
    }

    @Bean
    @ConditionalOnExpression("'${han.storage.type:s3}' == 's3' || '${han.storage.type:s3}' == 'rustfs'")
    @ConditionalOnMissingBean
    public StorageProvider rustFSStorageProvider(StorageProperties properties,
                                                 ObjectProvider<StorageConfigRepository> configRepositoryProvider) {
        StorageRuntimeConfig fallbackConfig = StorageRuntimeConfig.fromProperties(properties.getRustfs());
        StorageConfigRepository configRepository = configRepositoryProvider.getIfAvailable();
        return new DynamicStorageProvider(fallbackConfig, configRepository);
    }
}
