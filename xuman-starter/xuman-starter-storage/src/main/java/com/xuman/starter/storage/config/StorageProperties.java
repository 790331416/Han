package com.xuman.starter.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "xuman.storage")
public class StorageProperties {

    /**
     * 存储类型: local, rustfs, oss
     */
    private String type = "local";

    /**
     * RustFS 配置
     */
    private RustFS rustfs = new RustFS();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public RustFS getRustfs() {
        return rustfs;
    }

    public void setRustfs(RustFS rustfs) {
        this.rustfs = rustfs;
    }

    public static class RustFS {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "xumanadmin";
        private String secretKey = "xuman@2026";
        private String region = "us-east-1";
        private String bucket = "xuman";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
