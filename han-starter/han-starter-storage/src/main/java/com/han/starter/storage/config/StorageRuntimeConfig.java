package com.han.starter.storage.config;

import org.springframework.util.StringUtils;

/**
 * Resolved runtime storage configuration.
 */
public final class StorageRuntimeConfig {

    private final Long ossConfigId;
    private final String configKey;
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;
    private final String prefix;
    private final String region;
    private final String isHttps;

    public StorageRuntimeConfig(Long ossConfigId, String configKey, String endpoint, String accessKey, String secretKey,
                                String bucketName, String prefix, String region, String isHttps) {
        this.ossConfigId = ossConfigId;
        this.configKey = configKey;
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.prefix = prefix;
        this.region = region;
        this.isHttps = isHttps;
    }

    public static StorageRuntimeConfig fromProperties(StorageProperties.RustFS properties) {
        return new StorageRuntimeConfig(
                null,
                "rustfs",
                properties.getEndpoint(),
                properties.getAccessKey(),
                properties.getSecretKey(),
                properties.getBucket(),
                properties.getPrefix(),
                properties.getRegion(),
                properties.getIsHttps()
        );
    }

    public String getConfigKey() {
        return configKey;
    }

    public Long getOssConfigId() {
        return ossConfigId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getRegion() {
        return region;
    }

    public String getIsHttps() {
        return isHttps;
    }

    public String signature() {
        return String.join("|",
                safe(configKey),
                safe(endpoint),
                safe(accessKey),
                safe(secretKey),
                safe(bucketName),
                safe(prefix),
                safe(region),
                safe(isHttps));
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
