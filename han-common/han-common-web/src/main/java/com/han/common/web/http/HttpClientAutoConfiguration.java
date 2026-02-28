package com.han.common.web.http;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HttpExchange 声明式客户端自动配置。
 * <p>
 * 提供带 {@link LoadBalanced} 的 {@link RestClient.Builder}，
 * 结合 Spring Cloud LoadBalancer 实现基于服务名的负载均衡调用。
 */
@Configuration
public class HttpClientAutoConfiguration {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
