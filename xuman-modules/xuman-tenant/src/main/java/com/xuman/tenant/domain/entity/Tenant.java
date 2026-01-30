package com.xuman.tenant.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.xuman.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class Tenant extends BizEntity {

    /**
     * 租户名称
     */
    private String tenantName;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 租户套餐ID
     */
    private Long packageId;

    /**
     * 用户数量限制(-1不限制)
     */
    private Integer userLimit;

    /**
     * 账号数量限制(-1不限制)
     */
    private Integer accountLimit;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 隔离类型(logical逻辑隔离/physical物理隔离/hybrid混合)
     */
    private String isolationType;

    /**
     * 绑定域名
     */
    private String domain;

    /**
     * 状态(0正常 1停用)
     */
    private Integer status;
}
