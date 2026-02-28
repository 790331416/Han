package com.han.auth.domain;

import lombok.Builder;

/**
 * 登录响应VO
 */
@Builder
public record LoginVO(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        UserInfoVO userInfo
) {
    /**
     * 用户摘要信息（Java Record — 不可变值对象）
     */
    @Builder
    public record UserInfoVO(
            Long userId,
            String username,
            String nickname,
            String avatar,
            String phone
    ) {}
}
