package com.han.starter.storage.config;

import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.impl.RustFSStorageProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "han.storage.type", havingValue = "rustfs", matchIfMissing = true)
    @ConditionalOnMissingBean
    public StorageProvider rustFSStorageProvider(StorageProperties properties) {
        return new RustFSStorageProvider(properties.getRustfs());
    }
}
