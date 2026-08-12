package com.han.open.domain.token;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 访问令牌记录（存放于 Redis，由 TTL 自动过期）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权用户ID，client_credentials 模式为 0 */
    private Long userId;

    /** 客户端标识（app_key） */
    private String clientId;

    /** 令牌授权范围 */
    private String scope;

    /** 配对的刷新令牌，撤销时联动失效 */
    private String refreshToken;

    /** 过期时间（epoch 秒） */
    private long expiresAt;

    @JsonIgnore
    public boolean isExpired() {
        return Instant.now().getEpochSecond() > expiresAt;
    }
}
