package com.han.api.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社交账号绑定信息（服务间传输对象）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialBindingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long tenantId;
    private String provider;
    private String openId;
    private String nickname;
    private String avatar;

    /**
     * 绑定时间。
     *
     * <p>格式与 {@link UserVO#getLoginTime()} 对齐：服务端全局 Jackson 定制把
     * {@code LocalDateTime} 写成 {@code yyyy-MM-dd HH:mm:ss}，而声明式客户端用的是未经 Boot
     * 定制的 ObjectMapper（默认按 ISO-8601 解析）。显式声明格式保证契约自描述、收发两侧一致。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
