package com.han.starter.storage.config;

import java.util.Optional;

/**
 * 运行期存储配置的解析仓储。
 */
public interface StorageConfigRepository {

    /**
     * 解析当前启用的存储配置记录。
     *
     * @return active record
     */
    Optional<StorageConfigRecord> findActiveRecord();

    /**
     * 按定位符解析存储配置记录。
     *
     * @param locator record locator
     * @return matched record
     */
    Optional<StorageConfigRecord> findRecord(String locator);

    /**
     * 解析当前生效的运行期配置。
     *
     * @return active runtime configuration
     */
    default Optional<StorageRuntimeConfig> findActiveConfig() {
        return findActiveRecord().map(StorageConfigRecord::getRuntimeConfig);
    }
}
