package com.han.auth.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 第三方社交用户信息
 */
@Data
@Builder
public class SocialUser {
    private String provider;
    private String openId;
    private String nickname;
    private String avatar;
    private String email;
    private String accessToken;
}
