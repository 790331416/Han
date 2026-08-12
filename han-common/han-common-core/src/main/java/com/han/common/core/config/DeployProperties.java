package com.han.common.core.config;

import com.han.common.core.enums.DeployTier;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一部署层级配置
 * <p>
 * 绑定 {@code han.deploy.*}。业务侧需要判断部署档位时应注入本类，
 * 不要各自去读 {@code HAN_DEPLOY_TIER} 环境变量。
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
