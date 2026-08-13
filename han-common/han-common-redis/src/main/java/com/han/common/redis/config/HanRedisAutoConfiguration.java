package com.han.common.redis.config;

import com.han.common.redis.core.RedisAtomicOps;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * han-common-redis 的自动配置。
 * <p>
 * 本模块此前只有一个 pom（打出来是个 1.4 KB 的空 jar），规则要求的「Redis 操作统一收口」
 * 在依赖图上存在、在代码里不存在，于是每个调用点都自己拼 key、自己写
 * {@code increment} + {@code expire}。这里先补上最要紧的一块：原子操作门面。
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
public class HanRedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public RedisAtomicOps redisAtomicOps(StringRedisTemplate redisTemplate) {
        return new RedisAtomicOps(redisTemplate);
    }
}
