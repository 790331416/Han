package com.han.starter.storage.config;

import java.util.Optional;

/**
 * Repository for resolving runtime storage configuration.
 */
public interface StorageConfigRepository {

    Optional<StorageRuntimeConfig> findActiveConfig();
}
