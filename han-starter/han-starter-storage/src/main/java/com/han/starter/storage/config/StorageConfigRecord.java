package com.han.starter.storage.config;

/**
 * 带稳定定位符的存储配置记录。
 */
public final class StorageConfigRecord {

    private static final String DATABASE_PREFIX = "db-";

    private final String locator;
    private final StorageRuntimeConfig runtimeConfig;

    private StorageConfigRecord(String locator, StorageRuntimeConfig runtimeConfig) {
        this.locator = locator;
        this.runtimeConfig = runtimeConfig;
    }

    public static StorageConfigRecord fromDatabase(Long ossConfigId, StorageRuntimeConfig runtimeConfig) {
        return new StorageConfigRecord(DATABASE_PREFIX + ossConfigId, runtimeConfig);
    }

    public static StorageConfigRecord fromStatic(String configKey, StorageRuntimeConfig runtimeConfig) {
        return new StorageConfigRecord("static-" + configKey, runtimeConfig);
    }

    public String getLocator() {
        return locator;
    }

    public StorageRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public static Long parseDatabaseId(String locator) {
        if (locator == null || !locator.startsWith(DATABASE_PREFIX)) {
            return null;
        }
        String value = locator.substring(DATABASE_PREFIX.length()).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
