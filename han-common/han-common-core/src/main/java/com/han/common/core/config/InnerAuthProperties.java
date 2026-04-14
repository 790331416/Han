package com.han.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务间内部鉴权配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "han.security.inner-auth")
public class InnerAuthProperties {

    /**
     * 是否启用内部鉴权
     */
    private boolean enabled = true;

    /**
     * 服务间共享密钥
     */
    private String secret = "han-cloud-inner-auth";

    /**
     * 允许的时间偏差，单位：秒
     */
    private long clockSkewSeconds = 300;
}
