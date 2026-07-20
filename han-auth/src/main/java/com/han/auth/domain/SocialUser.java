package com.han.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方社交用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialUser {
    private String provider;
    /** 存储用第三方唯一标识（微信优先 unionid，无则 openid） */
    private String openId;
    /** 微信原始 openid（unionid 生效时保留在此，便于排查） */
    private String rawOpenId;
    private String nickname;
    private String avatar;
    private String email;
    private String accessToken;
}
