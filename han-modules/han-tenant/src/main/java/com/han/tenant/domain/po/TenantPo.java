package com.han.tenant.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
@JsonIgnoreProperties({"tenantId"})
public class TenantPo extends BizEntity {

    /** 租户名称 */
    private String tenantName;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 联系邮箱 */
    private String contactEmail;

    /** 租户套餐ID */
    private Long packageId;

    /** 用户数量限制(-1不限制) */
    private Integer userLimit;

    /** 账号数量限制(-1不限制) */
    private Integer accountLimit;

    /** 过期时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 隔离类型(logical逻辑隔离/physical物理隔离/hybrid混合) */
    private String isolationType;

    /** 绑定域名 */
    private String domain;

    /** 状态(0正常 1停用) */
    private Integer status;
}
