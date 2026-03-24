package com.han.system.controller;

import com.han.common.core.config.DeployProperties;
import com.han.common.core.domain.R;
import com.han.common.core.enums.DeployTier;
import com.han.common.security.annotation.PermissionExempt;
import com.han.system.domain.vo.RuntimeCapabilityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时能力探测接口。
 */
@RestController
@RequestMapping("/system/runtime")
@RequiredArgsConstructor
public class RuntimeCapabilityController {

    private final DeployProperties deployProperties;
    private final Environment environment;

    @GetMapping("/capabilities")
    @PermissionExempt("登录前需要读取部署层级与启用能力")
    public R<RuntimeCapabilityVO> capabilities() {
        DeployTier tier = deployProperties.getTierEnum();
        boolean mediumTier = tier.isAtLeast(DeployTier.MEDIUM);
        boolean fullTier = tier.isAtLeast(DeployTier.FULL);
        boolean genEnabled = hasAny("HAN_GEN_ENABLED", "han.gen.enabled");

        List<String> enabledModules = new ArrayList<>(List.of(
                "gateway",
                "auth",
                "system",
                "job",
                "user-center",
                "notice",
                "config",
                "monitor"
        ));
        if (mediumTier) {
            enabledModules.addAll(List.of("tenant", "workflow", "open", "file"));
        }
        if (genEnabled) {
            enabledModules.add("gen");
        }
        if (fullTier) {
            enabledModules.addAll(List.of("ai", "mcp", "prompt", "agent", "ai-workflow", "chat", "token"));
        }

        Map<String, Boolean> optionalServices = new LinkedHashMap<>();
        optionalServices.put("redis", hasAny("spring.data.redis.host", "REDIS_HOST"));
        optionalServices.put("nacos", hasAny("spring.cloud.nacos.discovery.server-addr", "NACOS_SERVER_ADDR"));
        optionalServices.put("rustfs", hasAny("RUSTFS_ENDPOINT", "RUSTFS_ACCESS_KEY", "RUSTFS_SECRET_KEY"));
        optionalServices.put("rabbitmq", mediumTier && hasAny("spring.rabbitmq.host", "RABBITMQ_HOST"));
        optionalServices.put("kafka", fullTier && hasAny("spring.kafka.bootstrap-servers", "KAFKA_SERVERS"));
        optionalServices.put("elasticsearch", fullTier && hasAny("spring.elasticsearch.uris", "ELASTICSEARCH_URIS"));

        Map<String, Boolean> featureFlags = new LinkedHashMap<>();
        featureFlags.put("tenantSelect", mediumTier);
        featureFlags.put("workflow", mediumTier);
        featureFlags.put("openPlatform", mediumTier);
        featureFlags.put("ossConfig", mediumTier);
        featureFlags.put("gen", genEnabled);
        featureFlags.put("ai", fullTier);
        featureFlags.put("observability", fullTier && (optionalServices.get("kafka") || optionalServices.get("elasticsearch")));

        return R.ok(new RuntimeCapabilityVO(
                tier.value(),
                enabledModules,
                optionalServices,
                featureFlags
        ));
    }

    private boolean hasAny(String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }
}
