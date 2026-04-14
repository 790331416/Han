package com.han.starter.storage.config;

import com.han.common.core.context.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * PostgreSQL-based storage configuration lookup.
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

    public JdbcStorageConfigRepository(StorageDatabaseProperties databaseProperties, SecurityContext securityContext) {
        this.databaseProperties = databaseProperties;
        this.securityContext = securityContext;
    }

    @Override
    public Optional<StorageConfigRecord> findActiveRecord() {
        if (!databaseProperties.isConfigured()) {
            return Optional.empty();
        }

        Long tenantId = securityContext != null ? securityContext.getTenantId() : null;
        try {
            if (tenantId != null) {
                Optional<StorageRuntimeConfig> tenantConfig = queryFirst(
                        SELECT_COLUMNS + " AND tenant_id = ? ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST LIMIT 1",
                        tenantId
                );
                if (tenantConfig.isPresent()) {
                    return tenantConfig.map(this::toRecord);
                }
            }

            Optional<StorageRuntimeConfig> globalConfig = queryFirst(
                    SELECT_COLUMNS + " AND tenant_id IS NULL ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST LIMIT 1"
            );
            if (globalConfig.isPresent()) {
                return globalConfig.map(this::toRecord);
            }

            return queryFirst(
                    SELECT_COLUMNS + " ORDER BY update_time DESC NULLS LAST, create_time DESC NULLS LAST LIMIT 1"
            ).map(this::toRecord);
        } catch (SQLException ex) {
            log.debug("Skipping database-backed storage config lookup: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<StorageConfigRecord> findRecord(String locator) {
        if (!databaseProperties.isConfigured()) {
            return Optional.empty();
        }
        Long ossConfigId = StorageConfigRecord.parseDatabaseId(locator);
        if (ossConfigId == null) {
            return Optional.empty();
        }
        try {
            return queryFirst(SELECT_BY_ID, ossConfigId).map(this::toRecord);
        } catch (SQLException ex) {
            log.debug("Skipping locator-based storage config lookup: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<StorageRuntimeConfig> queryFirst(String sql, Object... params) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                databaseProperties.getUrl(),
                databaseProperties.getUsername(),
                databaseProperties.getPassword()
        );
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
}
