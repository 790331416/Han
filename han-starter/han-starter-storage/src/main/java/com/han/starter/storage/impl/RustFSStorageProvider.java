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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public class RustFSStorageProvider implements StorageProvider, Closeable {

    /**
     * 分段上传的分段大小；S3 协议要求除最后一段外每段不小于 5MB。
     */
    private static final int MULTIPART_CHUNK_SIZE = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final String bucket;
    private final String endpoint;
    private final String prefix;

    public RustFSStorageProvider(StorageProperties.RustFS properties) {
        this(StorageRuntimeConfig.fromProperties(properties));
    }

    public RustFSStorageProvider(StorageRuntimeConfig config) {
        this.bucket = requireConfigured(config.getBucketName(), "bucket");
        this.endpoint = normalizeEndpoint(config.getEndpoint(), config.getIsHttps());
        this.prefix = normalizePrefix(config.getPrefix());
        String accessKey = requireConfigured(config.getAccessKey(), "accessKey");
        String secretKey = requireConfigured(config.getSecretKey(), "secretKey");
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(this.endpoint))
                .region(Region.of(config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
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
        return upload(path, stream, "application/octet-stream", null);
    }

    @Override
    public String upload(String path, InputStream stream, String contentType) {
        return upload(path, stream, contentType, null);
    }

    @Override
    public String upload(String path, InputStream stream, String contentType, Long contentLength) {
        try {
            String objectKey = buildObjectKey(path);
            String resolvedContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
            if (contentLength != null && contentLength >= 0) {
                s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(resolvedContentType)
                        .contentLength(contentLength)
                        .build(), RequestBody.fromInputStream(stream, contentLength));
            } else {
                uploadInChunks(objectKey, stream, resolvedContentType);
            }
            return getUrl(path);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("RustFS upload failed", e);
        }
    }

    /**
     * 长度未知时走分段上传：既不会像 {@code InputStream#available()} 那样静默截断，
     * 也不需要把整个文件读进内存。
     */
    private void uploadInChunks(String objectKey, InputStream stream, String contentType) throws IOException {
        String uploadId = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build()).uploadId();
        boolean completed = false;
        try {
            List<CompletedPart> parts = new ArrayList<>();
            byte[] buffer = new byte[MULTIPART_CHUNK_SIZE];
            int partNumber = 1;
            int read;
            while ((read = readChunk(stream, buffer)) > 0) {
                UploadPartResponse response = s3Client.uploadPart(UploadPartRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .uploadId(uploadId)
                                .partNumber(partNumber)
                                .contentLength((long) read)
                                .build(),
                        RequestBody.fromBytes(Arrays.copyOf(buffer, read)));
                parts.add(CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build());
                partNumber++;
            }
            if (parts.isEmpty()) {
                // 分段上传不允许零分段，空文件退回单次写入
                s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(bucket).key(objectKey).uploadId(uploadId).build());
                completed = true;
                s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(0L)
                        .build(), RequestBody.empty());
                return;
            }
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
            completed = true;
        } finally {
            if (!completed) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(bucket).key(objectKey).uploadId(uploadId).build());
                } catch (RuntimeException ignored) {
                    // 中止失败只会留下未完成分段，由存储侧生命周期策略回收
                }
            }
        }
    }

    private static int readChunk(InputStream stream, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = stream.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    @Override
    public InputStream download(String path) {
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(buildObjectKey(path)).build());
            return response;
        } catch (IllegalArgumentException e) {
            throw e;
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
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }

    private String buildObjectKey(String path) {
        String normalizedPath = normalizeObjectKey(path);
        if (!StringUtils.hasText(prefix)) {
            return normalizedPath;
        }
        if (!StringUtils.hasText(normalizedPath)) {
            return prefix;
        }
        return prefix + "/" + normalizedPath;
    }

    /**
     * 对象 key 归一化：统一分隔符、去掉空段与 {@code .} 段，
     * 出现 {@code ..} 或控制字符一律拒绝（不做静默 resolve，避免跨前缀/跨桶穿越）。
     */
    public static String normalizeObjectKey(String path) {
        String value = path == null ? "" : path.replace('\\', '/').trim();
        if (value.isEmpty()) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 0x20 || value.charAt(i) == 0x7f) {
                throw new IllegalArgumentException("Illegal storage key: control character not allowed");
            }
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Illegal storage key: path traversal not allowed");
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    private static String requireConfigured(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Storage " + field + " is not configured; refuse to fall back to a built-in default");
        }
        return value.trim();
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
