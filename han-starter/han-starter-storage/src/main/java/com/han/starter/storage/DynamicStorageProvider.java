package com.han.starter.storage;

import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;

import java.io.Closeable;
import java.io.InputStream;

/**
 * Storage provider that prefers database-backed active config and falls back to static config.
 */
public class DynamicStorageProvider implements StorageProvider, Closeable {

    private final StorageRuntimeConfig fallbackConfig;
    private final StorageConfigRepository configRepository;
    private final StorageProviderCache providerCache;

    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig, StorageConfigRepository configRepository) {
        this(fallbackConfig, configRepository, 16);
    }

    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig,
                                  StorageConfigRepository configRepository,
                                  int providerCacheSize) {
        this.fallbackConfig = fallbackConfig;
        this.configRepository = configRepository;
        this.providerCache = new StorageProviderCache(providerCacheSize);
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
    public String upload(String path, InputStream stream, String contentType, Long contentLength) {
        return resolveProvider().upload(path, stream, contentType, contentLength);
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

    @Override
    public void close() {
        providerCache.clear();
    }

    private StorageProvider resolveProvider() {
        StorageRuntimeConfig runtimeConfig = configRepository == null
                ? fallbackConfig
                : configRepository.findActiveConfig().orElse(fallbackConfig);
        return providerCache.get(runtimeConfig.signature(), () -> new RustFSStorageProvider(runtimeConfig));
    }
}
