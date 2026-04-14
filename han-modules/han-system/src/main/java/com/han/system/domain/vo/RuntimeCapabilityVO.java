package com.han.system.domain.vo;

import java.util.List;
import java.util.Map;

/**
 * 运行时能力信息
 */
public record RuntimeCapabilityVO(
        String tier,
        List<String> enabledModules,
        Map<String, Boolean> optionalServices,
        Map<String, Boolean> featureFlags
) {
}
