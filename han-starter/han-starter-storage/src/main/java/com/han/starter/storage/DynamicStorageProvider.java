package com.han.starter.storage;

import com.han.starter.storage.config.StorageConfigRepository;
import com.han.starter.storage.config.StorageRuntimeConfig;

import java.io.Closeable;
import java.io.InputStream;

/**
 * Storage provider that prefers database-backed active config and falls back to static config.
 */
public class DynamicStorageProvider implements StorageProvider, Closeable {

    private final StorageRuntimeConfig fallbackConfig;
    private final StorageConfigRepository configRepository;
    private final StorageProviderFactory providerFactory;
    private final boolean ownsFactory;

    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig, StorageConfigRepository configRepository) {
        this(fallbackConfig, configRepository, 16);
    }

    /**
     * 自建工厂：调用方没有共享工厂可用时使用，本实例负责在 {@link #close()} 时释放。
     */
    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig,
                                  StorageConfigRepository configRepository,
                                  int providerCacheSize) {
        this(fallbackConfig, configRepository, new StorageProviderFactory(providerCacheSize, true), true);
    }

    /**
     * 复用共享工厂：Provider 的创建与释放统一由工厂负责，本实例不参与关闭。
     */
    public DynamicStorageProvider(StorageRuntimeConfig fallbackConfig,
                                  StorageConfigRepository configRepository,
                                  StorageProviderFactory providerFactory) {
        this(fallbackConfig, configRepository, providerFactory, false);
    }

    private DynamicStorageProvider(StorageRuntimeConfig fallbackConfig,
                                   StorageConfigRepository configRepository,
                                   StorageProviderFactory providerFactory,
                                   boolean ownsFactory) {
        this.fallbackConfig = fallbackConfig;
        this.configRepository = configRepository;
        this.providerFactory = providerFactory;
        this.ownsFactory = ownsFactory;
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
        // 工厂是共享组件时由容器负责关闭，这里只关自己独占的那一份
        if (ownsFactory) {
            providerFactory.close();
        }
    }

    private StorageProvider resolveProvider() {
        StorageRuntimeConfig runtimeConfig = configRepository == null
                ? fallbackConfig
                : configRepository.findActiveConfig().orElse(fallbackConfig);
        return providerFactory.get(runtimeConfig);
    }
}
