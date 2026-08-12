package com.han.api.system.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long tenantId;
    private String provider;
    private String openId;
    private String nickname;
    private String avatar;
    private LocalDateTime createTime;
}
