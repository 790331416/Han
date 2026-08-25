package com.han.starter.storage.config;

import com.han.common.core.context.SecurityContext;
import com.han.common.core.util.AesGcmCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Database-backed storage configuration lookup.
 */
@Slf4j
public class JdbcStorageConfigRepository implements StorageConfigRepository {

    private static final String SELECT_COLUMNS = """
            SELECT config.oss_config_id, config.config_key, config.endpoint, config.public_endpoint,
                   config.access_key_ciphertext, config.secret_key_ciphertext,
                   config.bucket_name, config.prefix, config.region, config.is_https, config.path_style
              FROM sys_storage_active active
              JOIN sys_oss_config config ON config.oss_config_id = active.oss_config_id
             WHERE config.status = '0'
            """;

    private static final String SELECT_BY_ID = """
            SELECT oss_config_id, config_key, endpoint, public_endpoint, access_key_ciphertext, secret_key_ciphertext,
                   bucket_name, prefix, region, is_https, path_style
              FROM sys_oss_config
             WHERE oss_config_id = ? AND status IN ('0', '2')
             LIMIT 1
            """;

    private final StorageDatabaseProperties databaseProperties;
    private final SecurityContext securityContext;
    private final String masterKey;

    public JdbcStorageConfigRepository(StorageDatabaseProperties databaseProperties, SecurityContext securityContext,
                                       String masterKey) {
        this.databaseProperties = databaseProperties;
        this.securityContext = securityContext;
        this.masterKey = masterKey;
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
                        SELECT_COLUMNS + " AND active.tenant_id = ? LIMIT 1",
                        tenantId
                );
                if (tenantConfig.isPresent()) {
                    return tenantConfig.map(this::toRecord);
                }
            }

            Optional<StorageRuntimeConfig> globalConfig = queryFirst(
                    SELECT_COLUMNS + " AND active.tenant_id = 0 LIMIT 1"
            );
            if (globalConfig.isPresent()) {
                return globalConfig.map(this::toRecord);
            }

            return Optional.empty();
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
                        resultSet.getString("public_endpoint"),
                        AesGcmCipher.decrypt(masterKey, resultSet.getString("access_key_ciphertext")),
                        AesGcmCipher.decrypt(masterKey, resultSet.getString("secret_key_ciphertext")),
                        resultSet.getString("bucket_name"),
                        normalizePrefix(resultSet.getString("prefix")),
                        firstNonBlank(resultSet.getString("region"), "us-east-1"),
                        firstNonBlank(resultSet.getString("is_https"), "1"),
                        resultSet.getBoolean("path_style")
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
