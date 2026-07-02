package com.han.auth.service;

import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证码开关读取服务
 * <p>
 * 从 han-system 的 sys_config 读取 sys.account.captchaEnabled；
 * 带 60 秒本地缓存避免每次登录/取码都远程调用；读取失败时默认开启（fail-secure）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaSettingService {

    public static final String CAPTCHA_ENABLED_KEY = "sys.account.captchaEnabled";

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final SystemServiceClient systemServiceClient;

    private final AtomicReference<CachedValue> cache = new AtomicReference<>();

    public boolean isCaptchaEnabled() {
        CachedValue cached = cache.get();
        if (cached != null && cached.expireAt().isAfter(Instant.now())) {
            return cached.enabled();
        }
        boolean enabled = loadRemote();
        cache.set(new CachedValue(enabled, Instant.now().plus(CACHE_TTL)));
        return enabled;
    }

    private boolean loadRemote() {
        try {
            R<String> result = systemServiceClient.getConfigValue(CAPTCHA_ENABLED_KEY);
            String value = result != null ? result.getData() : null;
            if (value == null || value.isBlank()) {
                return true;
            }
            return !"false".equalsIgnoreCase(value.trim());
        } catch (Exception e) {
            log.warn("读取验证码开关配置失败，默认开启验证码", e);
            return true;
        }
    }

    private record CachedValue(boolean enabled, Instant expireAt) {
    }
}
