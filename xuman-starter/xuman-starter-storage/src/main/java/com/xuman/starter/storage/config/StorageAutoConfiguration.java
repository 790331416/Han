package com.xuman.starter.storage.config;

import com.xuman.starter.storage.StorageProvider;
import com.xuman.starter.storage.impl.RustFSStorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "xuman.storage.type", havingValue = "rustfs")
    @ConditionalOnMissingBean
    public StorageProvider rustFSStorageProvider(StorageProperties properties) {
        return new RustFSStorageProvider(properties.getRustfs());
    }
}
