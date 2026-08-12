package com.han.common.core.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 部署层级
 */
public enum DeployTier {

    SMALL,
    MEDIUM,
    FULL;

    private static final Logger log = LoggerFactory.getLogger(DeployTier.class);

    public static final DeployTier DEFAULT = MEDIUM;

    /**
     * 解析部署档位。无法识别的取值仍按 {@link #DEFAULT} 处理以保证可启动，
     * 但会打 WARN —— 档位决定启用哪些模块与资源规格，静默兜底的代价太高。
     */
    public static DeployTier from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "small" -> SMALL;
            case "medium" -> MEDIUM;
            case "full" -> FULL;
            default -> {
                log.warn("[DeployTier] 未识别的部署档位 '{}'，已按 {} 处理。合法取值：small | medium | full",
                        value, DEFAULT.value());
                yield DEFAULT;
            }
        };
    }

    public boolean isAtLeast(DeployTier other) {
        return this.ordinal() >= other.ordinal();
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
