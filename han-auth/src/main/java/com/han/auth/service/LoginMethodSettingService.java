package com.han.auth.service;

import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录方式开关读取服务
 * <p>
 * 从 han-system 的 sys_config 读取 sys.login.* 开关，60 秒本地缓存；
 * 与验证码开关相反，登录方式开关读取失败或未配置时默认**关闭**（fail-secure：
 * 新登录方式必须由管理员显式开启，避免凭据未配好时暴露入口）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginMethodSettingService {

    public static final String WECHAT_ENABLED_KEY = "sys.login.wechatEnabled";

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final SystemServiceClient systemServiceClient;

    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    public boolean isWechatLoginEnabled() {
        return isEnabled(WECHAT_ENABLED_KEY);
    }

    /**
     * 判断某社交登录提供方是否开启。
     * <p>已接入开关的提供方（微信）按 sys_config 开关判定；
     * 未接入开关的提供方（GitHub 等历史提供方）保持「配置即启用」的原有行为。
     */
    public boolean isProviderEnabled(String provider) {
        if (WeChatOAuthService.PROVIDER.equals(provider)) {
            return isWechatLoginEnabled();
        }
        return true;
    }

    private boolean isEnabled(String configKey) {
        CachedValue cached = cache.get(configKey);
        if (cached != null && cached.expireAt().isAfter(Instant.now())) {
            return cached.enabled();
        }
        boolean enabled = loadRemote(configKey);
        cache.put(configKey, new CachedValue(enabled, Instant.now().plus(CACHE_TTL)));
        return enabled;
    }

    private boolean loadRemote(String configKey) {
        try {
            R<String> result = systemServiceClient.getConfigValue(configKey);
            String value = result != null ? result.getData() : null;
            return value != null && "true".equalsIgnoreCase(value.trim());
        } catch (Exception e) {
            log.warn("读取登录方式开关[{}]失败，默认关闭", configKey, e);
            return false;
        }
    }

    private record CachedValue(boolean enabled, Instant expireAt) {
    }
}
