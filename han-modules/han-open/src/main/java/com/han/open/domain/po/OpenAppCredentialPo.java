package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 应用分环境凭证持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_app_credential")
public class OpenAppCredentialPo extends BizEntity {

    /** 应用ID */
    private Long appId;

    /** 环境：SANDBOX沙箱、PROD生产 */
    private String environment;

    /** 客户端ID */
    private String clientId;

    /** 客户端密钥哈希 */
    private String clientSecretHash;

    /** 状态：0正常 1停用 2已轮换 */
    private Integer status;

    /** 轮换时间 */
    private LocalDateTime rotatedAt;

    /** 过期时间 */
    private LocalDateTime expireAt;
}
