package com.han.starter.storage.impl;

import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageProperties;
import com.han.starter.storage.config.StorageRuntimeConfig;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;

/**
 * @deprecated 使用 {@link S3CompatibleStorageProvider}；保留该类仅兼容旧的 RustFS 配置入口。
 */
@Deprecated(forRemoval = false)
public class RustFSStorageProvider extends S3CompatibleStorageProvider {

    public RustFSStorageProvider(StorageProperties.RustFS properties) {
        this(StorageRuntimeConfig.fromProperties(properties));
    }

    public RustFSStorageProvider(StorageRuntimeConfig config) {
        super(config);
    }
}
