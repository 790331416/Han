package com.han.starter.storage.impl;

import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageRuntimeConfig;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * 标准 S3 协议存储适配器。
 *
 * <p>天翼云 ZOS、RustFS、MinIO 等均通过 Endpoint、Region、桶和寻址方式配置，不再按供应商复制实现。</p>
 */
public class S3CompatibleStorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;
    private final String publicEndpoint;
    private final String prefix;
    private final S3Presigner presigner;

    public S3CompatibleStorageProvider(StorageRuntimeConfig config) {
        this.bucket = config.getBucketName();
        this.endpoint = normalizeEndpoint(config.getEndpoint(), config.getIsHttps());
        this.publicEndpoint = StringUtils.hasText(config.getPublicEndpoint())
                ? normalizeEndpoint(config.getPublicEndpoint(), config.getIsHttps()) : this.endpoint;
        this.prefix = normalizePrefix(config.getPrefix());
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(this.endpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle())
                        .build())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(this.publicEndpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle())
                        .build())
                .build();
    }

    @Override
    public String upload(String path, InputStream stream) {
        return upload(path, stream, "application/octet-stream");
    }

    @Override
    public String upload(String path, InputStream stream, String contentType) {
        return upload(path, stream, contentType, -1);
    }

    @Override
    public String upload(String path, InputStream stream, String contentType, long contentLength) {
        try {
            String objectKey = buildObjectKey(path);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(), contentLength >= 0
                    ? RequestBody.fromInputStream(stream, contentLength)
                    : RequestBody.fromContentProvider(() -> stream, contentType));
            return getUrl(path);
        } catch (Exception ex) {
            throw new IllegalStateException("S3 对象上传失败", ex);
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            ResponseInputStream<?> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build());
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("S3 对象读取失败", ex);
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
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public String createTemporaryUrl(String path, Duration duration) {
        Duration safeDuration = duration == null || duration.isNegative() || duration.isZero()
                ? Duration.ofMinutes(10) : duration;
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(safeDuration)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build())
                .build()).url().toString();
    }

    protected String buildObjectKey(String path) {
        String normalizedPath = path == null ? "" : path.replace("\\", "/");
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (!StringUtils.hasText(prefix)) {
            return normalizedPath;
        }
        return StringUtils.hasText(normalizedPath) ? prefix + "/" + normalizedPath : prefix;
    }

    private static String normalizeEndpoint(String endpoint, String isHttps) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("对象存储 Endpoint 不能为空");
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "0".equals(isHttps) ? "https://" + normalized : "http://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
}
