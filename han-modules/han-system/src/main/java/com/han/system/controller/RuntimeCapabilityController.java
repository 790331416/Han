package com.han.system.controller;

import com.han.common.core.domain.R;
import com.han.common.core.enums.DeployTier;
import com.han.common.security.annotation.PermissionExempt;
import com.han.system.domain.vo.RuntimeCapabilityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时能力探测接口。
 */
@RestController
@RequestMapping("/system/runtime")
@RequiredArgsConstructor
public class RuntimeCapabilityController {

    private static final String MODULE_GATEWAY = "gateway";
    private static final String MODULE_AUTH = "auth";
    private static final String MODULE_SYSTEM = "system";
    private static final String MODULE_JOB = "job";
    private static final String MODULE_TENANT = "tenant";
    private static final String MODULE_WORKFLOW = "workflow";
    private static final String MODULE_OPEN = "open";
    private static final String MODULE_FILE = "file";
    private static final String MODULE_GEN = "gen";
    private static final String MODULE_AI = "ai";

    private static final String SERVICE_GATEWAY = "han-gateway";
    private static final String SERVICE_AUTH = "han-auth";
    private static final String SERVICE_SYSTEM = "han-system";
    private static final String SERVICE_JOB = "han-job";
    private static final String SERVICE_TENANT = "han-tenant";
    private static final String SERVICE_WORKFLOW = "han-workflow";
    private static final String SERVICE_OPEN = "han-open";
    private static final String SERVICE_FILE = "han-file";
    private static final String SERVICE_GEN = "han-gen";
    private static final String SERVICE_AI = "han-ai";

    private final Environment environment;
    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;

    @GetMapping("/capabilities")
    @PermissionExempt("登录前需要读取部署层级与启用能力")
    public R<RuntimeCapabilityVO> capabilities() {
        DeployTier tier = resolveTier();
        boolean mediumTier = tier.isAtLeast(DeployTier.MEDIUM);
        boolean fullTier = tier.isAtLeast(DeployTier.FULL);
        boolean genConfigured = hasAny("HAN_GEN_ENABLED", "han.gen.enabled");
        Set<String> discoveredServices = resolveDiscoveredServices();

        boolean gatewayAvailable = isServiceAvailable(discoveredServices, SERVICE_GATEWAY);
        boolean authAvailable = isServiceAvailable(discoveredServices, SERVICE_AUTH);
        boolean systemAvailable = isServiceAvailable(discoveredServices, SERVICE_SYSTEM) || discoveredServices.isEmpty();
        boolean jobAvailable = isServiceAvailable(discoveredServices, SERVICE_JOB);
        boolean tenantAvailable = mediumTier && isServiceAvailable(discoveredServices, SERVICE_TENANT);
        boolean workflowAvailable = mediumTier && isServiceAvailable(discoveredServices, SERVICE_WORKFLOW);
        boolean openAvailable = mediumTier && isServiceAvailable(discoveredServices, SERVICE_OPEN);
        boolean fileAvailable = mediumTier && isServiceAvailable(discoveredServices, SERVICE_FILE);
        boolean genAvailable = genConfigured && isServiceAvailable(discoveredServices, SERVICE_GEN);
        boolean aiAvailable = fullTier && isServiceAvailable(discoveredServices, SERVICE_AI);

        List<String> enabledModules = new ArrayList<>();
        if (gatewayAvailable) {
            enabledModules.add(MODULE_GATEWAY);
        }
        if (authAvailable) {
            enabledModules.add(MODULE_AUTH);
        }
        if (systemAvailable) {
            enabledModules.addAll(List.of(MODULE_SYSTEM, "user-center", "notice", "config", "monitor"));
        }
        if (jobAvailable) {
            enabledModules.add(MODULE_JOB);
        }
        if (tenantAvailable) {
            enabledModules.add(MODULE_TENANT);
        }
        if (workflowAvailable) {
            enabledModules.add(MODULE_WORKFLOW);
        }
        if (openAvailable) {
            enabledModules.add(MODULE_OPEN);
        }
        if (fileAvailable) {
            enabledModules.add(MODULE_FILE);
        }
        if (genAvailable) {
            enabledModules.add(MODULE_GEN);
        }
        if (aiAvailable) {
            enabledModules.addAll(List.of(MODULE_AI, "mcp", "prompt", "agent", "ai-workflow", "chat", "token"));
        }

        Map<String, Boolean> optionalServices = new LinkedHashMap<>();
        optionalServices.put("redis", hasAny("spring.data.redis.host", "REDIS_HOST"));
        optionalServices.put("nacos", hasAny("spring.cloud.nacos.discovery.server-addr", "NACOS_SERVER_ADDR"));
        optionalServices.put("rustfs", hasAny("RUSTFS_ENDPOINT", "RUSTFS_ACCESS_KEY", "RUSTFS_SECRET_KEY"));
        optionalServices.put("rabbitmq", mediumTier && hasAny("spring.rabbitmq.host", "RABBITMQ_HOST"));
        optionalServices.put("kafka", fullTier && hasAny("spring.kafka.bootstrap-servers", "KAFKA_SERVERS"));
        optionalServices.put("elasticsearch", fullTier && hasAny("spring.elasticsearch.uris", "ELASTICSEARCH_URIS"));

        Map<String, Boolean> featureFlags = new LinkedHashMap<>();
        featureFlags.put("tenantSelect", tenantAvailable);
        featureFlags.put("workflow", workflowAvailable);
        featureFlags.put("openPlatform", openAvailable);
        featureFlags.put("ossConfig", mediumTier && systemAvailable);
        featureFlags.put("gen", genAvailable);
        featureFlags.put("ai", aiAvailable);
        featureFlags.put("observability", fullTier && (optionalServices.get("kafka") || optionalServices.get("elasticsearch")));

        return R.ok(new RuntimeCapabilityVO(
                tier.value(),
                enabledModules,
                optionalServices,
                featureFlags
        ));
    }

    private DeployTier resolveTier() {
        String tier = environment.getProperty("han.deploy.tier");
        if (tier == null || tier.isBlank()) {
            tier = environment.getProperty("HAN_DEPLOY_TIER", DeployTier.DEFAULT.value());
        }
        return DeployTier.from(tier);
    }

    private boolean hasAny(String... keys) {
        for (String key : keys) {
            if (hasText(environment.getProperty(key))) {
                return true;
            }
            if (hasText(System.getenv(key))) {
                return true;
            }
            if (hasText(System.getenv(normalizeEnvKey(key)))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    private Set<String> resolveDiscoveredServices() {
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return Set.of();
        }
        try {
            return new LinkedHashSet<>(discoveryClient.getServices().stream()
                    .map(service -> service == null ? "" : service.toLowerCase(Locale.ROOT))
                    .filter(service -> !service.isBlank())
                    .collect(Collectors.toList()));
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean isServiceAvailable(Set<String> discoveredServices, String serviceName) {
        return discoveredServices.contains(serviceName.toLowerCase(Locale.ROOT));
    }
}
