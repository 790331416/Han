package com.han.starter.storage;

import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage provider that prefers database-backed active config and falls back to static config.
 */
public class DynamicStorageProvider implements StorageProvider {

    private final StorageRuntimeConfig fallbackConfig;
    private final StorageConfigRepository configRepository;
    private final Map<String, StorageProvider> providerCache = new ConcurrentHashMap<>();

    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig, StorageConfigRepository configRepository) {
        this.fallbackConfig = fallbackConfig;
        this.configRepository = configRepository;
    }

    @Override
    public String upload(String path, InputStream stream) {
        return resolveProvider().upload(path, stream);
    }

    @Override
    public String upload(String path, InputStream stream, String contentType) {
        return resolveProvider().upload(path, stream, contentType);
    }

    @Override
    public InputStream download(String path) {
        return resolveProvider().download(path);
    }

    @Override
    public void delete(String path) {
        resolveProvider().delete(path);
    }

    @Override
    public String getUrl(String path) {
        return resolveProvider().getUrl(path);
    }

    @Override
    public boolean exists(String path) {
        return resolveProvider().exists(path);
    }

    private StorageProvider resolveProvider() {
        StorageRuntimeConfig runtimeConfig = configRepository == null
                ? fallbackConfig
                : configRepository.findActiveConfig().orElse(fallbackConfig);
        return providerCache.computeIfAbsent(runtimeConfig.signature(), key -> new RustFSStorageProvider(runtimeConfig));
    }
}
