package com.han.common.core.config;

import com.han.common.core.enums.DeployTier;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一部署层级配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "han.deploy")
public class DeployProperties {

    /**
     * small | medium | full
     */
    private String tier = DeployTier.DEFAULT.value();

    public DeployTier getTierEnum() {
        return DeployTier.from(tier);
    }
}
