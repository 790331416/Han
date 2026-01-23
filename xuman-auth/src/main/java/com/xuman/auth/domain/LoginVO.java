package com.xuman.auth.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应VO
 */
@Data
@Builder
public class LoginVO {

    /** 访问Token */
    private String accessToken;

    /** 刷新Token */
    private String refreshToken;

    /** 有效期（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserInfoVO userInfo;

    @Data
    @Builder
    public static class UserInfoVO {
        private Long userId;
        private String username;
        private String nickname;
        private String avatar;
        private String phone;
    }
}
