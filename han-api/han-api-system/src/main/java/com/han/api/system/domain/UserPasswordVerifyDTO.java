package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码校验入参（han-auth → han-system）。
 *
 * <p>用来替代「把 BCrypt 哈希跟着 {@link UserVO} 一起跨服务传回来、调用方自己比对」的做法：
 * 明文密码送进 han-system，比对在 han-system 内部完成，只回一个布尔值，哈希不出库所在服务。
 *
 * <p>参数走请求体而不是 query，一是避免明文密码进访问日志与链路追踪的 URL 字段，
 * 二是内部签名一旦纳入 body 摘要，body 参数天然受签名保护。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordVerifyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /**
     * 明文密码。
     *
     * <p>调用方负责在本端完成 RSA 解密（han-auth 的登录链路已有该步骤）。
     * 已排除出 {@code toString()}，避免误打日志。
     */
    @ToString.Exclude
    private String rawPassword;
}
