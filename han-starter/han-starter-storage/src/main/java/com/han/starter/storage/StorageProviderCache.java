package com.han.starter.storage;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 有上界的 {@link StorageProvider} 缓存。
 *
 * <p>按最近最少使用淘汰，淘汰时关闭底层客户端；无上界的缓存会随租户/配置数量增长
 * 持续堆积不会释放的 S3 客户端与连接池。
 */
public final class StorageProviderCache {

    private final Map<String, StorageProvider> cache;

    public StorageProviderCache(int maxSize) {
        final int limit = maxSize > 0 ? maxSize : 16;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, StorageProvider> eldest) {
                if (size() <= limit) {
                    return false;
                }
                closeQuietly(eldest.getValue());
                return true;
            }
        };
    }

    /**
     * 取缓存的 Provider，缺失时用工厂创建并放入缓存。
     *
     * @param key     缓存键（配置签名）
     * @param factory Provider 工厂
     * @return Provider 实例
     */
    public StorageProvider get(String key, Supplier<StorageProvider> factory) {
        synchronized (cache) {
            StorageProvider provider = cache.get(key);
            if (provider == null) {
                provider = factory.get();
                cache.put(key, provider);
            }
            return provider;
        }
    }

    /**
     * 清空缓存并关闭全部 Provider。
     */
    public void clear() {
        synchronized (cache) {
            cache.values().forEach(StorageProviderCache::closeQuietly);
            cache.clear();
        }
    }

    private static void closeQuietly(StorageProvider provider) {
        if (provider instanceof Closeable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
        }
    }
}
