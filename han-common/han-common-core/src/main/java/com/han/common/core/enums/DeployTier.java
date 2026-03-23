package com.han.common.core.enums;

import java.util.Locale;

/**
 * 部署层级
 */
public enum DeployTier {

    SMALL,
    MEDIUM,
    FULL;

    public static final DeployTier DEFAULT = MEDIUM;

    public static DeployTier from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "small" -> SMALL;
            case "full" -> FULL;
            default -> MEDIUM;
        };
    }

    public boolean isAtLeast(DeployTier other) {
        return this.ordinal() >= other.ordinal();
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
