package com.han.common.mybatis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 租户配置属性
 */
@Data
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 是否启用多租户
     */
    private Boolean enable = true;

    /**
     * 排除的表（不需要租户过滤）
     */
    private List<String> excludes = List.of();
}
