package com.han.starter.storage.config;

import com.han.common.core.context.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * PostgreSQL-based storage configuration lookup.
 *
 * <p>优先复用应用的 {@link DataSource}（连接池 / 监控 / 超时都能生效），
 * 仅在应用未提供数据源时才退回 {@link DriverManager} 直连。
 * 存储配置是低频变更数据，查询结果带 TTL 缓存，避免每次文件读写都打一次库。
 */
@Slf4j
public class JdbcStorageConfigRepository implements StorageConfigRepository {

    private static final String SELECT_COLUMNS = """
            SELECT oss_config_id, config_key, endpoint, access_key, secret_key, bucket_name, prefix, region, is_https
              FROM sys_oss_config
             WHERE status = '0'
            """;

    private static final String SELECT_BY_ID = """
            SELECT oss_config_id, config_key, endpoint, access_key, secret_key, bucket_name, prefix, region, is_https
              FROM sys_oss_config
             WHERE oss_config_id = ?
             LIMIT 1
            """;

    private final StorageDatabaseProperties databaseProperties;
    private final SecurityContext securityContext;
    private final Supplier<DataSource> dataSourceSupplier;
    private final long cacheTtlMillis;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile DataSource resolvedDataSource;
    private volatile boolean dataSourceResolved;

    public JdbcStorageConfigRepository(StorageDatabaseProperties databaseProperties, SecurityContext securityContext) {
        this(databaseProperties, securityContext, null, 60_000L);
    }

    public JdbcStorageConfigRepository(StorageDatabaseProperties databaseProperties,
                                       SecurityContext securityContext,
                                       Supplier<DataSource> dataSourceSupplier,
                                       long cacheTtlMillis) {
        this.databaseProperties = databaseProperties;
        this.securityContext = securityContext;
        this.dataSourceSupplier = dataSourceSupplier;
        this.cacheTtlMillis = Math.max(cacheTtlMillis, 0L);
    }

    @Override
    public Optional<StorageConfigRecord> findActiveRecord() {
        if (!isUsable()) {
            return Optional.empty();
        }

        Long tenantId = securityContext != null ? securityContext.getTenantId() : null;
        return fromCache("active:" + tenantId, () -> {
            if (tenantId != null) {
                Optional<StorageRuntimeConfig> tenantConfig = queryFirst(
                        SELECT_COLUMNS + " AND tenant_id = ? ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST LIMIT 1",
                        tenantId
                );
                if (tenantConfig.isPresent()) {
                    return tenantConfig.map(this::toRecord);
                }
            }

            // 平台级全局配置是最后一级；绝不回退到「任意一条」，
            // 否则未配置存储的租户会静默写进别家租户的桶并持有别家凭证。
            return queryFirst(
                    SELECT_COLUMNS + " AND tenant_id IS NULL ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST LIMIT 1"
            ).map(this::toRecord);
        });
    }

    @Override
    public Optional<StorageConfigRecord> findRecord(String locator) {
        if (!isUsable()) {
            return Optional.empty();
        }
        Long ossConfigId = StorageConfigRecord.parseDatabaseId(locator);
        if (ossConfigId == null) {
            return Optional.empty();
        }
        // 按 locator 取配置不加租户/状态条件：locator 只解决「这条记录存在哪个桶」，
        // 归属与租户由调用方在 sys_file 上校验；此处加条件会让停用配置下的历史文件直接不可下载。
        return fromCache("locator:" + ossConfigId, () -> queryFirst(SELECT_BY_ID, ossConfigId).map(this::toRecord));
    }

    /**
     * 清空配置缓存（存储配置变更后可调用）。
     */
    public void clearCache() {
        cache.clear();
    }

    private boolean isUsable() {
        return dataSource() != null || databaseProperties.isConfigured();
    }

    private Optional<StorageConfigRecord> fromCache(String key, ConfigLoader loader) {
        CacheEntry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expireAt() > now) {
            return entry.value();
        }
        Optional<StorageConfigRecord> value;
        try {
            value = loader.load();
        } catch (SQLException ex) {
            log.debug("Skipping database-backed storage config lookup: {}", ex.getMessage());
            return Optional.empty();
        }
        if (cacheTtlMillis > 0) {
            cache.put(key, new CacheEntry(value, now + cacheTtlMillis));
        }
        return value;
    }

    private Optional<StorageRuntimeConfig> queryFirst(String sql, Object... params) throws SQLException {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StorageRuntimeConfig(
                        resultSet.getLong("oss_config_id"),
                        resultSet.getString("config_key"),
                        resultSet.getString("endpoint"),
                        resultSet.getString("access_key"),
                        resultSet.getString("secret_key"),
                        resultSet.getString("bucket_name"),
                        normalizePrefix(resultSet.getString("prefix")),
                        firstNonBlank(resultSet.getString("region"), "us-east-1"),
                        firstNonBlank(resultSet.getString("is_https"), "1")
                ));
            }
        }
    }

    private Connection openConnection() throws SQLException {
        DataSource dataSource = dataSource();
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword()
        );
    }

    /**
     * 延迟解析数据源：starter 在自动装配阶段拿数据源会打乱初始化顺序，改为首次使用时解析并缓存。
     */
    private DataSource dataSource() {
        if (!dataSourceResolved) {
            synchronized (this) {
                if (!dataSourceResolved) {
                    DataSource candidate = null;
                    if (dataSourceSupplier != null) {
                        try {
                            candidate = dataSourceSupplier.get();
                        } catch (RuntimeException ex) {
                            log.debug("Application DataSource unavailable for storage config lookup: {}", ex.getMessage());
                        }
                    }
                    resolvedDataSource = candidate;
                    dataSourceResolved = true;
                }
            }
        }
        return resolvedDataSource;
    }

    private StorageConfigRecord toRecord(StorageRuntimeConfig runtimeConfig) {
        Long ossConfigId = runtimeConfig.getOssConfigId();
        if (ossConfigId == null) {
            throw new IllegalStateException("Database-backed storage config requires ossConfigId");
        }
        return StorageConfigRecord.fromDatabase(ossConfigId, runtimeConfig);
    }

    private String normalizePrefix(String prefix) {
        return StringUtils.hasText(prefix) ? prefix.trim() : "";
    }

    private String firstNonBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    @FunctionalInterface
    private interface ConfigLoader {
        Optional<StorageConfigRecord> load() throws SQLException;
    }

    private record CacheEntry(Optional<StorageConfigRecord> value, long expireAt) {
    }
}
