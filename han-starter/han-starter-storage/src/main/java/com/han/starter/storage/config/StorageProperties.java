package com.han.starter.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "han.storage")
public class StorageProperties {

    /**
     * 存储类型: local, rustfs, oss
     */
    private String type = "local";

    /**
     * RustFS 配置
     */
    private RustFS rustfs = new RustFS();

    /**
     * 存储配置查询结果缓存秒数；存储配置低频变更，避免每次文件读写都查库。
     */
    private int configCacheSeconds = 60;

    /**
     * 运行期 Provider 缓存上限；超出后按最近最少使用淘汰并关闭对应的 S3 客户端。
     */
    private int providerCacheSize = 16;

    /**
     * 桶不存在时是否自动创建；生产环境建议关闭并按最小权限授予服务账号。
     */
    private boolean autoCreateBucket = true;

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

    public int getConfigCacheSeconds() {
        return configCacheSeconds;
    }

    public void setConfigCacheSeconds(int configCacheSeconds) {
        this.configCacheSeconds = configCacheSeconds;
    }

    public int getProviderCacheSize() {
        return providerCacheSize;
    }

    public void setProviderCacheSize(int providerCacheSize) {
        this.providerCacheSize = providerCacheSize;
    }

    public boolean isAutoCreateBucket() {
        return autoCreateBucket;
    }

    public void setAutoCreateBucket(boolean autoCreateBucket) {
        this.autoCreateBucket = autoCreateBucket;
    }

    public static class RustFS {
        private String endpoint = "http://localhost:9000";

        /**
         * 访问凭据必须由部署环境显式注入；不提供内置默认值，缺失时存储操作直接失败。
         */
        private String accessKey = "";

        /**
         * 访问密钥必须由部署环境显式注入；不提供内置默认值，缺失时存储操作直接失败。
         */
        private String secretKey = "";

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
