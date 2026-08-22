package com.han.open.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端凭证视图；故意不包含 clientSecret 或 clientSecretHash。 */
@Data
public class OpenAppCredentialAdminVO {

    /** 兼容旧前端字段。 */
    private Long id;

    private Long credentialId;
    private Long appId;
    private String environment;
    private String clientId;
    private Integer status;
    private LocalDateTime rotatedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
}
