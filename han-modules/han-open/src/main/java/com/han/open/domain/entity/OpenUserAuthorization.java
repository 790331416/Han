package com.han.open.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户授权记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_user_authorization")
public class OpenUserAuthorization extends TenantEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 应用ID
     */
    private Long appId;

    /**
     * 应用Key
     */
    private String appKey;

    /**
     * 授权范围
     */
    private String scopes;

    /**
     * 授权时间
     */
    private LocalDateTime authorizeTime;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessTime;

    /**
     * 授权状态(0有效 1已撤销)
     */
    private Integer status;
}
