package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * TOTP 动态码校验入参（han-auth → han-system）。
 *
 * <p>用来替代「han-auth 先把 TOTP 明文种子取回本地再自己算」的做法：种子不出 han-system，
 * 调用方只送 userId + 用户输入的 6 位码，校验在 han-system 内部完成。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTotpVerifyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /**
     * 用户输入的动态码。
     *
     * <p>已排除出 {@code toString()}：虽然只有 30 秒有效期，但落进日志仍然是可重放窗口。
     */
    @ToString.Exclude
    private String code;
}
