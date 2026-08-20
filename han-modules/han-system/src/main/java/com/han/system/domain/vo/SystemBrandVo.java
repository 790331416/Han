package com.han.system.domain.vo;

/** 前端展示所需的非敏感平台品牌信息。 */
public record SystemBrandVo(
        String fullName,
        String shortName,
        String displayMode,
        String displayName,
        String loginSubtitle,
        String logoUrl
) {
}
