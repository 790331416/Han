package com.han.starter.storage.config;

import java.util.Optional;

/**
 * Repository for resolving runtime storage configuration.
 */
public interface StorageConfigRepository {

    /**
     * Resolve active storage configuration record.
     *
     * @return active record
     */
    Optional<StorageConfigRecord> findActiveRecord();

    /**
     * Resolve a storage configuration record by locator.
     *
     * @param locator record locator
     * @return matched record
     */
    Optional<StorageConfigRecord> findRecord(String locator);

    /**
     * Resolve active runtime configuration.
     *
     * @return active runtime configuration
     */
    default Optional<StorageRuntimeConfig> findActiveConfig() {
        return findActiveRecord().map(StorageConfigRecord::getRuntimeConfig);
    }
}
