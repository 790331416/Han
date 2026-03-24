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

public class RustFSStorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;
    private final String prefix;

    public RustFSStorageProvider(StorageProperties.RustFS properties) {
        this(StorageRuntimeConfig.fromProperties(properties));
    }

    public RustFSStorageProvider(StorageRuntimeConfig config) {
        this.bucket = config.getBucketName();
        this.endpoint = normalizeEndpoint(config.getEndpoint(), config.getIsHttps());
        this.prefix = normalizePrefix(config.getPrefix());
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(this.endpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .forcePathStyle(true)
                .build();

        // Ensure bucket exists
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ignored) {
        } catch (Exception e) {
            // Log or handle other exceptions
        }
    }

    @Override
    public String upload(String path, InputStream stream) {
        return upload(path, stream, "application/octet-stream");
    }

    @Override
    public String upload(String path, InputStream stream, String contentType) {
        try {
            String objectKey = buildObjectKey(path);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(), RequestBody.fromInputStream(stream, (long) stream.available()));
            return getUrl(path);
        } catch (Exception e) {
            throw new RuntimeException("RustFS upload failed", e);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build());
            return response;
        } catch (Exception e) {
            throw new RuntimeException("RustFS download failed", e);
        }
    }

    @Override
    public void delete(String path) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build());
    }

    @Override
    public String getUrl(String path) {
        return endpoint + "/" + bucket + "/" + buildObjectKey(path);
    }

    @Override
    public boolean exists(String path) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildObjectKey(String path) {
        String normalizedPath = path == null ? "" : path.replace("\\", "/");
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (!StringUtils.hasText(prefix)) {
            return normalizedPath;
        }
        if (!StringUtils.hasText(normalizedPath)) {
            return prefix;
        }
        return prefix + "/" + normalizedPath;
    }

    private static String normalizeEndpoint(String endpoint, String isHttps) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Storage endpoint must not be blank");
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return stripTrailingSlash(normalized);
        }
        String scheme = "0".equals(isHttps) ? "https://" : "http://";
        return stripTrailingSlash(scheme + normalized);
    }

    private static String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String normalized = prefix.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
