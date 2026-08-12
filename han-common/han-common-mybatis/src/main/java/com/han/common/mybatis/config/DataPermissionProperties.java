package com.han.common.mybatis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据权限配置属性
 */
@Data
@ConfigurationProperties(prefix = "data-permission")
public class DataPermissionProperties {

    /**
     * 是否注册数据权限拦截器。
     *
     * <p>默认开启。拦截器只对标注了 {@code @DataPermission} 的语句生成条件，
     * 但它的 {@code beforeQuery} 会对每条 SQL 做一次 JSqlParser 解析。
     * 如果某个服务确认不需要数据范围过滤、又对解析开销敏感，可以关掉。</p>
     */
    private Boolean enable = true;
}
