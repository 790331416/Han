package com.han.starter.storage;

import com.han.starter.storage.config.StorageRuntimeConfig;
import com.han.starter.storage.impl.RustFSStorageProvider;

import java.io.Closeable;

/**
 * {@link StorageProvider} 的统一出口。
 *
 * <p>Provider 持有 S3 客户端（自带连接池与线程），必须只有一处负责创建、复用与释放。
 * 各调用方一律通过本工厂获取，不要再自行 {@code new} 实现类或另建一份缓存 ——
 * 那样同一份配置会被实例化多次，且谁都不负责关闭。
 */
public class StorageProviderFactory implements Closeable {

    private final StorageProviderCache cache;
    private final boolean autoCreateBucket;

    public StorageProviderFactory(int cacheSize, boolean autoCreateBucket) {
        this.cache = new StorageProviderCache(cacheSize);
        this.autoCreateBucket = autoCreateBucket;
    }

    /**
     * 按运行期配置取 Provider，同一份配置复用同一个实例。
     *
     * @param runtimeConfig 运行期存储配置
     * @return Provider 实例
     */
    public StorageProvider get(StorageRuntimeConfig runtimeConfig) {
        return cache.get(runtimeConfig.signature(), () -> new RustFSStorageProvider(runtimeConfig, autoCreateBucket));
    }

    @Override
    public void close() {
        cache.clear();
    }
}
