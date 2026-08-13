package com.han.common.web.http;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HttpExchange 声明式客户端自动配置。
 * <p>
 * 提供带 {@link LoadBalanced} 的 {@link RestClient.Builder}，
 * 结合 Spring Cloud LoadBalancer 实现基于服务名的负载均衡调用。
 * <p>
 * <b>注意</b>：这是容器里唯一的 {@code RestClient.Builder} Bean 且带 {@code @LoadBalanced}。
 * 任何组件如果只是想调用外部厂商 API（例如 AI 模块调第三方推理接口），
 * <b>不要</b>直接注入 {@code RestClient.Builder} —— 拿到的是负载均衡版本，
 * 它会把 {@code api.example.com} 当服务名去注册中心查，报错信息还指向注册中心，排查方向完全跑偏。
 * 这种场景请自行 {@code RestClient.builder()}。
 * 这里加 {@code @ConditionalOnMissingBean} 是为了让业务侧可以用自己的 Builder 覆盖。
 */
@Configuration
public class HttpClientAutoConfiguration {

    @Bean
    @LoadBalanced
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
