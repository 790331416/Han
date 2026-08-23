package com.han.system.domain.vo;

/** 管理端系统设置视图；测试安全开关不进入登录前公开品牌接口。 */
public record SystemBrandSettingsVo(
        String fullName,
        String shortName,
        String displayMode,
        String displayName,
        String loginSubtitle,
        String logoUrl,
        boolean allowInsecureVendorRegistration
) {
}
