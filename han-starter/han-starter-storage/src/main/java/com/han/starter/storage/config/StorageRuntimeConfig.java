package com.han.starter.storage.config;

import org.springframework.util.StringUtils;

/**
 * Resolved runtime storage configuration.
 */
public final class StorageRuntimeConfig {

    private final Long ossConfigId;
    private final String configKey;
    private final String endpoint;
    private final String publicEndpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucketName;
    private final String prefix;
    private final String region;
    private final String isHttps;
    private final boolean pathStyle;

    public StorageRuntimeConfig(Long ossConfigId, String configKey, String endpoint, String accessKey, String secretKey,
                                String bucketName, String prefix, String region, String isHttps) {
        this(ossConfigId, configKey, endpoint, null, accessKey, secretKey, bucketName, prefix, region, isHttps, true);
    }

    public StorageRuntimeConfig(Long ossConfigId, String configKey, String endpoint, String publicEndpoint,
                                String accessKey, String secretKey,
                                String bucketName, String prefix, String region, String isHttps, boolean pathStyle) {
        this.ossConfigId = ossConfigId;
        this.configKey = configKey;
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.prefix = prefix;
        this.region = region;
        this.isHttps = isHttps;
        this.pathStyle = pathStyle;
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

    public String getPublicEndpoint() {
        return publicEndpoint;
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

    public boolean isPathStyle() {
        return pathStyle;
    }

    public String signature() {
        return String.join("|",
                safe(configKey),
                safe(endpoint),
                safe(publicEndpoint),
                safe(accessKey),
                safe(secretKey),
                safe(bucketName),
                safe(prefix),
                safe(region),
                safe(isHttps),
                String.valueOf(pathStyle));
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
