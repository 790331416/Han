package com.han.common.log.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志配置，绑定 {@code han.log.oper.*}。
 */
@Data
@ConfigurationProperties(prefix = "han.log.oper")
public class OperLogProperties {

    /**
     * 是否启用操作日志切面
     */
    private boolean enabled = true;

    /**
     * 在默认脱敏字段名之外追加的字段名（大小写不敏感，按「包含」匹配）
     */
    private List<String> maskFields = new ArrayList<>();

    /**
     * 异步写入线程池
     */
    private Async async = new Async();

    @Data
    public static class Async {

        /**
         * 核心线程数
         */
        private int corePoolSize = 2;

        /**
         * 最大线程数
         */
        private int maxPoolSize = 4;

        /**
         * 队列容量。队列满后走 CallerRuns，宁可拖慢调用方也不丢审计日志。
         */
        private int queueCapacity = 512;

        /**
         * 线程空闲回收秒数
         */
        private int keepAliveSeconds = 60;
    }
}
