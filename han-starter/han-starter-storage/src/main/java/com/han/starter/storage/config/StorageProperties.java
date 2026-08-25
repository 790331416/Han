package com.han.starter.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "han.storage")
public class StorageProperties {

    /**
     * Base64 编码的 32 字节 AES-GCM 主密钥，仅允许来自受控环境变量或 Secret。
     */
    private String masterKey;

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

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public RustFS getRustfs() {
        return rustfs;
    }

    public void setRustfs(RustFS rustfs) {
        this.rustfs = rustfs;
    }

    public static class RustFS {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "hanadmin";
        private String secretKey = "han@2026";
        private String region = "us-east-1";
        private String bucket = "han";
        private String prefix = "";
        private String isHttps = "1";

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
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getIsHttps() { return isHttps; }
        public void setIsHttps(String isHttps) { this.isHttps = isHttps; }
    }
}
