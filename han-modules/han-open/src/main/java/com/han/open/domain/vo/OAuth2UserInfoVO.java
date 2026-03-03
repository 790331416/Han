package com.han.open.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 用户信息响应VO (OpenID Connect UserInfo)
 */
@Data
@Builder
public class OAuth2UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识
     */
    private String sub;

    /**
     * 用户名
     */
    private String name;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String picture;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 邮箱是否验证
     */
    @JsonProperty("email_verified")
    private Boolean emailVerified;

    /**
     * 手机号
     */
    @JsonProperty("phone_number")
    private String phoneNumber;

    /**
     * 手机号是否验证
     */
    @JsonProperty("phone_number_verified")
    private Boolean phoneNumberVerified;

    /**
     * 性别
     */
    private String gender;

    /**
     * 更新时间
     */
    @JsonProperty("updated_at")
    private long updatedAt;
}
