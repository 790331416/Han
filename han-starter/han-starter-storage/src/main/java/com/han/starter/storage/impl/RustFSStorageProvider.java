package com.han.starter.storage.impl;

import com.han.starter.storage.StorageProvider;
import com.han.starter.storage.config.StorageProperties;
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

    public RustFSStorageProvider(StorageProperties.RustFS properties) {
        this.bucket = properties.getBucket();
        this.endpoint = properties.getEndpoint();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
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
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(path)
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
                    GetObjectRequest.builder().bucket(bucket).key(path).build());
            return response;
        } catch (Exception e) {
            throw new RuntimeException("RustFS download failed", e);
        }
    }

    @Override
    public void delete(String path) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(path).build());
    }

    @Override
    public String getUrl(String path) {
        return endpoint + "/" + bucket + "/" + path;
    }

    @Override
    public boolean exists(String path) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(path).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
