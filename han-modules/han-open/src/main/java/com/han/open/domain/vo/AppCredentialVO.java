package com.han.open.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 应用凭证VO
 */
@Data
public class AppCredentialVO {

    /** 凭证ID */
    private Long id;

    /** 应用ID */
    private Long appId;

    /** 环境：SANDBOX沙箱、PROD生产 */
    private String environment;

    /** 客户端ID */
    private String clientId;

    /** 客户端密钥（明文，仅生成/轮换时返回一次，查询不返回） */
    private String clientSecret;

    /** 状态：0正常 1停用 2已轮换 */
    private Integer status;

    /** 轮换时间 */
    private LocalDateTime rotatedAt;

    /** 过期时间 */
    private LocalDateTime expireAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
